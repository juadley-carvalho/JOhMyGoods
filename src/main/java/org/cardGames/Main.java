package org.cardGames;

import org.cardGames.database.Database;

import java.util.List;

public class Main {
    public static void main(String[] args) {
        Database database = new Database();
        List<Card> deck = Setup.createDeck(database);
        for (Card card : deck) {
            card.info();
        }

        Player player = new Player(deck);

        new MainWindow(player);
    }
}