package org.cardGames;

import org.cardGames.database.Database;

import java.util.List;

public class Main {
    public static void main(String[] args) {
        new MainWindow(Main::newGame);
    }

    /** Nova partida: pergunta jogadores e nomes na tela inicial e lê as cartas do banco. Cancelar encerra o programa. */
    static GameState newGame() {
        List<String> names = StartScreen.ask();
        if (names == null) System.exit(0);
        Database database = new Database();
        GameState state = Setup.newGame(database, names);
        database.closeConnection();
        return state;
    }
}
