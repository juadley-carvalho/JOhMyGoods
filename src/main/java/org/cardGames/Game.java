package org.cardGames;

import java.awt.SecondaryLoop;
import java.awt.Toolkit;
import java.util.List;

/**
 * Controlador do jogo: aplica as regras ao modelo (Deck, Player) e pede ao TablePanel
 * que anime o que aconteceu. Cobre a rodada completa, fases I a IV do manual: o jogador humano
 * decide pela tela e os oponentes ({@link Bot}) decidem sozinhos — no planejamento, junto com o humano
 * (sem ver a escolha dele); na Fase IV, cada um na sua vez, a partir do jogador inicial.
 */
public class Game {

    enum Phase {
        NEW_HAND("Fase I - Nova mão de cartas", "R: trocar a mão inteira (opcional)   |   ESPAÇO: receber 2 cartas"),
        SUNRISE("Fase II - Nascer do Sol", "ESPAÇO: abrir o mercado até aparecerem 2 meios sóis"),
        PLAN("Fase II - Planejamento", "clique num estabelecimento: trabalhador (de novo: atento/distraído)   |   "
                + "clique num assistente e depois num estabelecimento livre: mover (2 moedas)   |   "
                + "C: construir a carta selecionada   |   ESPAÇO: continuar"),
        MOVE("Fase II - Mover assistente", "clique nos estabelecimentos para pagar com os bens deles "
                + "(botão direito: tirar)   |   ESPAÇO: pagar e mover   |   N: desistir"),
        SUNSET("Fase III - Pôr do Sol", "ESPAÇO: abrir a 2ª fileira do mercado"),
        PRODUCE("Fase IV - Produzir", "selecione cartas da mão para completar os recursos   |   "
                + "ESPAÇO: produzir   |   N: não produzir"),
        CHAIN("Fase IV - Cadeia de produção", "selecione cartas da mão (bens de outros estabelecimentos "
                + "entram sozinhos)   |   K: usar a cadeia   |   ESPAÇO: terminar"),
        BUILD("Fase IV - Construir ou contratar", "clique nos estabelecimentos para pagar com os bens deles "
                + "(botão direito: tirar)   |   clique num assistente para contratá-lo em vez de construir   |   "
                + "ESPAÇO: pagar e encerrar a rodada   |   N: nada"),
        PLACE_ASSISTANT("Fase IV - Alocar assistente", "clique num estabelecimento livre para o novo assistente   |   "
                + "N: voltar"),
        GAME_OVER("Fim de partida", "ESPAÇO: ver o resultado");

        final String title;
        final String hint;

        Phase(String title, String hint) {
            this.title = title;
            this.hint = hint;
        }
    }

    private static final int STEP_MS = 120; // intervalo entre uma carta e a próxima
    private static final int BOT_PAUSE_MS = 700; // pausa depois de cada passo de um oponente
    private static final java.awt.Color ATTENTIVE_COLOR = new java.awt.Color(0x2E7D32);
    private static final java.awt.Color DISTRACTED_COLOR = new java.awt.Color(0xC75B12);
    private static final java.awt.Color PAYMENT_COLOR = new java.awt.Color(0x1565C0);
    private static final java.awt.Color ASSISTANT_COLOR = new java.awt.Color(0x6A1B9A);
    private static final java.awt.Color HIREABLE_COLOR = new java.awt.Color(0x1B5E20);
    private static final java.awt.Color MISSING_COLOR = new java.awt.Color(0xB71C1C);
    private static final java.awt.Color UNAVAILABLE_COLOR = new java.awt.Color(0x555555);
    private static final java.awt.Color SOURCE_COLOR = new java.awt.Color(0x00838F);

    private final GameState state;
    private final Deck deck;
    private final Player player;
    private final Market market;
    private final TablePanel table;

    private Phase phase = Phase.NEW_HAND;
    private boolean handReplaced;
    private int clock; // atraso acumulado da ação em andamento: faz as cartas saírem uma de cada vez
    private int stepStart; // quando começou o passo atual do oponente (a narração aparece nesse momento)
    private String warning; // aviso mostrado no lugar da dica até a próxima ação
    private final java.util.Map<Building, Integer> payment = new java.util.LinkedHashMap<>(); // bens escolhidos para pagar
    private Assistant toHire;   // assistente escolhido na Fase IV (em vez de construir)
    private Assistant moving;   // assistente escolhido para mudar de estabelecimento na Fase II
    private Building moveTarget; // para onde ele vai (Fase II, pagando)
    private final java.util.Deque<Building> producers = new java.util.ArrayDeque<>(); // fila da produção; o primeiro produz agora
    private final java.util.Map<Resource, Building> chainSources = new java.util.EnumMap<>(Resource.class); // origem escolhida dos bens da cadeia
    private final java.util.Map<Player, String> lastTurn = new java.util.HashMap<>(); // resumo da última vez de cada oponente
    private boolean finalChains; // rodada final: as cadeias de todos os estabelecimentos já entraram na fila
    private int discarding;     // exaustão: quantas cartas o humano precisa escolher para descartar (0 = nenhuma)
    private SecondaryLoop exhaustionLoop; // segura a ação em andamento enquanto o humano escolhe
    private boolean autoExhaustion; // capturas de tela: o jogo escolhe o descarte do humano também
    private java.util.function.Consumer<List<Scoring.Score>> onGameOver = ranking -> { };
    private java.util.function.Consumer<String> onInfo = info -> { };

    /** Mesa vista pelos oponentes: bens comprados vão para a área deles; cartas gastas, para o descarte. */
    private final Bot.Table botTable = new Bot.Table() {
        @Override
        public Card draw() {
            Card card = Game.this.draw();
            if (card != null) send(card, Zone.OPPONENTS);
            return card;
        }

        @Override
        public void discard(Card card) {
            deck.discard(card);
            send(card, Zone.DISCARD);
        }

        /** O passo aparece na barra de status quando as cartas dele começam a se mover; depois, uma pausa. */
        @Override
        public void step(String text) {
            table.narrate("Fase IV - vez dos oponentes", text, stepStart);
            clock = Math.max(clock, stepStart) + BOT_PAUSE_MS;
            stepStart = clock;
        }
    };

    public Game(GameState state, TablePanel table) {
        this.state = state;
        this.deck = state.deck();
        this.player = state.human();
        this.market = state.market();
        this.table = table;
        table.onCardClick(this::isClickable, this::click);
        table.onCardRightClick(this::rightClick);
        table.onTileClick(this::isTileClickable, this::clickTile);
        table.onSelectionChange(this::updateStatus);
    }

    /**
     * Mostra na mesa a preparação já feita pelo Setup: os estabelecimentos (a Carvoaria) no lugar, e os bens
     * e a mão inicial saindo da pilha de compras, uma carta de cada vez.
     */
    public void start() {
        for (Building building : player.getBuildings()) {
            table.addCard(building.getCard(), Zone.BUILDINGS);
        }

        // As cartas já compradas no Setup partem do topo da pilha, como se fossem compradas agora
        for (Card card : deck.getDrawPile()) {
            table.addCard(card, Zone.DECK);
        }
        for (Card card : deck.getDiscardPile()) {
            table.addCard(card, Zone.DISCARD);
        }
        for (Building building : player.getBuildings()) {
            for (Card card : building.getGoods()) table.addCard(card, Zone.DECK);
        }
        for (Card card : player.getHand()) {
            table.addCard(card, Zone.DECK);
        }
        for (Player opponent : state.opponents()) {
            for (Card card : opponentCards(opponent)) table.addCard(card, Zone.DECK);
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
        for (Player opponent : state.opponents()) {
            for (Card card : opponentCards(opponent)) send(card, Zone.OPPONENTS);
        }
        updateStatus();
    }

    /** Cartas de um oponente que estão na mesa: estabelecimentos, bens e mão. */
    private static List<Card> opponentCards(Player opponent) {
        List<Card> cards = new java.util.ArrayList<>();
        for (Building building : opponent.getBuildings()) {
            cards.add(building.getCard());
            cards.addAll(building.getGoods());
        }
        cards.addAll(opponent.getHand());
        return cards;
    }

    public GameState getState() { return state; }

    /** Etapa atual da rodada (usado pelas capturas de tela). */
    Phase phase() { return phase; }

    /** Capturas de tela: na exaustão, o jogo escolhe o descarte do humano (sem esperar a tela). */
    void setAutoExhaustion(boolean auto) { this.autoExhaustion = auto; }

    /** Chamado (com a classificação) quando o jogador pede o resultado ao fim da partida. */
    public void onGameOver(java.util.function.Consumer<List<Scoring.Score>> listener) {
        this.onGameOver = listener;
    }

    /** Recebe o resumo do jogador humano (moedas em bens, pontos, cartas) a cada mudança. */
    public void onInfo(java.util.function.Consumer<String> listener) {
        this.onInfo = listener;
    }

    /** Avança para a próxima etapa da rodada. */
    public void advance() {
        if (discarding > 0) {
            confirmDiscard();
            return;
        }
        if (table.isBusy()) return; // espera as cartas terminarem de se mover
        clock = 0;

        switch (phase) {
            case NEW_HAND -> {
                for (Player p : state.turnOrder()) {
                    int cards = 2 + p.newHandBonus(); // guildas de carta: +1 com até 3 cartas na mão
                    for (int i = 0; i < cards; i++) deal(p);
                }
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
                // Decisões "simultâneas": os oponentes planejam com o mesmo mercado, sem ver a escolha do humano
                for (Player opponent : state.opponents()) {
                    Bot.plan(opponent, market);
                    lastTurn.remove(opponent);
                }
                moving = null;
                phase = Phase.SUNSET;
            }
            case MOVE -> {
                if (!confirmMove()) return;
                phase = Phase.PLAN;
            }
            case SUNSET -> {
                openMarketRow(Zone.MARKET_SUNSET);
                playOpponents(true); // oponentes que jogam antes do humano nesta rodada
                producers.clear();
                producers.addAll(player.producingBuildings());
                phase = Phase.PRODUCE;
                if (producers.isEmpty()) nextProducer();
            }
            case PRODUCE -> {
                if (!produce()) return;
                Building producing = producers.peekFirst();
                if (producing.hasProducedThisRound() && !producing.getCard().getChainResources().isEmpty()) {
                    phase = Phase.CHAIN;
                } else {
                    nextProducer();
                }
            }
            case CHAIN -> nextProducer();
            case BUILD -> {
                if (toHire != null) {
                    if (!checkHire()) return;
                    phase = Phase.PLACE_ASSISTANT;
                } else {
                    if (player.getPlannedBuilding() != null && !build()) return;
                    endRound();
                }
            }
            case PLACE_ASSISTANT -> {
                warn("clique num estabelecimento livre para o assistente (N: voltar)");
                return;
            }
            case GAME_OVER -> {
                onGameOver.accept(Scoring.ranking(state.players()));
                return;
            }
        }
        updateStatus();
    }

    /** Fase I: descarta TODA a mão (não algumas) e compra a mesma quantidade. */
    public void replaceHand() {
        if (discarding > 0 || table.isBusy() || phase != Phase.NEW_HAND || handReplaced) return;
        clock = 0;
        handReplaced = true;

        List<Card> old = player.takeHand();
        for (Card card : old) {
            deck.discard(card);
            send(card, Zone.DISCARD);
        }
        for (int i = 0; i < old.size(); i++) {
            deal(player);
        }
        updateStatus();
    }

    /** Tecla N: abre mão de produzir, de construir, de mover o assistente ou volta da alocação. */
    public void decline() {
        if (discarding > 0 || table.isBusy()) return;
        clock = 0;
        if (phase == Phase.PRODUCE) {
            nextProducer();
        } else if (phase == Phase.MOVE) {
            cancelMove();
        } else if (phase == Phase.PLACE_ASSISTANT) {
            phase = Phase.BUILD;
        } else if (phase == Phase.BUILD) {
            endRound(); // a carta planejada volta para a mão
        }
        updateStatus();
    }

    /**
     * Cadeia de produção: executa uma vez com as cartas selecionadas na mão (pode repetir).
     * Se um bem que falta está em mais de um estabelecimento, o jogador escolhe antes de onde ele sai.
     */
    public void runChain() {
        if (discarding > 0 || table.isBusy() || phase != Phase.CHAIN) return;
        clock = 0;
        Building building = producers.peekFirst();
        List<Card> selected = player.getHand().stream().filter(Card::isSelected).toList();
        Resource ask = ambiguousChainItem(building, selected);
        if (ask != null) {
            warn("há " + ask + " em mais de um estabelecimento - clique naquele de onde ele deve sair e use K de novo");
            return;
        }
        List<Card> moved = player.runChain(building, selected, chainSources);
        if (moved.isEmpty()) {
            warn("a cadeia precisa de " + describeChain(building) + " - selecione na mão ou produza esses bens antes");
            return;
        }
        int index = player.getBuildings().indexOf(building);
        for (Card card : moved) {
            table.moveCard(card, Zone.GOODS, index, clock);
            clock += STEP_MS;
        }
        updateStatus();
    }

    /** Planejamento: separa a carta selecionada na mão para construir (virada para baixo). */
    public void planSelected() {
        if (discarding > 0 || table.isBusy() || phase != Phase.PLAN) return;
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
        if (discarding > 0) return false;
        Building building = buildingOf(card);
        if (phase == Phase.BUILD) {
            return (player.getPlannedBuilding() != null || toHire != null) && building != null && building.goodsCount() > 0;
        }
        if (phase == Phase.MOVE) {
            return building != null && building.goodsCount() > 0;
        }
        if (phase == Phase.PLACE_ASSISTANT) {
            return building != null && player.canPlaceAssistant(toHire, building);
        }
        if (phase == Phase.CHAIN) {
            return building != null && isChainSourceChoice(building);
        }
        if (phase != Phase.PLAN) return false;
        if (card == player.getPlannedBuilding()) return true;
        if (building == null) return false;
        if (moving != null && player.canPlaceAssistant(moving, building)) return true;
        return player.canPlaceWorker(building) || building.getPerson() instanceof Assistant;
    }

    /** Assistentes da lateral: clicáveis na construção (escolher quem contratar). */
    private boolean isTileClickable(int index) {
        return discarding == 0 && phase == Phase.BUILD && index < state.availableAssistants().size();
    }

    /** Construção: clicar num assistente o escolhe para contratar (de novo: desiste); a carta planejada fica de lado. */
    private void clickTile(int index) {
        clock = 0;
        Assistant assistant = state.availableAssistants().get(index);
        toHire = (toHire == assistant) ? null : assistant;
        updateStatus();
    }

    /** Se dá para contratar o assistente escolhido com o pagamento atual; senão, avisa o motivo. */
    private boolean checkHire() {
        if (!player.hasColorsFor(toHire)) {
            warn(toHire + " exige estabelecimentos " + describeColors(toHire));
        } else if (!player.hasFreeBuilding()) {
            warn("não há estabelecimento livre para o assistente");
        } else if (!player.canHire(toHire, payment)) {
            warn("o pagamento (" + Player.paymentValue(payment) + ") não cobre o custo " + toHire.getCost()
                    + " - escolha mais bens");
        } else {
            return true;
        }
        return false;
    }

    /** Aloca o assistente recém-contratado (paga com os bens escolhidos) e encerra a rodada. */
    private void placeHired(Building building) {
        for (Card good : player.hireAssistant(toHire, payment, building)) {
            deck.discard(good);
            send(good, Zone.DISCARD);
        }
        state.availableAssistants().remove(toHire);
        endRound();
    }

    /** Fase II: escolhido o destino do assistente, o jogador escolhe os bens para pagar as 2 moedas. */
    private void startMove(Building target) {
        int coins = player.getBuildings().stream().mapToInt(Building::goodsValue).sum();
        if (coins < Player.MOVE_ASSISTANT_COST) {
            moving = null;
            warn("mover o assistente custa " + Player.MOVE_ASSISTANT_COST + " moedas em bens - não há bens suficientes");
            return;
        }
        moveTarget = target;
        payment.clear();
        phase = Phase.MOVE;
    }

    /** Paga com os bens escolhidos e muda o assistente de estabelecimento; false (com aviso) se não cobre. */
    private boolean confirmMove() {
        if (!player.canMoveAssistant(moving, moveTarget, payment)) {
            warn("o pagamento (" + Player.paymentValue(payment) + ") não cobre as " + Player.MOVE_ASSISTANT_COST
                    + " moedas - escolha mais bens ou N: desistir");
            return false;
        }
        for (Card good : player.moveAssistant(moving, moveTarget, payment)) {
            deck.discard(good);
            send(good, Zone.DISCARD);
        }
        moving = null;
        moveTarget = null;
        payment.clear();
        return true;
    }

    private void cancelMove() {
        moving = null;
        moveTarget = null;
        payment.clear();
        phase = Phase.PLAN;
    }

    private String describeColors(Assistant assistant) {
        return String.join(", ", assistant.getRequiredColors().stream().map(Game::colorName).toList());
    }

    private static String colorName(Color color) {
        return switch (color) {
            case AZUL_ESCURO -> "az.escuro";
            case AZUL_CLARO -> "az.claro";
            case AMARELO -> "amarelo";
            case VERMELHO -> "vermelho";
            case VERDE -> "verde";
            case PRETO -> "preto";
        };
    }

    /** Cor da carta na mesa (quadradinhos das fichas de assistente). */
    private static java.awt.Color chipColor(Color color) {
        return switch (color) {
            case AZUL_ESCURO -> new java.awt.Color(0x1E3A8A);
            case AZUL_CLARO -> new java.awt.Color(0x4FC3F7);
            case AMARELO -> new java.awt.Color(0xFBC02D);
            case VERMELHO -> new java.awt.Color(0xD32F2F);
            case VERDE -> new java.awt.Color(0x43A047);
            case PRETO -> new java.awt.Color(0x111111);
        };
    }

    /** Passa para o próximo estabelecimento da fila de produção; no fim da fila, vai para a construção. */
    private void nextProducer() {
        producers.pollFirst();
        chainSources.clear();
        if (producers.isEmpty() && player.areChainsUnlocked() && !finalChains) {
            // Rodada final: depois da produção, a cadeia de cada estabelecimento pode ser usada
            finalChains = true;
            player.getBuildings().stream()
                    .filter(b -> !b.getCard().getChainResources().isEmpty()).forEach(producers::add);
        }
        if (producers.isEmpty()) {
            player.finishProduction();
            phase = Phase.BUILD;
        } else {
            phase = finalChains ? Phase.CHAIN : Phase.PRODUCE;
        }
    }

    /**
     * Um item da cadeia que não vem das cartas selecionadas e está em mais de um estabelecimento,
     * sem o jogador ter escolhido de qual deles sai; null se não há dúvida.
     */
    private Resource ambiguousChainItem(Building building, List<Card> selected) {
        for (Resource item : Production.itemsFromGoods(building, selected)) {
            List<Building> sources = Production.goodSources(player, building, item);
            if (sources.size() > 1 && !sources.contains(chainSources.get(item))) return item;
        }
        return null;
    }

    /** Na cadeia, um estabelecimento é clicável se tem um bem da cadeia que também está em outro. */
    private boolean isChainSourceChoice(Building building) {
        Building producing = producers.peekFirst();
        if (producing == null || building == producing) return false;
        Resource product = building.getCard().getProduct();
        return producing.getCard().getChainResources().contains(product)
                && Production.goodSources(player, producing, product).size() > 1
                && building.goodsCount() > 0;
    }

    /**
     * Clique num estabelecimento: na construção e ao mover, escolhe bens do pagamento; na cadeia, a origem dos bens;
     * no planejamento, aloca o trabalhador (ou alterna o modo, se ele já está lá) ou escolhe o assistente a mover;
     * clicar na carta a construir a devolve.
     */
    private void click(Card card) {
        clock = 0;
        Building building = buildingOf(card);
        if (phase == Phase.BUILD || phase == Phase.MOVE) {
            choosePayment(building, +1);
            return;
        }
        if (phase == Phase.CHAIN) {
            chainSources.put(building.getCard().getProduct(), building);
            updateStatus();
            return;
        }
        if (phase == Phase.PLACE_ASSISTANT) {
            placeHired(building);
            updateStatus();
            return;
        }
        if (card == player.getPlannedBuilding()) {
            player.cancelPlannedBuilding();
            send(card, Zone.HAND);
        } else if (moving != null && building == player.getAssistantBuilding(moving)) {
            moving = null; // clicou de novo no assistente: desiste de mover
        } else if (moving != null && player.canPlaceAssistant(moving, building)) {
            startMove(building);
        } else if (building.getPerson() instanceof Assistant assistant) {
            moving = assistant;
        } else if (building == player.getWorkerBuilding()) {
            player.getWorker().toggleMode();
        } else {
            player.placeWorker(building);
        }
        updateStatus();
    }

    /** Botão direito: tira 1 bem do estabelecimento do pagamento. */
    private void rightClick(Card card) {
        if (phase == Phase.BUILD || phase == Phase.MOVE) choosePayment(buildingOf(card), -1);
    }

    /**
     * Produz no estabelecimento da vez (trabalhador ou assistente) com as cartas selecionadas na mão:
     * as cartas usadas vão para o descarte e os bens saem da pilha de compras para cima do estabelecimento.
     * Retorna false (com aviso) se os recursos não bastam.
     */
    private boolean produce() {
        Building building = producers.peekFirst();
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
        return true; // o trabalhador só sai depois de toda a produção
    }

    private String describeChain(Building building) {
        List<Resource> chain = building.getCard().getChainResources();
        return String.join(" + ", chain.stream().map(Resource::name).toList());
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
     * Pagamento: o clique põe mais 1 bem do estabelecimento (passando do total, volta a 0);
     * o botão direito tira 1.
     */
    private void choosePayment(Building building, int delta) {
        int n = payment.getOrDefault(building, 0) + delta;
        if (n > building.goodsCount()) n = 0;
        if (n <= 0) payment.remove(building); else payment.put(building, n);
        updateStatus();
    }

    /** Valor do pagamento escolhido diante do custo (o que passar do custo se perde: não há troco). */
    private String describePayment(int cost) {
        int value = Player.paymentValue(payment);
        String text = "pagamento " + value + " de " + cost;
        if (value > cost) text += " (" + (value - cost) + " a mais: não há troco)";
        else if (value < cost) text += " (faltam " + (cost - value) + ")";
        return text;
    }

    /**
     * Constrói a carta planejada com os bens escolhidos: eles vão para o descarte e a carta
     * entra nos estabelecimentos. Retorna false (com aviso) se o pagamento não cobre o custo.
     */
    private boolean build() {
        Card planned = player.getPlannedBuilding();
        if (!player.canBuild(payment)) {
            warn("o pagamento (" + Player.paymentValue(payment) + ") não cobre o custo " + planned.getCost()
                    + " - escolha mais bens ou N: não construir");
            return false;
        }
        for (Card good : player.buildPlanned(payment)) {
            deck.discard(good);
            send(good, Zone.DISCARD);
        }
        send(planned, Zone.BUILDINGS);
        return true;
    }

    /**
     * Fase IV dos oponentes, na ordem do turno: before=true joga os que vêm antes do humano
     * (a partir do jogador inicial), before=false os que vêm depois. Quem joga antes tem
     * prioridade para contratar um assistente. Cada passo deles aparece na barra de status, com uma pausa.
     */
    private void playOpponents(boolean before) {
        List<Player> order = state.turnOrder();
        int human = order.indexOf(player);
        List<Player> turn = before ? order.subList(0, human) : order.subList(human + 1, order.size());
        for (Player opponent : turn) {
            stepStart = clock;
            lastTurn.put(opponent, Bot.playTurn(opponent, state, botTable));
        }
    }

    /**
     * Fim da rodada: os oponentes que faltam jogam, o mercado é descartado, a carta planejada que
     * não foi construída volta para a mão e o próximo jogador passa a ser o inicial.
     * Se era a rodada final, a partida acaba.
     */
    private void endRound() {
        playOpponents(false);
        closeMarket();
        state.endRound();
        player.finishProduction();
        Card planned = player.cancelPlannedBuilding();
        if (planned != null) send(planned, Zone.HAND);
        payment.clear();
        chainSources.clear();
        toHire = null;
        moving = null;
        moveTarget = null;
        handReplaced = false;
        finalChains = false;
        phase = state.isGameOver() ? Phase.GAME_OVER : Phase.NEW_HAND;
    }

    /** O estabelecimento do jogador de que a carta faz parte: a própria carta ou um bem sobre ele. */
    private Building buildingOf(Card card) {
        return player.getBuildings().stream()
                .filter(b -> b.getCard() == card || b.getGoods().contains(card)).findFirst().orElse(null);
    }

    private void warn(String message) {
        warning = message;
        updateStatus();
    }

    private void deal(Player to) {
        Card card = draw();
        if (card != null) {
            to.receive(card);
            send(card, to == player ? Zone.HAND : Zone.OPPONENTS);
        }
    }

    private void openMarketRow(Zone row) {

        int suns = 0;
        while (suns < 2) {
            Card card = draw();
            if (card == null) break; // nem compras nem descarte, mesmo depois da exaustão
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
        if (deck.isExhausted()) exhaust();
        if (deck.needsReshuffle()) {
            for (Card card : deck.reshuffle()) {
                send(card, Zone.DECK);
            }
        }
        return deck.draw();
    }

    /**
     * Regra de exaustão (compras e descarte vazios): cada jogador descarta metade da mão. Os oponentes
     * descartam na hora; o humano escolhe as cartas na mesa e a ação em andamento espera (a tela continua
     * respondendo, como numa janela modal) até ele confirmar com ESPAÇO.
     */
    private void exhaust() {
        for (Player opponent : state.opponents()) {
            for (Card card : opponent.discardHalf()) {
                deck.discard(card);
                send(card, Zone.DISCARD);
            }
        }
        int count = player.exhaustionDiscards();
        if (count == 0) return;
        if (autoExhaustion) {
            for (Card card : player.discardHalf()) {
                deck.discard(card);
                send(card, Zone.DISCARD);
            }
            return;
        }
        discarding = count;
        table.clearSelection();
        updateStatus();
        exhaustionLoop = Toolkit.getDefaultToolkit().getSystemEventQueue().createSecondaryLoop();
        exhaustionLoop.enter(); // volta quando confirmDiscard chamar exit()
        exhaustionLoop = null;
        clock = 0; // as cartas de antes já se moveram enquanto o jogador escolhia
    }

    /** ESPAÇO na exaustão: descarta as cartas selecionadas, se forem exatamente metade da mão. */
    private void confirmDiscard() {
        List<Card> chosen = player.getHand().stream().filter(Card::isSelected).toList();
        if (chosen.size() != discarding) {
            warn("selecione exatamente " + discarding + (discarding == 1 ? " carta" : " cartas") + " para descartar");
            return;
        }
        clock = 0;
        for (Card card : player.discardChosen(chosen)) {
            deck.discard(card);
            send(card, Zone.DISCARD);
        }
        discarding = 0;
        updateStatus();
        if (exhaustionLoop != null) exhaustionLoop.exit();
    }

    private void send(Card card, Zone zone) {
        table.moveCard(card, zone, clock);
        clock += STEP_MS;
    }

    /** Barra de status: 1ª linha com a etapa e o que está em jogo; 2ª com as teclas ou o aviso. */
    private void updateStatus() {
        String context = null;
        String hint = phase.hint;
        Building working = producers.peekFirst();
        Card planned = player.getPlannedBuilding();
        List<Card> selected = player.getHand().stream().filter(Card::isSelected).toList();
        switch (phase) {
            case PRODUCE -> {
                if (working != null) {
                    String missing = describeMissing(working, List.of());
                    context = working.getCard().getName() + " (" + describePerson(working) + "): "
                            + (missing.isEmpty() ? "recursos completos no mercado" : "faltam " + missing);
                }
            }
            case PLAN -> {
                if (moving != null) {
                    context = "mover " + moving + ": clique num estabelecimento livre (" + Player.MOVE_ASSISTANT_COST
                            + " moedas)";
                }
            }
            case MOVE -> context = "mover " + moving + " para " + moveTarget.getCard().getName() + ": "
                    + describePayment(Player.MOVE_ASSISTANT_COST);
            case CHAIN -> {
                if (working != null) {
                    context = working.getCard().getName() + ": " + describeChain(working) + " -> "
                            + working.getCard().getChainResources().size() + " " + working.getCard().getProduct();
                    Resource ask = ambiguousChainItem(working, selected);
                    if (ask != null && warning == null) context += "   |   há " + ask + " em mais de um estabelecimento: clique no de onde ele sai";
                }
            }
            case BUILD -> {
                if (toHire != null) context = "contratar " + toHire + ": " + describePayment(toHire.getCost());
                else if (planned != null) context = "construir " + planned.getName() + ": " + describePayment(planned.getCost());
                else context = "nenhuma carta planejada";
            }
            default -> { }
        }
        String title = phase.title;
        if (discarding > 0) {
            title = "Exaustão - compras e descarte acabaram: cada jogador descarta metade da mão";
            context = "escolha " + discarding + (discarding == 1 ? " carta" : " cartas") + " (" + selected.size()
                    + " selecionadas)";
            hint = "clique nas cartas da mão para escolher   |   ESPAÇO: descartar";
        }
        boolean warned = warning != null;
        if (warned) hint = "ATENÇÃO: " + warning;
        warning = null;
        String flags = (state.isFinalRound() && !state.isGameOver() ? " (RODADA FINAL)" : "")
                + (state.startingPlayer() == player ? " (você é o inicial)" : "");
        table.setStatus(title + flags + (context != null ? "   |   " + context : ""), hint, warned);
        table.setTips(tips());
        refreshInfo();
        refreshBadges();
        refreshAssistants();
        refreshOpponents();
    }

    /**
     * Quadro de dicas: primeiro o que vale para a situação do jogador (o que dá para construir ou contratar,
     * que cartas da mão completam a produção, rodada final), depois as regras da etapa ({@link Help#tips}).
     */
    private List<String> tips() {
        if (discarding > 0) return List.of(Help.EXHAUSTION_TIP);
        List<String> tips = new java.util.ArrayList<>();
        int coins = Scoring.score(player).coins();
        switch (phase) {
            case PLAN -> {
                List<String> affordable = player.getHand().stream().filter(c -> c.getCost() <= coins)
                        .map(c -> c.getName() + " (" + c.getCost() + ")").toList();
                tips.add("Seus bens valem " + coins + " moedas. " + (affordable.isEmpty()
                        ? "Nenhuma carta da mão cabe nisso ainda; os bens desta rodada também vão contar."
                        : "Já cabem: " + String.join(", ", affordable) + "."));
                if (!player.getAssistants().isEmpty()) {
                    tips.add("Clique num assistente e depois num estabelecimento livre para mudá-lo ("
                            + Player.MOVE_ASSISTANT_COST + " moedas).");
                }
            }
            case PRODUCE -> {
                Building working = producers.peekFirst();
                if (working != null) {
                    java.util.Map<Resource, Integer> available = new java.util.EnumMap<>(Resource.class);
                    available.putAll(Production.freeResources(player, market));
                    java.util.Set<Resource> missing = Production.missing(working.getCard(), available).keySet();
                    List<String> useful = player.getHand().stream()
                            .filter(c -> c.getResource() != null && missing.contains(c.getResource()))
                            .map(c -> c.getName() + " (" + c.getResource() + ")").toList();
                    if (!missing.isEmpty()) {
                        tips.add(useful.isEmpty() ? "Nenhuma carta da mão tem o recurso que falta."
                                : "Na mão servem: " + String.join(", ", useful) + ".");
                    }
                }
            }
            case BUILD -> {
                Card planned = player.getPlannedBuilding();
                tips.add("Seus bens valem " + coins + " moedas" + (planned == null ? "."
                        : "; construir " + planned.getName() + " custa " + planned.getCost()
                        + (planned.getCost() <= coins ? "." : " (não dá).")));
                List<String> hireable = state.availableAssistants().stream()
                        .filter(a -> player.hasColorsFor(a) && a.getCost() <= coins && player.hasFreeBuilding())
                        .map(a -> a + " (" + a.getPoints() + " pts)").toList();
                if (!hireable.isEmpty()) {
                    tips.add("Assistentes ao seu alcance: " + String.join(", ", hireable) + ".");
                } else if (!state.availableAssistants().isEmpty()) {
                    boolean colors = state.availableAssistants().stream().anyMatch(player::hasColorsFor);
                    tips.add("Nenhum assistente ao seu alcance: " + (!colors
                            ? "os que restam exigem cores de estabelecimento que você não tem."
                            : !player.hasFreeBuilding() ? "não há estabelecimento livre para ele."
                            : "seus bens não cobrem o custo dos que você poderia contratar."));
                }
            }
            default -> { }
        }
        if (state.isFinalRound() && !state.isGameOver()) {
            tips.add("Rodada final: depois da produção, as cadeias de todos os estabelecimentos podem ser usadas.");
        }
        tips.addAll(Help.tips(phase));
        return tips;
    }

    /**
     * Área dos oponentes: na caixa, pontos, estabelecimentos, moedas em bens, mão, assistentes e o que fizeram
     * na última vez; no detalhe (mouse), cada estabelecimento com os bens (o do trabalhador com * se atento ou
     * ~ se distraído, só depois do planejamento; +A com assistente).
     */
    private void refreshOpponents() {
        List<TablePanel.OpponentView> views = new java.util.ArrayList<>();
        boolean revealed = phase != Phase.NEW_HAND && phase != Phase.SUNRISE && phase != Phase.PLAN && phase != Phase.MOVE;
        for (Player opponent : state.opponents()) {
            Scoring.Score score = Scoring.score(opponent);
            List<String> lines = new java.util.ArrayList<>();
            lines.add(score.total() + " pts · " + opponent.getBuildings().size() + " estab. · " + score.coins() + " moedas");
            lines.add("mão " + opponent.getHand().size() + " · " + opponent.getAssistants().size() + " assist."
                    + (revealed && opponent.getPlannedBuilding() != null ? " · +1 a construir" : ""));
            String last = lastTurn.get(opponent);
            if (last != null) lines.add("> " + last.substring(opponent.getName().length() + 2));

            List<String> detail = new java.util.ArrayList<>();
            for (Building b : opponent.getBuildings()) {
                detail.add(b.getCard().getName()
                        + (revealed && b.getPerson() instanceof Worker w
                            ? (w.getMode() == Worker.Mode.ATTENTIVE ? " *" : " ~") : "")
                        + (b.getPerson() instanceof Assistant a ? " +" + a : "")
                        + ": " + b.goodsCount() + (b.goodsCount() == 1 ? " bem" : " bens")
                        + (b.goodsCount() > 0 ? " (" + b.goodsValue() + " moedas)" : ""));
            }
            detail.add("pontos: " + score.buildingPoints() + " estab. + " + score.assistantPoints() + " assist. + "
                    + score.goodsPoints() + " bens");
            if (last != null) detail.add("última vez: " + last.substring(opponent.getName().length() + 2));
            detail.add("* atento   ~ distraído   + assistente");
            views.add(new TablePanel.OpponentView(opponent.getName(), opponent == state.startingPlayer(), lines, detail));
        }
        table.setOpponents(views);
    }

    /** Contador (no título da janela): moedas em bens (o que vale para pagar e para pontuar), pontos e cartas na mão. */
    private void refreshInfo() {
        Scoring.Score score = Scoring.score(player);
        onInfo.accept(player.getName() + ":  " + score.coins() + " moedas em bens   |   " + score.total() + " pts   |   "
                + player.getHand().size() + " cartas   |   H: ajuda");
    }

    private String describePerson(Building building) {
        return building.getPerson() instanceof Assistant a ? a.toString() : "trabalhador";
    }

    /** Assistentes disponíveis na lateral: verde se o jogador tem as cores, azul se escolhido para contratar. */
    private void refreshAssistants() {
        table.setTiles(state.availableAssistants().stream().map(a -> new TablePanel.Tile(
                "#" + a.getNumber(),
                "custo " + a.getCost() + " · " + a.getPoints() + " pts",
                a + ": custo " + a.getCost() + ", " + a.getPoints() + " pontos; exige " + describeColors(a),
                a.getRequiredColors().stream().map(Game::chipColor).toList(),
                a == toHire ? PAYMENT_COLOR : player.hasColorsFor(a) ? HIREABLE_COLOR : UNAVAILABLE_COLOR)).toList());
    }

    /** Etiquetas sobre os estabelecimentos: assistentes, mudança, origem da cadeia, pagamento e trabalhador. */
    private void refreshBadges() {
        table.clearBadges();
        for (Assistant assistant : player.getAssistants()) {
            Building at = player.getAssistantBuilding(assistant);
            if (at != null) table.setBadge(at.getCard(), (assistant == moving ? "Mover " : "") + assistant, ASSISTANT_COLOR);
        }
        if (phase == Phase.MOVE && moveTarget != null) table.setBadge(moveTarget.getCard(), "Destino", ASSISTANT_COLOR);
        if (phase == Phase.CHAIN) {
            chainSources.values().forEach(b -> { if (b.goodsCount() > 0) table.setBadge(b.getCard(), "Origem", SOURCE_COLOR); });
        }
        payment.forEach((b, n) -> table.setBadge(b.getCard(), "Pagar " + n + " (" + n * b.getCard().getGoodValue() + ")",
                PAYMENT_COLOR));
        Building building = player.getWorkerBuilding();
        if (building != null) {
            boolean attentive = player.getWorker().getMode() == Worker.Mode.ATTENTIVE;
            table.setBadge(building.getCard(), attentive ? "Atento" : "Distraído",
                    attentive ? ATTENTIVE_COLOR : DISTRACTED_COLOR);
        }
        refreshProducerBadge();
    }

    /** Produção: o estabelecimento da vez mostra quantos recursos faltam (contando as cartas selecionadas). */
    private void refreshProducerBadge() {
        Building working = producers.peekFirst();
        if (phase != Phase.PRODUCE || working == null) return;
        java.util.Map<Resource, Integer> available = new java.util.EnumMap<>(Resource.class);
        available.putAll(Production.freeResources(player, market));
        player.getHand().stream().filter(Card::isSelected).forEach(c -> {
            if (c.getResource() != null) available.merge(c.getResource(), 1, Integer::sum);
        });
        int missing = Production.shortfall(working.getCard(), available);
        boolean enough = missing <= working.getPerson().missingAllowed();
        String who = working.getPerson() instanceof Assistant a ? a.toString()
                : player.getWorker().getMode() == Worker.Mode.ATTENTIVE ? "Atento" : "Distraído";
        table.setBadge(working.getCard(), who + (missing == 0 ? ": ok" : ": falta " + missing),
                enough ? ATTENTIVE_COLOR : MISSING_COLOR);
    }
}
