package org.cardGames;

import java.util.ArrayList;
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

    private static final int INITIAL_HAND = 5;
    private static final int STEP_MS = 120; // intervalo entre uma carta e a próxima

    private final Deck deck;
    private final Player player;
    private final TablePanel table;
    private final List<Card> market = new ArrayList<>();

    private Phase phase = Phase.NEW_HAND;
    private boolean handReplaced;
    private int clock; // atraso acumulado da ação em andamento: faz as cartas saírem uma de cada vez

    public Game(Deck deck, Player player, TablePanel table) {
        this.deck = deck;
        this.player = player;
        this.table = table;
    }

    /** Preparação: monta a pilha de compras na mesa e distribui a mão inicial. */
    public void start() {
        for (Card card : deck.getDrawPile()) {
            table.addCard(card, Zone.DECK);
        }
        clock = 0;
        for (int i = 0; i < INITIAL_HAND; i++) {
            deal();
        }
        updateStatus();
    }

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
            market.add(card);
            send(card, row);
            if (card.isSun()) suns++;

        }
    }

    /** Fim da fase IV: o mercado inteiro vai para o descarte. */
    private void closeMarket() {
        for (Card card : market) {
            deck.discard(card);
            send(card, Zone.DISCARD);
        }
        market.clear();
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