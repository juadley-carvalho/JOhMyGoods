package org.cardGames;

import java.util.List;

/** Tudo o que está na mesa: baralho, mercado, jogadores e assistentes disponíveis para contratar. */
public record GameState(Deck deck, Market market, List<Player> players, List<Assistant> availableAssistants) {

    /** O jogador humano (por enquanto, o primeiro). */
    public Player human() { return players.getFirst(); }
}
