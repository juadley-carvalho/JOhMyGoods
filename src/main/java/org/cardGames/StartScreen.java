package org.cardGames;

import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.List;

/** Tela inicial: número de jogadores (2 a 4) e nomes. O primeiro é o humano; os demais, o computador. */
public final class StartScreen {

    private StartScreen() { }

    /** Mostra a tela e devolve os nomes escolhidos, ou null se o jogador cancelar. */
    public static List<String> ask() {
        Integer[] counts = new Integer[Setup.MAX_PLAYERS - Setup.MIN_PLAYERS + 1];
        for (int i = 0; i < counts.length; i++) counts[i] = Setup.MIN_PLAYERS + i;
        JComboBox<Integer> count = new JComboBox<>(counts);

        List<String> defaults = Setup.defaultNames(Setup.MAX_PLAYERS);
        List<JTextField> fields = new ArrayList<>();
        JPanel panel = new JPanel(new GridLayout(0, 2, 8, 6));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        panel.add(new JLabel("Jogadores:"));
        panel.add(count);
        for (int i = 0; i < Setup.MAX_PLAYERS; i++) {
            JTextField field = new JTextField(defaults.get(i), 14);
            fields.add(field);
            panel.add(new JLabel(i == 0 ? "Seu nome:" : "Oponente " + i + " (computador):"));
            panel.add(field);
        }
        Runnable enable = () -> {
            for (int i = 0; i < fields.size(); i++) fields.get(i).setEnabled(i < (Integer) count.getSelectedItem());
        };
        count.addActionListener(e -> enable.run());
        enable.run();

        int choice = JOptionPane.showConfirmDialog(null, panel, "Oh My Goods! - Nova partida",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (choice != JOptionPane.OK_OPTION) return null;

        List<String> names = new ArrayList<>();
        for (int i = 0; i < (Integer) count.getSelectedItem(); i++) {
            String name = fields.get(i).getText().trim();
            names.add(name.isEmpty() ? defaults.get(i) : name);
        }
        return names;
    }
}
