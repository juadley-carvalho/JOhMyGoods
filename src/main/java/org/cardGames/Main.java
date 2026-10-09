package org.cardGames;

import org.cardGames.database.Database;

public class Main {
    public static void main(String[] args) {
        Database database = new Database();
        GameState state = Setup.newGame(database, 2);
        database.closeConnection();

        new MainWindow(state);
    }
}