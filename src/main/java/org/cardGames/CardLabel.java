package org.cardGames;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class CardLabel extends JLabel {

    private final Card card;
    private final Icon normalIcon;
    private final Icon selectedIcon;

    public CardLabel(Card card, int width, int height) {
        this.card = card;
        this.normalIcon = new ImageIcon(card.getImage().getScaledInstance(width, height, Image.SCALE_SMOOTH));
        this.selectedIcon = new ImageIcon(card.getSelectedImage().getScaledInstance(width, height, Image.SCALE_SMOOTH));

        setIcon(normalIcon);
        setSize(width, height);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                card.toggleSelected();
                refresh();
            }
        });
    }

    public void refresh() {
        setIcon(card.isSelected() ? selectedIcon : normalIcon);
    }

    public Card getCard() { return card; }
}
