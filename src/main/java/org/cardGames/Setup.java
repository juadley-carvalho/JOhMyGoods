package org.cardGames;

import org.cardGames.database.Database;

import java.util.List;

public class Setup {

    public static List<Card> createDeck(Database database) {
        return database.getCards();
    }
}
