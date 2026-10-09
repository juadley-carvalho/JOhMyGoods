package org.cardGames;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URL;

/**
 * Representação visual de uma Card: onde ela está na tela agora, para onde vai
 * e a imagem já redimensionada. A Card continua sendo só o modelo do jogo.
 */
public class CardSprite {

    public static final int WIDTH = 118;
    public static final int HEIGHT = 184;
    /** Quantos pixels a carta sobe quando selecionada. */
    public static final int LIFT = 30;
    /** Fração do caminho percorrida por quadro (0..1): maior = animação mais rápida. */
    private static final double EASING = 0.25;

    private static BufferedImage back; // verso compartilhado por todas as cartas

    private final Card card;
    private final BufferedImage image;   // redimensionada uma única vez
    private Zone zone;
    private int group;                   // subdivisão da área (ex.: índice do estabelecimento dono do bem)

    private double x, y;                 // posição atual (animada)
    private double targetX, targetY;     // para onde a carta está indo
    private int slotX, slotY;            // posição de repouso definida pelo layout da área
    private boolean flying;              // true enquanto muda de área (desenha por cima)
    private int delayMs;                 // espera antes de começar a se mover (para distribuir uma carta de cada vez)
    private boolean faceDown;            // o que está sendo desenhado agora
    private boolean pendingFaceDown;     // o que vai valer quando a carta começar a se mover

    public CardSprite(Card card, Zone zone) {
        this.card = card;
        this.zone = zone;
        this.image = scale(card.getImage(), WIDTH, HEIGHT);
        this.faceDown = this.pendingFaceDown = zone.isFaceDown();
    }

    /** Chamado pelo layout da área. Define a posição de repouso e recalcula o destino. */
    public void setSlot(int slotX, int slotY) {
        this.slotX = slotX;
        this.slotY = slotY;
        refreshTarget();
    }

    /** Posição horizontal de repouso (sem contar a animação em andamento). */
    public int getSlotX() { return slotX; }

    /** Recalcula o destino: no slot, ou 30px acima dele se a carta estiver selecionada. */
    public void refreshTarget() {
        targetX = slotX;
        targetY = slotY - (card.isSelected() ? LIFT : 0);
    }

    public void snapToTarget() {
        x = targetX;
        y = targetY;
        flying = false;
        delayMs = 0;
        faceDown = pendingFaceDown;
    }

    /** Avança a animação em dtMs milissegundos. Retorna true se a carta ainda está esperando ou se movendo. */
    public boolean update(int dtMs) {
        if (delayMs > 0) {
            delayMs -= dtMs;
            if (delayMs > 0) return true;
            faceDown = pendingFaceDown; // vira a carta só quando ela sai do lugar
        }

        double dx = targetX - x;
        double dy = targetY - y;
        if (Math.abs(dx) < 0.5 && Math.abs(dy) < 0.5) {
            x = targetX;
            y = targetY;
            flying = false;
            return false;
        }
        x += dx * EASING;
        y += dy * EASING;
        return true;
    }

    public void draw(Graphics2D g) {
        g.drawImage(faceDown ? cardBack() : image, (int) Math.round(x), (int) Math.round(y), null);
    }

    /**
     * Área sensível ao clique: a posição atual UNIDA à posição de repouso.
     * Sem isso, ao subir a carta a faixa inferior dela deixaria de responder ao mouse
     * e o jogador não conseguiria clicar ali para desmarcá-la.
     */
    public Rectangle getHitBounds() {
        Rectangle current = new Rectangle((int) Math.round(x), (int) Math.round(y), WIDTH, HEIGHT);
        return current.union(new Rectangle(slotX, slotY, WIDTH, HEIGHT));
    }

    public Card getCard() { return card; }
    public Zone getZone() { return zone; }
    public int getGroup() { return group; }
    public void setGroup(int group) { this.group = group; }
    public boolean isFlying() { return flying; }

    /** Troca de área. A carta espera delayMs antes de partir e é desenhada por cima das outras durante a viagem. */
    public void setZone(Zone zone, int delayMs) {
        this.zone = zone;
        this.flying = true;
        this.delayMs = delayMs;
        this.pendingFaceDown = zone.isFaceDown();
        if (delayMs <= 0) {
            this.faceDown = pendingFaceDown;
        }
    }

    // ---------------------------------------------------------------- imagens

    /** Verso da carta: usa /images/cards/verso.png se existir; senão desenha um verso provisório. */
    private static BufferedImage cardBack() {
        if (back == null) {
            BufferedImage loaded = null;
            URL url = CardSprite.class.getResource("/images/cards/verso.png");
            if (url != null) {
                try {
                    loaded = scale(ImageIO.read(url), WIDTH, HEIGHT);
                } catch (IOException ignored) {
                    // cai no verso provisório
                }
            }
            back = (loaded != null) ? loaded : placeholderBack();
        }
        return back;
    }

    private static BufferedImage placeholderBack() {
        BufferedImage img = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(0x8B5A2B));
        g.fillRoundRect(0, 0, WIDTH, HEIGHT, 14, 14);
        g.setColor(new Color(0x5C3A1A));
        for (int py = 30; py < HEIGHT - 10; py += 26) {
            g.drawLine(12, py, WIDTH - 12, py); // "tábuas" da caixa de madeira
        }
        g.setStroke(new BasicStroke(3));
        g.drawRoundRect(1, 1, WIDTH - 3, HEIGHT - 3, 14, 14);
        g.dispose();
        return img;
    }

    /** Reduz em passos de 1/2 e termina com bilinear: evita serrilhado em reduções grandes. */
    private static BufferedImage scale(BufferedImage src, int w, int h) {
        BufferedImage current = src;
        int cw = src.getWidth();
        int ch = src.getHeight();
        while (cw / 2 >= w && ch / 2 >= h) {
            cw /= 2;
            ch /= 2;
            current = drawScaled(current, cw, ch);
        }
        return drawScaled(current, w, h);
    }

    private static BufferedImage drawScaled(Image src, int w, int h) {
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();
        return out;
    }
}