package org.cardGames;

import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.function.Supplier;

public class MainWindow extends JFrame {

    private final Supplier<GameState> newGame;

    public MainWindow(Supplier<GameState> newGame) {
        super("Oh My Goods!");
        this.newGame = newGame;
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        TablePanel table = new TablePanel();
        table.setPreferredSize(new Dimension(1024, 680)); // área de jogo, sem contar a barra de título
        add(table, BorderLayout.CENTER);

        Game game = new Game(newGame.get(), table);
        table.onKey(KeyEvent.VK_SPACE, game::advance);
        table.onKey(KeyEvent.VK_R, game::replaceHand);
        table.onKey(KeyEvent.VK_C, game::planSelected);
        table.onKey(KeyEvent.VK_N, game::decline);
        table.onKey(KeyEvent.VK_K, game::runChain);
        game.onGameOver(this::showResult);

        pack();
        setLocationRelativeTo(null);
        setVisible(true);

        // Depois do primeiro layout da janela, para a distribuição inicial aparecer animada
        SwingUtilities.invokeLater(game::start);
    }

    /** Tela de resultado: detalhamento da pontuação e opção de nova partida. */
    private void showResult(List<Scoring.Score> ranking) {
        Object[] options = {"Nova partida", "Sair"};
        int choice = JOptionPane.showOptionDialog(this, resultText(ranking), "Resultado",
                JOptionPane.YES_NO_OPTION, JOptionPane.INFORMATION_MESSAGE, null, options, options[0]);
        if (choice == 0) {
            dispose();
            new MainWindow(newGame);
        } else if (choice == 1) {
            dispose();
            System.exit(0);
        }
    }

    static String resultText(List<Scoring.Score> ranking) {
        StringBuilder text = new StringBuilder("<html><table cellpadding=4>"
                + "<tr><th></th><th>Jogador</th><th>Estabel.</th><th>Assist.</th><th>Bens</th>"
                + "<th>Total</th><th>Sobra</th></tr>");
        for (int i = 0; i < ranking.size(); i++) {
            Scoring.Score s = ranking.get(i);
            text.append("<tr><td>").append(i + 1).append("º</td><td>").append(s.player().getName())
                    .append("</td><td align=right>").append(s.buildingPoints())
                    .append("</td><td align=right>").append(s.assistantPoints())
                    .append("</td><td align=right>").append(s.goodsPoints()).append(" (").append(s.coins()).append(" moedas)")
                    .append("</td><td align=right><b>").append(s.total())
                    .append("</b></td><td align=right>").append(s.leftoverCoins()).append("</td></tr>");
        }
        String winner = Scoring.isTie(ranking) ? "Empate!" : ranking.getFirst().player().getName() + " venceu!";
        return text.append("</table><br><b>").append(winner)
                .append("</b><br>Bens: 1 ponto a cada ").append(Scoring.COINS_PER_POINT)
                .append(" moedas; desempate pela sobra.</html>").toString();
    }
}
