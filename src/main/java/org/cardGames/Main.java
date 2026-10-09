package org.cardGames;

import org.cardGames.database.Database;

import java.util.List;

public class Main {
    public static void main(String[] args) {
        new MainWindow(Main::newGame);
    }

    /** Nova partida com os nomes escolhidos na tela inicial: lê as cartas do banco. */
    static GameState newGame(List<String> names) {
        Database database = new Database();
        GameState state = Setup.newGame(database, names);
        database.closeConnection();
        return state;
    }
}
