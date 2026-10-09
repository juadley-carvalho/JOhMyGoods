package org.cardGames;

import java.util.List;

/**
 * Controlador do jogo: aplica as regras ao modelo (Deck, Player) e pede ao TablePanel
 * que anime o que aconteceu. Por enquanto cobre as fases I, II e III do manual.
 */
public class Game {

    private enum Phase {
        NEW_HAND("Fase I - Nova mão de cartas", "R: trocar a mão inteira (opcional)   |   ESPAÇO: receber 2 cartas"),
        SUNRISE("Fase II - Nascer do Sol", "ESPAÇO: abrir o mercado até aparecerem 2 meios sóis"),
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

    private final GameState state;
    private final Deck deck;
    private final Player player;
    private final Market market;
    private final TablePanel table;

    private Phase phase = Phase.NEW_HAND;
    private boolean handReplaced;
    private int clock; // atraso acumulado da ação em andamento: faz as cartas saírem uma de cada vez

    public Game(GameState state, TablePanel table) {
        this.state = state;
        this.deck = state.deck();
        this.player = state.human();
        this.market = state.market();
        this.table = table;
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
        for (Card card : charcoal.getGoods()) {
            send(card, Zone.GOODS);
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
                phase = Phase.SUNSET;
            }
            case SUNSET -> {
                openMarketRow(Zone.MARKET_SUNSET);
                phase = Phase.PRODUCE;
            }
            case PRODUCE -> {
                closeMarket();
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

    // ------------------------------------------------------------- internos

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
        table.setStatus(phase.title + "   |   " + phase.hint);
    }
}