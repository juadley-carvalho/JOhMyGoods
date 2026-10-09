package org.cardGames;

import javax.swing.AbstractAction;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.Timer;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * A mesa inteira desenhada num único painel. Como todas as áreas (mão, mercado,
 * descarte...) dividem a mesma superfície de desenho, uma carta pode viajar
 * de uma área para outra sem ficar presa aos limites de um componente.
 *
 * O painel só desenha e anima: quem decide o que acontece no jogo é o modelo/controlador.
 */
public class TablePanel extends JPanel {

    private static final int TICK_MS = 16; // ~60 quadros/s

    private final Map<Zone, List<CardSprite>> zones = new EnumMap<>(Zone.class);
    private final Map<Card, CardSprite> spriteByCard = new IdentityHashMap<>();
    private final Timer animationTimer = new Timer(TICK_MS, e -> tick());
    private String status = "";

    public TablePanel() {
        setBackground(new Color(0x2E5E3E));
        for (Zone zone : Zone.values()) {
            zones.put(zone, new ArrayList<>());
        }

        // Ao redimensionar, reposiciona tudo sem animar (a carta não "fica para trás" da janela)
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                layoutAll(false);
            }
        });

        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                onPress(e.getPoint());
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                updateCursor(e.getPoint());
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
    }

    // ------------------------------------------------------------ API pública

    /** Coloca uma carta numa área, já na posição final (sem animação). Usado na preparação da mesa. */
    public void addCard(Card card, Zone zone) {
        CardSprite sprite = new CardSprite(card, zone);
        spriteByCard.put(card, sprite);
        zones.get(zone).add(sprite);
        layoutAll(false);
    }

    /** Move uma carta para outra área com animação, começando depois de delayMs. */
    public void moveCard(Card card, Zone target, int delayMs) {
        CardSprite sprite = spriteByCard.get(card);
        zones.get(sprite.getZone()).remove(sprite);
        zones.get(target).add(sprite);
        sprite.getCard().setSelected(false); // seleção só faz sentido dentro da mão
        sprite.setZone(target, delayMs);
        layoutAll(true);
    }

    /** True enquanto alguma carta está esperando ou se movendo. */
    public boolean isBusy() {
        return animationTimer.isRunning();
    }

    public int count(Zone zone) {
        return zones.get(zone).size();
    }

    public void setStatus(String status) {
        this.status = status;
        repaint();
    }

    /** Liga uma tecla a uma ação (funciona independente de qual componente tem o foco). */
    public void onKey(int keyCode, Runnable action) {
        String name = "key-" + keyCode;
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(keyCode, 0), name);
        getActionMap().put(name, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                action.run();
            }
        });
    }

    // ------------------------------------------------------------- internos

    /** Pede a cada área que recalcule as posições. Com animate=false, as cartas pulam direto ao destino. */
    private void layoutAll(boolean animate) {
        for (Zone zone : Zone.values()) {
            List<CardSprite> cards = zones.get(zone);
            zone.layout(cards, getWidth(), getHeight());
            if (!animate) {
                cards.forEach(CardSprite::snapToTarget);
            }
        }
        if (animate) {
            startAnimation();
        }
        repaint();
    }

    private void startAnimation() {
        if (!animationTimer.isRunning()) {
            animationTimer.start();
        }
    }

    /** Um quadro de animação. O timer se desliga sozinho quando nada mais se move. */
    private void tick() {
        boolean moving = false;
        for (List<CardSprite> cards : zones.values()) {
            for (CardSprite sprite : cards) {
                moving |= sprite.update(TICK_MS);
            }
        }
        repaint();
        if (!moving) {
            animationTimer.stop();
        }
    }

    /**
     * Ordem de desenho, de trás para frente: primeiro as cartas paradas em suas áreas
     * (só o topo das pilhas), depois as que estão viajando entre áreas, para nunca
     * passarem por baixo das outras.
     */
    private List<CardSprite> paintOrder() {
        List<CardSprite> order = new ArrayList<>();
        for (Zone zone : Zone.values()) {
            List<CardSprite> cards = zones.get(zone);
            for (int i = Math.max(0, cards.size() - zone.getVisibleLimit()); i < cards.size(); i++) {
                if (!cards.get(i).isFlying()) order.add(cards.get(i));
            }
        }
        for (Zone zone : Zone.values()) {
            for (CardSprite sprite : zones.get(zone)) {
                if (sprite.isFlying()) order.add(sprite);
            }
        }
        return order;
    }

    /** A carta mais "por cima" sob o ponto, ou null. */
    private CardSprite topCardAt(Point p) {
        List<CardSprite> order = paintOrder();
        for (int i = order.size() - 1; i >= 0; i--) {
            if (order.get(i).getHitBounds().contains(p)) {
                return order.get(i);
            }
        }
        return null;
    }

    private void onPress(Point p) {
        CardSprite sprite = topCardAt(p);
        if (sprite == null || !sprite.getZone().isSelectable()) return;

        sprite.getCard().toggleSelected();
        sprite.refreshTarget();
        startAnimation();
    }

    private void updateCursor(Point p) {
        CardSprite sprite = topCardAt(p);
        boolean clickable = sprite != null && sprite.getZone().isSelectable();
        setCursor(Cursor.getPredefinedCursor(clickable ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
    }

    /** Quantidade de bens, num selo sobre a faixa da pilha que aparece acima do estabelecimento. */
    private void drawGoodsCount(Graphics2D g) {
        List<CardSprite> goods = zones.get(Zone.GOODS);
        if (goods.isEmpty() || goods.getLast().isFlying()) return;
        Rectangle top = goods.getLast().getHitBounds();
        String text = String.valueOf(goods.size());
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setFont(getFont().deriveFont(Font.BOLD, 14f));
        int size = 24;
        int cx = top.x + top.width - size - 6;
        int cy = top.y + 5;
        g.setColor(new Color(0x222222));
        g.fillOval(cx, cy, size, size);
        g.setColor(Color.WHITE);
        g.drawOval(cx, cy, size, size);
        int tw = g.getFontMetrics().stringWidth(text);
        g.drawString(text, cx + (size - tw) / 2, cy + size / 2 + 5);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g); // pinta o fundo
        Graphics2D g2 = (Graphics2D) g.create();
        for (CardSprite sprite : paintOrder()) {
            sprite.draw(g2);
        }
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        drawGoodsCount(g2);
        g2.setColor(Color.WHITE);
        g2.setFont(getFont().deriveFont(Font.BOLD, 14f));
        g2.drawString(status, 20, 20);
        g2.dispose();
    }
}