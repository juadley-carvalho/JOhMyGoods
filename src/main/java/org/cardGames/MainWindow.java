package org.cardGames;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public class MainWindow extends JFrame {

        private final int cardWidth = 118;
        private final int cardHeight = 184;

    public MainWindow(Player player) {
        super("Oh My Goods!");
        setSize(800, 600);
        setLayout(null);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        showPlayerHand(player);

        setVisible(true);
    }

    public void showPlayerHand(Player player) {
        int initialX = 200 + (player.getHand().size() * 30);
        for (Card card : player.getHand()) {
            CardLabel label = new CardLabel(card, cardWidth, cardHeight);
            label.setLocation(initialX, 200);
            initialX -= 30;
            add(label);
        }
    }
}
