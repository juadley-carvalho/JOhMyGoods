package org.cardGames;

import java.util.ArrayList;
import java.util.List;

/**
 * Tudo o que está na mesa: baralho, mercado, jogadores, assistentes disponíveis para contratar
 * e quem é o jogador inicial da rodada.
 */
public final class GameState {

    private final Deck deck;
    private final Market market;
    private final List<Player> players;
    private final List<Assistant> availableAssistants;
    private int startingPlayer;
    private boolean finalRound;  // a rodada em andamento é a última
    private boolean gameOver;

    public GameState(Deck deck, Market market, List<Player> players, List<Assistant> availableAssistants) {
        this.deck = deck;
        this.market = market;
        this.players = List.copyOf(players);
        this.availableAssistants = availableAssistants;
    }

    public Deck deck() { return deck; }
    public Market market() { return market; }
    public List<Player> players() { return players; }
    public List<Assistant> availableAssistants() { return availableAssistants; }

    /** O jogador humano (o primeiro); os outros são controlados pelo computador ({@link Bot}). */
    public Player human() { return players.getFirst(); }

    /** Oponentes controlados pelo computador, na ordem da mesa. */
    public List<Player> opponents() { return players.subList(1, players.size()); }

    public Player startingPlayer() { return players.get(startingPlayer); }

    /** Ordem da Fase IV: o jogador inicial, depois os demais em sentido horário (ordem da lista). */
    public List<Player> turnOrder() {
        List<Player> order = new ArrayList<>();
        for (int i = 0; i < players.size(); i++) {
            order.add(players.get((startingPlayer + i) % players.size()));
        }
        return order;
    }

    /** Fim da rodada: o próximo jogador em sentido horário passa a ser o inicial. */
    public void passStartingPlayer() {
        startingPlayer = (startingPlayer + 1) % players.size();
    }

    /** Estabelecimentos (contando a Carvoaria) que disparam o fim da partida. */
    public static final int BUILDINGS_TO_END = 8;

    public boolean isFinalRound() { return finalRound; }
    public boolean isGameOver() { return gameOver; }

    /**
     * Fim de rodada (depois de toda a Fase IV): se esta era a rodada final, a partida acaba;
     * senão, se alguém chegou a 8 estabelecimentos, a próxima rodada é a última,
     * com as cadeias de produção liberadas em todos os estabelecimentos.
     */
    public void endRound() {
        if (finalRound) {
            gameOver = true;
            players.forEach(p -> p.setChainsUnlocked(false));
            return;
        }
        passStartingPlayer();
        if (players.stream().anyMatch(p -> p.getBuildings().size() >= BUILDINGS_TO_END)) {
            finalRound = true;
            players.forEach(p -> p.setChainsUnlocked(true));
        }
    }
}
