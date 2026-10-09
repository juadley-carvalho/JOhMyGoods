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
        PRODUCE("Fase IV - Produzir e construir (ainda não implementada)", "ESPAÇO: encerrar a rodada e descartar o mercado");

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
     * Fim da rodada. Enquanto produção e construção (fases 3 e 5) não existem,
     * o trabalhador volta e a carta planejada retorna para a mão.
     */
    private void endPlanning() {
        player.removeWorker();
        player.getWorker().setMode(Worker.Mode.ATTENTIVE);
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