package org.cardGames;

import org.cardGames.database.Database;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** Preparação da partida (manual, p. 2). */
public class Setup {

    public static final int MIN_PLAYERS = 2;
    public static final int MAX_PLAYERS = 4;
    public static final int INITIAL_COAL = 7;
    public static final int INITIAL_HAND = 5;

    public static List<Card> createDeck(Database database) {
        return database.getCards();
    }

    public static GameState newGame(Database database, List<String> names) {
        return newGame(database.getCards(), database.getCharcoalBurners(), database.getAssistants(),
                names, new Random());
    }

    /** Nomes padrão: "Você" para o humano, "Jogador N" para os oponentes. */
    public static List<String> defaultNames(int numPlayers) {
        List<String> names = new ArrayList<>();
        for (int i = 0; i < numPlayers; i++) names.add(i == 0 ? "Você" : "Jogador " + (i + 1));
        return names;
    }

    public static GameState newGame(List<Card> cards, List<Card> charcoalBurners, List<Assistant> assistants,
                                    int numPlayers, Random random) {
        return newGame(cards, charcoalBurners, assistants, defaultNames(numPlayers), random);
    }

    /**
     * Cada jogador recebe uma Carvoaria aleatória com 7 carvões (cartas da pilha viradas para baixo)
     * e 5 cartas na mão; são sorteados 2 assistentes por jogador.
     */
    public static GameState newGame(List<Card> cards, List<Card> charcoalBurners, List<Assistant> assistants,
                                    List<String> names, Random random) {
        int numPlayers = names.size();
        if (numPlayers < MIN_PLAYERS || numPlayers > MAX_PLAYERS) {
            throw new IllegalArgumentException("número de jogadores deve ser de 2 a 4: " + numPlayers);
        }
        if (charcoalBurners.size() < numPlayers) {
            throw new IllegalArgumentException("faltam Carvoarias para " + numPlayers + " jogadores");
        }

        Deck deck = new Deck(cards);

        List<Card> burners = new ArrayList<>(charcoalBurners);
        Collections.shuffle(burners, random);

        List<Player> players = new ArrayList<>();
        for (int i = 0; i < numPlayers; i++) {
            Player player = new Player(names.get(i));
            Building charcoal = player.build(burners.get(i));
            for (int c = 0; c < INITIAL_COAL; c++) charcoal.addGood(deck.draw());
            players.add(player);
        }
        for (Player player : players) {
            for (int c = 0; c < INITIAL_HAND; c++) player.receive(deck.draw());
        }

        List<Assistant> pool = new ArrayList<>(assistants);
        Collections.shuffle(pool, random);
        int count = Math.min(assistantsFor(numPlayers), pool.size());

        return new GameState(deck, new Market(), List.copyOf(players), new ArrayList<>(pool.subList(0, count)));
    }

    /** 4 assistentes com 2 jogadores, 6 com 3, 8 com 4. */
    public static int assistantsFor(int numPlayers) {
        return 2 * numPlayers;
    }
}
