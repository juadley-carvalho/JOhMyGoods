package org.cardGames;

import org.cardGames.database.Database;

import java.util.List;

public class Main {
    public static void main(String[] args) {
        Database database = new Database();
        List<Card> cards = Setup.createDeck(database);

        Deck deck = new Deck(cards);
        Player player = new Player();

        new MainWindow(deck, player);
    }
}