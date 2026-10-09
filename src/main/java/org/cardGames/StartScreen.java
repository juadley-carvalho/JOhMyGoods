package org.cardGames;

import javax.imageio.ImageIO;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * Tela inicial, desenhada na própria janela do jogo (mesma mesa verde e painel da tela de resultado):
 * número de jogadores (2 a 4) e nomes. O primeiro é o humano; os demais, o computador.
 */
public final class StartScreen extends JPanel {

    // Mesma paleta da mesa (TablePanel)
    private static final Color FELT = new Color(0x2E5E3E);
    private static final Color PANEL = new Color(0x1F3A2A);
    private static final Color GOLD = new Color(0xFFD54F);
    private static final Color SOFT = new Color(0xC8E6C9);
    private static final Color FIELD = new Color(0x163022);
    private static final Color FIELD_OFF = new Color(0x3B5A47);

    /** Cartas do leque decorativo de cada lado (sorteadas a cada vez que a tela aparece). */
    private static final int FAN_CARDS = 3;
    private static final int CARD_COUNT = 98;

    private final List<BufferedImage> fan = new ArrayList<>();
    private final List<JTextField> fields = new ArrayList<>();
    private final List<JComponent> rows = new ArrayList<>();
    private final List<ChoiceButton> countButtons = new ArrayList<>();
    private final List<String> defaults = Setup.defaultNames(Setup.MAX_PLAYERS);
    private int count;

    /**
     * @param previous nomes da última partida (para "Jogar de novo"), ou null
     * @param onStart  recebe os nomes escolhidos
     * @param onExit   botão "Sair" (ou Esc)
     */
    public StartScreen(List<String> previous, Consumer<List<String>> onStart, Runnable onExit) {
        super(new GridBagLayout());
        setBackground(FELT);
        loadFan();

        JPanel box = new JPanel(new GridBagLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = antialiased(g);
                g2.setColor(new Color(0, 0, 0, 90));
                g2.fillRoundRect(6, 8, getWidth() - 8, getHeight() - 8, 18, 18); // sombra
                g2.setColor(PANEL);
                g2.fillRoundRect(0, 0, getWidth() - 7, getHeight() - 9, 16, 16);
                g2.setColor(GOLD);
                g2.setStroke(new BasicStroke(2f));
                g2.drawRoundRect(1, 1, getWidth() - 9, getHeight() - 11, 16, 16);
                g2.dispose();
            }
        };
        box.setOpaque(false);
        box.setBorder(BorderFactory.createEmptyBorder(22, 32, 30, 39));

        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridwidth = 2;
        c.fill = GridBagConstraints.HORIZONTAL;

        box.add(new Title(), c);
        c.insets = new Insets(0, 0, 18, 0);
        box.add(label("Nova partida", SOFT, Font.PLAIN, 14f, JLabel.CENTER), c);

        // Número de jogadores: botões 2, 3, 4
        c.insets = new Insets(0, 0, 6, 0);
        box.add(label("Jogadores", SOFT, Font.BOLD, 13f, JLabel.LEFT), c);
        JPanel counts = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        counts.setOpaque(false);
        for (int n = Setup.MIN_PLAYERS; n <= Setup.MAX_PLAYERS; n++) {
            int value = n;
            ChoiceButton b = new ChoiceButton(String.valueOf(n), () -> setCount(value));
            countButtons.add(b);
            if (n > Setup.MIN_PLAYERS) counts.add(Box.createHorizontalStrut(8));
            counts.add(b);
        }
        c.insets = new Insets(0, 0, 16, 0);
        box.add(counts, c);

        // Nomes: o humano e os oponentes do computador
        for (int i = 0; i < Setup.MAX_PLAYERS; i++) {
            String name = previous != null && i < previous.size() ? previous.get(i) : defaults.get(i);
            JTextField field = field(name);
            fields.add(field);
            JPanel row = new JPanel(new GridBagLayout());
            row.setOpaque(false);
            GridBagConstraints rc = new GridBagConstraints();
            rc.anchor = GridBagConstraints.WEST;
            rc.insets = new Insets(0, 0, 0, 12);
            JLabel tag = label(i == 0 ? "Você" : "Oponente " + i, i == 0 ? GOLD : Color.WHITE, Font.BOLD, 13f,
                    JLabel.LEFT);
            JLabel sub = label(i == 0 ? "seu nome" : "computador", SOFT, Font.PLAIN, 11f, JLabel.LEFT);
            JPanel tags = new JPanel(new GridBagLayout());
            tags.setOpaque(false);
            tags.setPreferredSize(new Dimension(100, 34));
            GridBagConstraints tc = new GridBagConstraints();
            tc.gridx = 0;
            tc.anchor = GridBagConstraints.WEST;
            tc.weightx = 1;
            tags.add(tag, tc);
            tags.add(sub, tc);
            row.add(tags, rc);
            rc.insets = new Insets(0, 0, 0, 0);
            rc.weightx = 1;
            rc.fill = GridBagConstraints.HORIZONTAL;
            row.add(field, rc);
            rows.add(row);
            c.insets = new Insets(0, 0, 8, 0);
            box.add(row, c);
        }

        // Botões
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.CENTER, 14, 0));
        buttons.setOpaque(false);
        ActionButton start = new ActionButton("Começar partida", true, () -> onStart.accept(names()));
        ActionButton exit = new ActionButton("Sair", false, onExit);
        buttons.add(start);
        buttons.add(exit);
        c.insets = new Insets(18, 0, 0, 0);
        box.add(buttons, c);

        add(box);

        bind(KeyEvent.VK_ENTER, () -> onStart.accept(names()));
        bind(KeyEvent.VK_ESCAPE, onExit);

        int initial = previous != null ? previous.size() : Setup.MIN_PLAYERS;
        setCount(Math.max(Setup.MIN_PLAYERS, Math.min(Setup.MAX_PLAYERS, initial)));
        SwingUtilities.invokeLater(() -> fields.getFirst().requestFocusInWindow());
    }

    private void setCount(int n) {
        count = n;
        for (int i = 0; i < countButtons.size(); i++) countButtons.get(i).setSelected(i + Setup.MIN_PLAYERS == n);
        for (int i = 0; i < rows.size(); i++) rows.get(i).setVisible(i < n);
        revalidate();
        repaint();
    }

    /** Nomes escolhidos; campo vazio fica com o nome padrão. */
    private List<String> names() {
        List<String> names = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String name = fields.get(i).getText().trim();
            names.add(name.isEmpty() ? defaults.get(i) : name);
        }
        return names;
    }

    private void bind(int keyCode, Runnable action) {
        String name = "start-" + keyCode;
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(keyCode, 0), name);
        getActionMap().put(name, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                action.run();
            }
        });
    }

    // ---------------------------------------------------------------- fundo

    /** Sorteia cartas diferentes para os dois leques. */
    private void loadFan() {
        List<Integer> numbers = new ArrayList<>();
        for (int i = 1; i <= CARD_COUNT; i++) numbers.add(i);
        Collections.shuffle(numbers);
        for (int n : numbers) {
            if (fan.size() == 2 * FAN_CARDS) break;
            URL url = StartScreen.class.getResource(String.format("/images/cards/carta_%03d.png", n));
            if (url == null) continue;
            try {
                BufferedImage img = ImageIO.read(url);
                if (img != null) fan.add(img);
            } catch (IOException ignored) {
                // sem a carta, o leque fica menor
            }
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g); // feltro
        Graphics2D g2 = antialiased(g);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        int w = getWidth();
        int h = getHeight();

        // Vinheta: centro um pouco mais claro, bordas mais escuras, como uma mesa iluminada
        float radius = Math.max(w, h) * 0.75f;
        g2.setPaint(new RadialGradientPaint(w / 2f, h / 2f, radius, new float[]{0f, 1f},
                new Color[]{new Color(255, 255, 255, 18), new Color(0, 0, 0, 110)}));
        g2.fillRect(0, 0, w, h);

        // Leques de cartas nas laterais, só se houver espaço ao lado do painel
        int half = fan.size() / 2;
        if (w >= 900 && half > 0) {
            drawFan(g2, fan.subList(0, half), w * 0.17, h * 0.58, -1);
            drawFan(g2, fan.subList(half, fan.size()), w * 0.83, h * 0.58, 1);
        }
        g2.dispose();
    }

    /** Cartas abertas em leque, giradas em torno de um ponto abaixo delas; side inclina o leque para fora. */
    private void drawFan(Graphics2D g, List<BufferedImage> cards, double cx, double cy, int side) {
        int cw = (int) (CardSprite.WIDTH * 1.25);
        int ch = (int) (CardSprite.HEIGHT * 1.25);
        double spread = Math.toRadians(16);
        for (int i = 0; i < cards.size(); i++) {
            double angle = side * Math.toRadians(8) + (i - (cards.size() - 1) / 2.0) * spread;
            AffineTransform saved = g.getTransform();
            g.translate(cx, cy + ch * 0.55);
            g.rotate(angle);
            g.translate(-cw / 2.0, -ch * 1.05);
            g.setColor(new Color(0, 0, 0, 70));
            g.fillRoundRect(5, 7, cw, ch, 14, 14);
            g.drawImage(cards.get(i), 0, 0, cw, ch, null);
            g.setColor(new Color(0, 0, 0, 60));
            g.drawRoundRect(0, 0, cw - 1, ch - 1, 12, 12);
            g.setTransform(saved);
        }
    }

    private static Graphics2D antialiased(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        return g2;
    }

    // ---------------------------------------------------------------- componentes

    private static JLabel label(String text, Color color, int style, float size, int align) {
        JLabel label = new JLabel(text, align);
        label.setForeground(color);
        label.setFont(label.getFont().deriveFont(style, size));
        return label;
    }

    /** Campo escuro com texto branco; a borda fica dourada quando o campo está em foco. */
    private static JTextField field(String text) {
        JTextField field = new JTextField(text, 16);
        field.setFont(field.getFont().deriveFont(Font.PLAIN, 14f));
        field.setBackground(FIELD);
        field.setForeground(Color.WHITE);
        field.setCaretColor(GOLD);
        field.setSelectionColor(new Color(0x2E7D32));
        field.setSelectedTextColor(Color.WHITE);
        Runnable border = () -> field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(field.hasFocus() ? GOLD : FIELD_OFF, 1),
                BorderFactory.createEmptyBorder(6, 8, 6, 8)));
        border.run();
        field.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                border.run();
                field.selectAll();
            }

            @Override
            public void focusLost(FocusEvent e) {
                border.run();
            }
        });
        return field;
    }

    /** Título do jogo em dourado, com sombra. */
    private static final class Title extends JComponent {
        private static final String TEXT = "Oh My Goods!";

        Title() {
            setFont(new JLabel().getFont().deriveFont(Font.BOLD, 34f));
            setPreferredSize(new Dimension(320, 48));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = antialiased(g);
            g2.setFont(getFont());
            FontMetrics fm = g2.getFontMetrics();
            int x = (getWidth() - fm.stringWidth(TEXT)) / 2;
            int y = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;
            g2.setColor(new Color(0, 0, 0, 130));
            g2.drawString(TEXT, x + 2, y + 3);
            g2.setColor(GOLD);
            g2.drawString(TEXT, x, y);
            g2.dispose();
        }
    }

    /** Botão desenhado à mão, no estilo dos botões da tela de resultado. */
    private abstract static class PaintedButton extends JButton {
        PaintedButton(String text, Runnable action) {
            super(text);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setFocusable(false); // Enter/Esc ficam com a tela; os campos mantêm o foco
            setRolloverEnabled(true);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setFont(getFont().deriveFont(Font.BOLD, 14f));
            addActionListener(e -> action.run());
        }

        abstract Color fill(boolean hover);

        Color textColor() {
            return Color.WHITE;
        }

        Color outline() {
            return Color.WHITE;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = antialiased(g);
            boolean hover = getModel().isRollover();
            g2.setColor(fill(hover));
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
            g2.setColor(outline());
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
            g2.setColor(textColor());
            g2.setFont(getFont());
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(getText(), (getWidth() - fm.stringWidth(getText())) / 2,
                    (getHeight() + fm.getAscent() - fm.getDescent()) / 2);
            g2.dispose();
        }
    }

    /** "Começar partida" (verde) e "Sair" (cinza), como "Jogar de novo" e "Sair" no resultado. */
    private static final class ActionButton extends PaintedButton {
        private final boolean primary;

        ActionButton(String text, boolean primary, Runnable action) {
            super(text, action);
            this.primary = primary;
            setPreferredSize(new Dimension(primary ? 180 : 110, 38));
        }

        @Override
        Color fill(boolean hover) {
            return primary ? new Color(hover ? 0x2E7D32 : 0x1B5E20) : new Color(hover ? 0x666666 : 0x444444);
        }
    }

    /** Botão de escolha do número de jogadores: o escolhido fica dourado. */
    private static final class ChoiceButton extends PaintedButton {
        ChoiceButton(String text, Runnable action) {
            super(text, action);
            setFont(getFont().deriveFont(Font.BOLD, 16f));
            setPreferredSize(new Dimension(52, 40));
        }

        @Override
        Color fill(boolean hover) {
            if (isSelected()) return GOLD;
            return hover ? new Color(0x2E5E3E) : FIELD;
        }

        @Override
        Color textColor() {
            return isSelected() ? PANEL : Color.WHITE;
        }

        @Override
        Color outline() {
            return isSelected() ? GOLD : FIELD_OFF;
        }
    }
}
