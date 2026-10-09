package org.cardGames;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.plaf.basic.BasicScrollBarUI;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.StyleSheet;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Ajuda desenhada sobre a mesa, dentro da própria janela (vai no glass pane): a mesa escurece e as regras
 * aparecem num painel no estilo da tela de resultado. Fecha pelo botão, pelo X, clicando fora ou com H/Esc.
 */
public final class HelpOverlay extends JComponent {

    private static final int MAX_WIDTH = 980;
    private static final int MAX_HEIGHT = 700;
    private static final int MARGIN = 20;
    private static final int ARC = 16;

    private final JPanel card = new JPanel(new BorderLayout()) {
        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = StartScreen.antialiased(g);
            g2.setColor(StartScreen.PANEL);
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, ARC, ARC);
            g2.setColor(StartScreen.GOLD);
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, ARC, ARC);
            g2.dispose();
        }
    };
    private final JScrollPane scroll;

    public HelpOverlay() {
        setOpaque(false);
        setLayout(null);
        setVisible(false);

        card.setOpaque(false);
        card.setBorder(BorderFactory.createEmptyBorder(16, 26, 14, 26));
        card.add(header(), BorderLayout.NORTH);
        scroll = new JScrollPane(content());
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 1, 0, StartScreen.FIELD_OFF),
                BorderFactory.createEmptyBorder(6, 0, 6, 0)));
        scroll.getVerticalScrollBar().setUI(new DarkScrollBar());
        scroll.getVerticalScrollBar().setOpaque(false);
        scroll.getVerticalScrollBar().setPreferredSize(new Dimension(10, 0));
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        card.add(scroll, BorderLayout.CENTER);
        card.add(footer(), BorderLayout.SOUTH);
        add(card);

        // Clique fora do painel fecha; os eventos de mouse não chegam à mesa enquanto a ajuda está aberta
        MouseAdapter outside = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (!card.getBounds().contains(e.getPoint())) close();
            }
        };
        addMouseListener(outside);
        addMouseMotionListener(new MouseAdapter() { });
    }

    /** Abre a ajuda, rolada para o topo. */
    public void open() {
        setVisible(true);
        doLayout();
        scroll.getVerticalScrollBar().setValue(0);
        repaint();
    }

    public void close() {
        setVisible(false);
    }

    public void toggle() {
        if (isVisible()) close();
        else open();
    }

    @Override
    public void doLayout() {
        int w = Math.min(MAX_WIDTH, getWidth() - 2 * MARGIN);
        int h = Math.min(MAX_HEIGHT, getHeight() - 2 * MARGIN);
        card.setBounds(new Rectangle((getWidth() - w) / 2, (getHeight() - h) / 2, w, h));
        card.validate();
    }

    @Override
    protected void paintComponent(Graphics g) {
        g.setColor(new Color(0, 0, 0, 160));
        g.fillRect(0, 0, getWidth(), getHeight());
    }

    // ---------------------------------------------------------------- partes

    /** Título dourado, subtítulo e o X de fechar. */
    private JComponent header() {
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));
        JLabel title = new JLabel("Como jogar");
        title.setForeground(StartScreen.GOLD);
        title.setFont(Hud.title(26f));
        JLabel subtitle = new JLabel("Oh My Goods! - regras resumidas e controles");
        subtitle.setForeground(StartScreen.SOFT);
        subtitle.setFont(Hud.regular(13.5f));
        JPanel titles = new JPanel(new BorderLayout());
        titles.setOpaque(false);
        titles.add(title, BorderLayout.NORTH);
        titles.add(subtitle, BorderLayout.SOUTH);
        header.add(titles, BorderLayout.CENTER);

        StartScreen.PaintedButton x = new StartScreen.PaintedButton("×", this::close) {
            @Override
            Color fill(boolean hover) {
                return hover ? new Color(0x2E5E3E) : StartScreen.FIELD;
            }

            @Override
            Color outline() {
                return StartScreen.FIELD_OFF;
            }
        };
        x.setFont(Hud.bold(20f));
        x.setPreferredSize(new Dimension(34, 34));
        x.setToolTipText("Fechar (H ou Esc)");
        JPanel corner = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        corner.setOpaque(false);
        corner.add(x);
        header.add(corner, BorderLayout.EAST);
        return header;
    }

    /** Regras em HTML, com a paleta da mesa. */
    private static JComponent content() {
        HTMLEditorKit kit = new HTMLEditorKit();
        StyleSheet css = new StyleSheet();
        css.addStyleSheet(kit.getStyleSheet());
        css.addRule("body { color: #FFFFFF; font-family: '" + Hud.htmlFamily() + "'; font-size: 12pt; margin: 0 }");
        css.addRule("h2 { color: #FFD54F; font-size: 14pt; margin-top: 10px; margin-bottom: 5px }");
        css.addRule("p { margin-top: 0; margin-bottom: 6px }");
        css.addRule("td.def { padding-bottom: 5px; color: #FFFFFF }");
        css.addRule("td.term { padding-bottom: 5px; color: #C8E6C9; font-weight: bold }");
        css.addRule("td.step { padding-bottom: 5px; color: #FFD54F; font-weight: bold }");
        css.addRule("td.keycell { padding-bottom: 5px }");
        css.addRule("td.key { background-color: #FFD54F; color: #1F3A2A; font-weight: bold; font-size: 11pt;"
                + " padding: 1px 4px }");
        css.addRule("p.score { background-color: #163022; color: #FFE082; padding: 7px 9px }");
        css.addRule("p.note { background-color: #163022; color: #C8E6C9; padding: 7px 9px }");
        kit.setStyleSheet(css);

        JEditorPane pane = new JEditorPane();
        pane.setEditorKit(kit);
        pane.setText(Help.TEXT);
        pane.setEditable(false);
        pane.setFocusable(false);
        pane.setOpaque(false);
        pane.setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 12));
        return pane;
    }

    /** "Voltar ao jogo" e a lembrança dos atalhos. */
    private JComponent footer() {
        JPanel footer = new JPanel(new BorderLayout());
        footer.setOpaque(false);
        footer.setBorder(BorderFactory.createEmptyBorder(12, 0, 0, 0));
        JLabel hint = new JLabel("H ou Esc fecha a ajuda");
        hint.setForeground(StartScreen.SOFT);
        hint.setFont(Hud.regular(12.5f));
        footer.add(hint, BorderLayout.WEST);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        buttons.setOpaque(false);
        buttons.add(new StartScreen.ActionButton("Voltar ao jogo", true, this::close));
        footer.add(buttons, BorderLayout.EAST);
        return footer;
    }

    /** Barra de rolagem fina e escura, no tom da mesa. */
    private static final class DarkScrollBar extends BasicScrollBarUI {
        @Override
        protected void configureScrollBarColors() {
            thumbColor = StartScreen.FIELD_OFF;
            trackColor = StartScreen.FIELD;
        }

        @Override
        protected JButton createDecreaseButton(int orientation) {
            return zeroButton();
        }

        @Override
        protected JButton createIncreaseButton(int orientation) {
            return zeroButton();
        }

        private static JButton zeroButton() {
            JButton b = new JButton();
            b.setPreferredSize(new Dimension(0, 0));
            return b;
        }

        @Override
        protected void paintTrack(Graphics g, JComponent c, Rectangle r) {
            g.setColor(trackColor);
            g.fillRect(r.x, r.y, r.width, r.height);
        }

        @Override
        protected void paintThumb(Graphics g, JComponent c, Rectangle r) {
            if (r.isEmpty() || !scrollbar.isEnabled()) return;
            Graphics2D g2 = StartScreen.antialiased(g);
            g2.setColor(isThumbRollover() ? StartScreen.SOFT : thumbColor);
            g2.fillRoundRect(r.x + 1, r.y, r.width - 2, r.height, 8, 8);
            g2.dispose();
        }
    }

    /** Para as capturas: dá o tamanho, monta o painel e abre. */
    void openFor(int width, int height) {
        setSize(width, height);
        open();
        layoutTree(card); // sem janela, validate() não desce na árvore
    }

    private static void layoutTree(java.awt.Container c) {
        c.doLayout();
        for (java.awt.Component child : c.getComponents()) {
            if (child instanceof java.awt.Container container) layoutTree(container);
        }
    }
}
