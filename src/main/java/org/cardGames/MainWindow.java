package org.cardGames;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.KeyEvent;

public class MainWindow extends JFrame {

    public MainWindow(GameState state) {
        super("Oh My Goods!");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        TablePanel table = new TablePanel();
        table.setPreferredSize(new Dimension(1024, 680)); // área de jogo, sem contar a barra de título
        add(table, BorderLayout.CENTER);

        Game game = new Game(state, table);
        table.onKey(KeyEvent.VK_SPACE, game::advance);
        table.onKey(KeyEvent.VK_R, game::replaceHand);
        table.onKey(KeyEvent.VK_C, game::planSelected);
        table.onKey(KeyEvent.VK_N, game::decline);
        table.onKey(KeyEvent.VK_K, game::runChain);

        pack();
        setLocationRelativeTo(null);
        setVisible(true);

        // Depois do primeiro layout da janela, para a distribuição inicial aparecer animada
        SwingUtilities.invokeLater(game::start);
    }
}