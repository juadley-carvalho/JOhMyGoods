package org.cardGames;

import org.cardGames.database.Database;

public class Main {
    public static void main(String[] args) {
        new MainWindow(Main::newGame);
    }

    /** Nova partida com 2 jogadores, lendo as cartas do banco. */
    static GameState newGame() {
        Database database = new Database();
        GameState state = Setup.newGame(database, 2);
        database.closeConnection();
        return state;
    }
}
