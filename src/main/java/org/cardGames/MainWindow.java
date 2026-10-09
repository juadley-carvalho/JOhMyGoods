package org.cardGames;

import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class MainWindow extends JFrame {

    private final Supplier<GameState> newGame;
    private final TablePanel table = new TablePanel();

    public MainWindow(Supplier<GameState> newGame) {
        super("Oh My Goods!");
        this.newGame = newGame;
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        // Área de jogo, sem contar a barra de título: maior com mais jogadores, limitada à tela
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        table.setPreferredSize(new Dimension(Math.min(1280, screen.width - 40), Math.min(760, screen.height - 100)));
        add(table, BorderLayout.CENTER);

        Game game = new Game(newGame.get(), table);
        table.onKey(KeyEvent.VK_SPACE, game::advance);
        table.onKey(KeyEvent.VK_R, game::replaceHand);
        table.onKey(KeyEvent.VK_C, game::planSelected);
        table.onKey(KeyEvent.VK_N, game::decline);
        table.onKey(KeyEvent.VK_K, game::runChain);
        table.onKey(KeyEvent.VK_H, this::showHelp);
        game.onGameOver(this::showResult);

        pack();
        setLocationRelativeTo(null);
        setVisible(true);

        // Depois do primeiro layout da janela, para a distribuição inicial aparecer animada
        SwingUtilities.invokeLater(game::start);
    }

    private void showHelp() {
        JOptionPane.showMessageDialog(this, Help.TEXT, "Ajuda", JOptionPane.PLAIN_MESSAGE);
    }

    /** Tela de resultado, desenhada sobre a mesa: detalhamento da pontuação e opção de nova partida. */
    private void showResult(List<Scoring.Score> ranking) {
        table.showResult(resultView(ranking), () -> {
            dispose();
            new MainWindow(newGame);
        }, () -> {
            dispose();
            System.exit(0);
        });
    }

    /** Tabela do resultado: posição, jogador, pontos por categoria, total e moedas que sobraram (desempate). */
    static TablePanel.ResultView resultView(List<Scoring.Score> ranking) {
        List<List<String>> rows = new ArrayList<>();
        for (int i = 0; i < ranking.size(); i++) {
            Scoring.Score s = ranking.get(i);
            rows.add(List.of((i + 1) + "º", s.player().getName(), String.valueOf(s.buildingPoints()),
                    String.valueOf(s.assistantPoints()), s.goodsPoints() + " (" + s.coins() + " moedas)",
                    String.valueOf(s.total()), String.valueOf(s.leftoverCoins())));
        }
        String winner = Scoring.isTie(ranking) ? "Empate!" : ranking.getFirst().player().getName() + " venceu!";
        return new TablePanel.ResultView(List.of("", "Jogador", "Estabel.", "Assist.", "Bens", "Total", "Sobra"),
                rows, winner, "Bens: 1 ponto a cada " + Scoring.COINS_PER_POINT + " moedas; desempate pela sobra.");
    }
}
