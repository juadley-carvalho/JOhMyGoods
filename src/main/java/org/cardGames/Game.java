package org.cardGames;

import java.util.List;

/**
 * Controlador do jogo: aplica as regras ao modelo (Deck, Player) e pede ao TablePanel
 * que anime o que aconteceu. Por enquanto cobre as fases I, II (com o planejamento) e III do manual.
 */
public class Game {

    private enum Phase {
        NEW_HAND("Fase I - Nova mão de cartas", "R: trocar a mão inteira (opcional)   |   ESPAÇO: receber 2 cartas"),
        SUNRISE("Fase II - Nascer do Sol", "ESPAÇO: abrir o mercado até aparecerem 2 meios sóis"),
        PLAN("Fase II - Planejamento", "clique num estabelecimento: trabalhador (de novo: atento/distraído)   |   "
                + "C: construir a carta selecionada   |   ESPAÇO: continuar"),
        SUNSET("Fase III - Pôr do Sol", "ESPAÇO: abrir a 2ª fileira do mercado"),
        PRODUCE("Fase IV - Produzir", "selecione cartas da mão para completar os recursos   |   "
                + "ESPAÇO: produzir   |   N: não produzir"),
        BUILD("Fase IV - Construir (ainda não implementada)", "ESPAÇO: encerrar a rodada e descartar o mercado");

        final String title;
        final String hint;

        Phase(String title, String hint) {
            this.title = title;
            this.hint = hint;
        }
    }

    private static final int STEP_MS = 120; // intervalo entre uma carta e a próxima
    private static final java.awt.Color ATTENTIVE_COLOR = new java.awt.Color(0x2E7D32);
    private static final java.awt.Color DISTRACTED_COLOR = new java.awt.Color(0xC75B12);

    private final GameState state;
    private final Deck deck;
    private final Player player;
    private final Market market;
    private final TablePanel table;

    private Phase phase = Phase.NEW_HAND;
    private boolean handReplaced;
    private int clock; // atraso acumulado da ação em andamento: faz as cartas saírem uma de cada vez
    private String warning; // aviso mostrado no lugar da dica até a próxima ação

    public Game(GameState state, TablePanel table) {
        this.state = state;
        this.deck = state.deck();
        this.player = state.human();
        this.market = state.market();
        this.table = table;
        table.onCardClick(this::isClickable, this::click);
    }

    /**
     * Mostra na mesa a preparação já feita pelo Setup: a Carvoaria no lugar, e os carvões
     * e a mão inicial saindo da pilha de compras, uma carta de cada vez.
     */
    public void start() {
        Building charcoal = player.getCharcoalBurner();
        table.addCard(charcoal.getCard(), Zone.BUILDINGS);

        // As cartas já compradas no Setup partem do topo da pilha, como se fossem compradas agora
        for (Card card : deck.getDrawPile()) {
            table.addCard(card, Zone.DECK);
        }
        for (Card card : charcoal.getGoods()) {
            table.addCard(card, Zone.DECK);
        }
        for (Card card : player.getHand()) {
            table.addCard(card, Zone.DECK);
        }

        clock = 0;
        List<Building> buildings = player.getBuildings();
        for (int i = 0; i < buildings.size(); i++) {
            for (Card card : buildings.get(i).getGoods()) {
                table.moveCard(card, Zone.GOODS, i, clock);
                clock += STEP_MS;
            }
        }
        for (Card card : player.getHand()) {
            send(card, Zone.HAND);
        }
        updateStatus();
    }

    public GameState getState() { return state; }

    /** Avança para a próxima etapa da rodada. */
    public void advance() {
        if (table.isBusy()) return; // espera as cartas terminarem de se mover
        clock = 0;

        switch (phase) {
            case NEW_HAND -> {
                deal();
                deal();
                phase = Phase.SUNRISE;
            }
            case SUNRISE -> {
                openMarketRow(Zone.MARKET_SUNRISE);
                phase = Phase.PLAN;
            }
            case PLAN -> {
                if (!player.isPlanningComplete()) {
                    warn("Coloque o trabalhador num estabelecimento antes de continuar");
                    return;
                }
                phase = Phase.SUNSET;
            }
            case SUNSET -> {
                openMarketRow(Zone.MARKET_SUNSET);
                phase = Phase.PRODUCE;
            }
            case PRODUCE -> {
                if (!produce()) return;
                phase = Phase.BUILD;
            }
            case BUILD -> {
                closeMarket();
                endPlanning();
                handReplaced = false;
                phase = Phase.NEW_HAND;
            }
        }
        updateStatus();
    }

    /** Fase I: descarta TODA a mão (não algumas) e compra a mesma quantidade. */
    public void replaceHand() {
        if (table.isBusy() || phase != Phase.NEW_HAND || handReplaced) return;
        clock = 0;
        handReplaced = true;

        List<Card> old = player.takeHand();
        for (Card card : old) {
            deck.discard(card);
            send(card, Zone.DISCARD);
        }
        for (int i = 0; i < old.size(); i++) {
            deal();
        }
        updateStatus();
    }

    /** Fase IV: abre mão de produzir nesta rodada. */
    public void skipProduction() {
        if (table.isBusy() || phase != Phase.PRODUCE) return;
        player.finishProduction();
        phase = Phase.BUILD;
        updateStatus();
    }

    /** Planejamento: separa a carta selecionada na mão para construir (virada para baixo). */
    public void planSelected() {
        if (table.isBusy() || phase != Phase.PLAN) return;
        clock = 0;

        List<Card> selected = player.getHand().stream().filter(Card::isSelected).toList();
        if (selected.size() != 1) {
            warn("Selecione exatamente 1 carta da mão para construir");
            return;
        }
        Card previous = player.getPlannedBuilding();
        player.planBuilding(selected.getFirst());
        if (previous != null) send(previous, Zone.HAND);
        send(selected.getFirst(), Zone.PLANNED);
        updateStatus();
    }

    // ------------------------------------------------------------- internos

    private boolean isClickable(Card card) {
        if (phase != Phase.PLAN) return false;
        if (card == player.getPlannedBuilding()) return true;
        Building building = buildingOf(card);
        return building != null && player.canPlaceWorker(building);
    }

    /** Planejamento: clicar num estabelecimento aloca o trabalhador (ou alterna o modo, se ele já está lá); clicar na carta a construir a devolve. */
    private void click(Card card) {
        clock = 0;
        if (card == player.getPlannedBuilding()) {
            player.cancelPlannedBuilding();
            send(card, Zone.HAND);
        } else {
            Building building = buildingOf(card);
            if (building == player.getWorkerBuilding()) {
                player.getWorker().toggleMode();
            } else {
                player.placeWorker(building);
            }
        }
        updateStatus();
    }

    /**
     * Produz no estabelecimento do trabalhador com as cartas selecionadas na mão:
     * as cartas usadas vão para o descarte e os bens saem da pilha de compras para cima do estabelecimento.
     * Retorna false (com aviso) se os recursos não bastam.
     */
    private boolean produce() {
        Building building = player.getWorkerBuilding();
        if (building == null) return true; // nada a produzir
        List<Card> selected = player.getHand().stream().filter(Card::isSelected).toList();
        Production.Result result = player.produce(building, market, selected);
        if (!result.succeeded()) {
            warn("faltam " + describeMissing(building, selected) + " - selecione cartas da mão ou N: não produzir");
            return false;
        }
        for (Card card : result.usedCards()) {
            deck.discard(card);
            send(card, Zone.DISCARD);
        }
        int index = player.getBuildings().indexOf(building);
        for (int i = 0; i < result.goods(); i++) {
            Card good = draw();
            if (good == null) break;
            building.addGood(good);
            table.moveCard(good, Zone.GOODS, index, clock);
            clock += STEP_MS;
        }
        player.finishProduction();
        return true;
    }

    private String describeMissing(Building building, List<Card> selected) {
        java.util.Map<Resource, Integer> available = new java.util.EnumMap<>(Resource.class);
        available.putAll(Production.freeResources(player, market));
        selected.forEach(c -> { if (c.getResource() != null) available.merge(c.getResource(), 1, Integer::sum); });
        int allowed = building.getPerson().missingAllowed();
        StringBuilder text = new StringBuilder();
        Production.missing(building.getCard(), available).forEach((resource, n) ->
                text.append(text.isEmpty() ? "" : ", ").append(n).append(" ").append(resource));
        if (allowed > 0 && !text.isEmpty()) text.append(" (distraído: 1 pode faltar)");
        return text.toString();
    }

    /**
     * Fim da rodada. Enquanto a construção (Fase 5) não existe, a carta planejada retorna para a mão.
     */
    private void endPlanning() {
        player.finishProduction();
        Card planned = player.cancelPlannedBuilding();
        if (planned != null) send(planned, Zone.HAND);
    }

    private Building buildingOf(Card card) {
        return player.getBuildings().stream().filter(b -> b.getCard() == card).findFirst().orElse(null);
    }

    private void warn(String message) {
        warning = message;
        updateStatus();
    }

    private void deal() {
        Card card = draw();
        if (card != null) {
            player.receive(card);
            send(card, Zone.HAND);
        }
    }

    private void openMarketRow(Zone row) {

        int suns = 0;
        while (suns < 2) {
            Card card = draw();
            if (card == null) break; // sem cartas (regra de esgotar as duas pilhas ainda não implementada)
            if (row == Zone.MARKET_SUNRISE) market.addSunrise(card); else market.addSunset(card);
            send(card, row);
            if (card.isSun()) suns++;

        }
    }

    /** Fim da fase IV: o mercado inteiro vai para o descarte. */
    private void closeMarket() {
        for (Card card : market.clear()) {
            deck.discard(card);
            send(card, Zone.DISCARD);
        }
    }

    /** Compra do topo; se a pilha acabou, embaralha o descarte de volta antes (e mostra isso na mesa). */
    private Card draw() {
        if (deck.needsReshuffle()) {
            for (Card card : deck.reshuffle()) {
                send(card, Zone.DECK);
            }
        }
        return deck.draw();
    }

    private void send(Card card, Zone zone) {
        table.moveCard(card, zone, clock);
        clock += STEP_MS;
    }

    private void updateStatus() {
        String hint = (warning != null) ? "ATENÇÃO: " + warning : phase.hint;
        Building working = player.getWorkerBuilding();
        if (warning == null && phase == Phase.PRODUCE && working != null) {
            String missing = describeMissing(working, List.of());
            hint = (missing.isEmpty() ? "recursos completos no mercado" : "faltam " + missing)
                    + "   |   " + hint;
        }
        warning = null;
        table.setStatus(phase.title + "   |   " + hint);
        refreshWorkerBadge();
    }

    private void refreshWorkerBadge() {
        table.clearBadges();
        Building building = player.getWorkerBuilding();
        if (building == null) return;
        boolean attentive = player.getWorker().getMode() == Worker.Mode.ATTENTIVE;
        table.setBadge(building.getCard(), attentive ? "Atento" : "Distraído",
                attentive ? ATTENTIVE_COLOR : DISTRACTED_COLOR);
    }
}