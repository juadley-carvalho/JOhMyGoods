package org.cardGames;

import javax.swing.UIManager;
import javax.swing.plaf.FontUIResource;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.GraphicsEnvironment;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.io.InputStream;

/**
 * Visual dos painéis desenhados sobre a mesa: fontes do jogo (Nunito para o texto, Cinzel para os títulos,
 * ambas em /fonts, licença OFL), painéis com sombra, teclas desenhadas e ícones.
 */
final class Hud {

    static final Color GOLD = new Color(0xFFD54F);
    static final Color SOFT = new Color(0xC8E6C9);
    static final Color INK = new Color(0x13241A);
    static final Color PANEL_TOP = new Color(28, 52, 38, 240);
    static final Color PANEL_BOTTOM = new Color(18, 36, 26, 240);
    static final Color NARRATION = new Color(0x90CAF9);
    static final Color WARNING = new Color(0xFFB300);

    private static final Font REGULAR = load("Nunito-400.ttf");
    private static final Font SEMIBOLD = load("Nunito-600.ttf");
    private static final Font BOLD = load("Nunito-700.ttf");
    private static final Font HEAVY = load("Nunito-800.ttf");
    private static final Font TITLE = load("Cinzel-700.ttf");

    private Hud() { }

    static Font regular(float size) { return REGULAR.deriveFont(size); }
    static Font semibold(float size) { return SEMIBOLD.deriveFont(size); }
    static Font bold(float size) { return BOLD.deriveFont(size); }
    static Font heavy(float size) { return HEAVY.deriveFont(size); }
    static Font title(float size) { return TITLE.deriveFont(size); }

    /** Fonte do jogo como padrão dos componentes Swing (tela inicial e ajuda). */
    static void install() {
        FontUIResource font = new FontUIResource(regular(13f));
        for (String key : new String[]{"Label.font", "Button.font", "TextField.font", "ToolTip.font", "Panel.font",
                "EditorPane.font"}) {
            UIManager.put(key, font);
        }
    }

    /**
     * Família do texto para o HTML da ajuda (as fontes ficam registradas no Java, que acha a negrita pela família).
     * O nome interno dos arquivos é "Nunito ExtraLight", mas o regular e o negrito são os de peso 400 e 700.
     */
    static String htmlFamily() {
        return REGULAR.getFamily();
    }

    /** Lê a fonte de dentro do jar e a registra; sem ela (arquivo faltando), cai na fonte padrão do Java. */
    private static Font load(String file) {
        try (InputStream in = Hud.class.getResourceAsStream("/fonts/" + file)) {
            if (in != null) {
                Font font = Font.createFont(Font.TRUETYPE_FONT, in);
                GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(font);
                return font;
            }
        } catch (Exception ignored) {
            // cai na fonte padrão
        }
        return new Font(Font.DIALOG, file.contains("400") ? Font.PLAIN : Font.BOLD, 12);
    }

    // ------------------------------------------------------------------ painéis

    /** Sombra suave, deslocada para baixo, por baixo de um painel arredondado. */
    static void shadow(Graphics2D g, Rectangle r, int arc) {
        for (int i = 4; i >= 1; i--) {
            g.setColor(new Color(0, 0, 0, 18));
            g.fillRoundRect(r.x - i + 1, r.y - i + 3, r.width + 2 * i - 2, r.height + 2 * i - 2, arc + i * 2, arc + i * 2);
        }
    }

    /** Painel arredondado com sombra, degradê vertical, borda e um brilho fino no alto. */
    static void panel(Graphics2D g, Rectangle r, int arc, Color top, Color bottom, Color border) {
        shadow(g, r, arc);
        g.setPaint(new GradientPaint(0, r.y, top, 0, r.y + r.height, bottom));
        g.fill(new RoundRectangle2D.Float(r.x, r.y, r.width, r.height, arc, arc));
        g.setColor(new Color(255, 255, 255, 28));
        g.drawLine(r.x + arc / 2, r.y + 1, r.x + r.width - arc / 2, r.y + 1);
        if (border != null) {
            g.setColor(border);
            g.setStroke(new BasicStroke(1.5f));
            g.draw(new RoundRectangle2D.Float(r.x, r.y, r.width, r.height, arc, arc));
            g.setStroke(new BasicStroke(1f));
        }
    }

    /** Painel escuro padrão da mesa. */
    static void panel(Graphics2D g, Rectangle r, Color border) {
        panel(g, r, 14, PANEL_TOP, PANEL_BOTTOM, border);
    }

    /** Etiqueta arredondada com texto centralizado; devolve a largura. */
    static int pill(Graphics2D g, String text, int x, int y, int h, Font font, Color fill, Color color) {
        g.setFont(font);
        FontMetrics fm = g.getFontMetrics();
        int w = fm.stringWidth(text) + h;
        g.setColor(fill);
        g.fillRoundRect(x, y, w, h, h, h);
        g.setColor(color);
        g.drawString(text, x + h / 2, y + (h + fm.getAscent() - fm.getDescent()) / 2);
        return w;
    }

    // ------------------------------------------------------------------ teclas

    static int keycapWidth(Graphics2D g, String key) {
        return g.getFontMetrics(bold(11.5f)).stringWidth(key) + 12;
    }

    /** Tecla desenhada (com a borda de baixo mais grossa, como uma tecla de verdade); devolve a largura. */
    static int keycap(Graphics2D g, String key, int x, int baseline) {
        int w = keycapWidth(g, key);
        int y = baseline - 14;
        int h = 19;
        g.setColor(new Color(0x8D7740));
        g.fillRoundRect(x, y, w, h, 6, 6);
        g.setPaint(new GradientPaint(0, y, new Color(0xFFF6DC), 0, y + h - 3, new Color(0xEBD9A6)));
        g.fillRoundRect(x, y, w, h - 3, 6, 6);
        g.setFont(bold(11.5f));
        FontMetrics fm = g.getFontMetrics();
        g.setColor(new Color(0x3A2E12));
        g.drawString(key, x + (w - fm.stringWidth(key)) / 2, y + (h - 3 + fm.getAscent() - fm.getDescent()) / 2);
        return w;
    }

    // ------------------------------------------------------------------ ícones

    /** Ícones pequenos desenhados com formas (as fontes do jogo não têm esses símbolos). */
    enum Icon {
        /** Moeda: valor dos bens. */
        COIN {
            @Override
            void draw(Graphics2D g, int x, int y, int s) {
                g.setPaint(new GradientPaint(0, y, new Color(0xFFE082), 0, y + s, new Color(0xE0A800)));
                g.fillOval(x, y, s, s);
                g.setColor(new Color(0x9A6B00));
                g.drawOval(x, y, s, s);
                int in = Math.max(2, s / 4);
                g.drawOval(x + in, y + in, s - 2 * in, s - 2 * in);
            }
        },
        /** Escudo: pontos (como o escudo do canto das cartas). */
        SHIELD {
            @Override
            void draw(Graphics2D g, int x, int y, int s) {
                Path2D p = new Path2D.Float();
                p.moveTo(x + s * 0.08, y);
                p.lineTo(x + s * 0.92, y);
                p.lineTo(x + s * 0.92, y + s * 0.5);
                p.quadTo(x + s * 0.92, y + s * 0.85, x + s * 0.5, y + s);
                p.quadTo(x + s * 0.08, y + s * 0.85, x + s * 0.08, y + s * 0.5);
                p.closePath();
                g.setPaint(new GradientPaint(0, y, new Color(0xFFE57F), 0, y + s, new Color(0xF2B705)));
                g.fill(p);
                g.setColor(new Color(0x8C6A00));
                g.draw(p);
            }
        },
        /** Casa: estabelecimentos. */
        HOUSE {
            @Override
            void draw(Graphics2D g, int x, int y, int s) {
                Path2D p = new Path2D.Float();
                p.moveTo(x + s * 0.5, y);
                p.lineTo(x + s, y + s * 0.45);
                p.lineTo(x + s * 0.85, y + s * 0.45);
                p.lineTo(x + s * 0.85, y + s);
                p.lineTo(x + s * 0.15, y + s);
                p.lineTo(x + s * 0.15, y + s * 0.45);
                p.lineTo(x, y + s * 0.45);
                p.closePath();
                g.setColor(new Color(0xE8C9A0));
                g.fill(p);
                g.setColor(new Color(0x6D4C2E));
                g.draw(p);
                g.fillRect(x + (int) (s * 0.4), y + (int) (s * 0.62), Math.max(2, (int) (s * 0.22)), (int) (s * 0.38));
            }
        },
        /** Duas cartas: cartas na mão. */
        CARDS {
            @Override
            void draw(Graphics2D g, int x, int y, int s) {
                int w = (int) (s * 0.62);
                int h = s;
                g.setColor(new Color(0xB0BEC5));
                g.fillRoundRect(x, y + 1, w, h - 1, 3, 3);
                g.setColor(new Color(0x455A64));
                g.drawRoundRect(x, y + 1, w, h - 1, 3, 3);
                g.setColor(Color.WHITE);
                g.fillRoundRect(x + s - w, y, w, h - 1, 3, 3);
                g.setColor(new Color(0x455A64));
                g.drawRoundRect(x + s - w, y, w, h - 1, 3, 3);
            }
        },
        /** Pessoa: assistentes. */
        PERSON {
            @Override
            void draw(Graphics2D g, int x, int y, int s) {
                g.setColor(new Color(0xCE93D8));
                int head = (int) (s * 0.42);
                g.fillOval(x + (s - head) / 2, y, head, head);
                g.fillArc(x + (int) (s * 0.1), y + (int) (s * 0.48), (int) (s * 0.8), s, 0, 180);
            }
        },
        /** Lâmpada: dicas. */
        BULB {
            @Override
            void draw(Graphics2D g, int x, int y, int s) {
                int d = (int) (s * 0.72);
                g.setColor(new Color(0xFFE082));
                g.fillOval(x + (s - d) / 2, y, d, d);
                g.setColor(new Color(0xB0BEC5));
                g.fillRoundRect(x + s / 3, y + d - 2, s / 3 + 1, s - d + 2, 3, 3);
            }
        },
        /** Exclamação num círculo: aviso. */
        ALERT {
            @Override
            void draw(Graphics2D g, int x, int y, int s) {
                g.setColor(WARNING);
                g.fillOval(x, y, s, s);
                g.setColor(INK);
                int w = Math.max(2, s / 6);
                g.fillRoundRect(x + (s - w) / 2, y + s / 5, w, s / 2, w, w);
                g.fillOval(x + (s - w) / 2, y + s * 3 / 4 - 1, w, w);
            }
        },
        /** Triângulo de "fala": narração do oponente. */
        PLAY {
            @Override
            void draw(Graphics2D g, int x, int y, int s) {
                g.setColor(NARRATION);
                g.fillPolygon(new int[]{x + s / 6, x + s, x + s / 6}, new int[]{y, y + s / 2, y + s}, 3);
            }
        };

        abstract void draw(Graphics2D g, int x, int y, int size);
    }
}
