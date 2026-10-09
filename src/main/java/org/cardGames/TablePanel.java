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
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntPredicate;
import java.util.function.Predicate;

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
    private final Map<Card, Badge> badges = new IdentityHashMap<>();
    private Predicate<Card> clickable = card -> false;
    private Consumer<Card> clickAction = card -> { };
    private String status = "";
    private List<Tile> tiles = List.of();
    private IntPredicate tileClickable = i -> false;
    private IntConsumer tileAction = i -> { };

    /** Ficha desenhada na lateral direita da mesa (ex.: assistente disponível para contratar). */
    public record Tile(String title, String detail, Color color) { }

    private static final int TILE_WIDTH = 150;
    private static final int TILE_HEIGHT = 40;
    private static final int TILE_GAP = 4;
    private static final int TILE_SIDE = 50;
    private static final int TILE_TOP = 30 + CardSprite.HEIGHT + 10; // logo abaixo do descarte

    /** Etiqueta desenhada sobre uma carta (ex.: o trabalhador alocado no estabelecimento). */
    private record Badge(String text, Color color) { }

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
        moveCard(card, target, 0, delayMs);
    }

    /** Como moveCard, indicando o grupo dentro da área (ex.: em qual estabelecimento fica o bem). */
    public void moveCard(Card card, Zone target, int group, int delayMs) {
        CardSprite sprite = spriteByCard.get(card);
        sprite.setGroup(group);
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

    /** Cartas fora da mão que respondem ao clique (ex.: estabelecimentos na Fase II), e o que fazer com elas. */
    public void onCardClick(Predicate<Card> clickable, Consumer<Card> action) {
        this.clickable = clickable;
        this.clickAction = action;
    }

    public void setBadge(Card card, String text, Color color) {
        badges.put(card, new Badge(text, color));
        repaint();
    }

    public void clearBadges() {
        badges.clear();
        repaint();
    }

    /** Fichas da lateral (assistentes), e quais respondem ao clique e o que fazer (recebe o índice). */
    public void setTiles(List<Tile> tiles) {
        this.tiles = List.copyOf(tiles);
        repaint();
    }

    public void onTileClick(IntPredicate clickable, IntConsumer action) {
        this.tileClickable = clickable;
        this.tileAction = action;
    }

    private Rectangle tileBounds(int i) {
        return new Rectangle(getWidth() - TILE_SIDE - TILE_WIDTH, TILE_TOP + i * (TILE_HEIGHT + TILE_GAP),
                TILE_WIDTH, TILE_HEIGHT);
    }

    private int tileAt(Point p) {
        for (int i = 0; i < tiles.size(); i++) {
            if (tileBounds(i).contains(p)) return i;
        }
        return -1;
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
            zone.layout(cards, getWidth(), getHeight(), zones.get(Zone.BUILDINGS).size());
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
            for (int i = 0; i < cards.size(); i++) {
                if (zone.isVisible(cards, i) && !cards.get(i).isFlying()) order.add(cards.get(i));
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
        if (isBusy()) return;
        int tile = tileAt(p);
        if (tile >= 0) {
            if (tileClickable.test(tile)) tileAction.accept(tile);
            return;
        }
        CardSprite sprite = topCardAt(p);
        if (sprite == null) return;

        if (sprite.getZone().isSelectable()) {
            sprite.getCard().toggleSelected();
            sprite.refreshTarget();
            startAnimation();
        } else if (clickable.test(sprite.getCard())) {
            clickAction.accept(sprite.getCard());
        }
    }

    private void updateCursor(Point p) {
        CardSprite sprite = topCardAt(p);
        int tile = tileAt(p);
        boolean canClick = tile >= 0 ? tileClickable.test(tile)
                : sprite != null && (sprite.getZone().isSelectable() || clickable.test(sprite.getCard()));
        setCursor(Cursor.getPredefinedCursor(canClick ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
    }

    /** Quantidade de bens, num selo sobre a faixa de cada pilha que aparece acima do estabelecimento. */
    private void drawGoodsCount(Graphics2D g) {
        Map<Integer, List<CardSprite>> piles = new TreeMap<>();
        for (CardSprite sprite : zones.get(Zone.GOODS)) {
            piles.computeIfAbsent(sprite.getGroup(), k -> new ArrayList<>()).add(sprite);
        }
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setFont(getFont().deriveFont(Font.BOLD, 14f));
        for (List<CardSprite> goods : piles.values()) {
            if (goods.getLast().isFlying()) continue;
            Rectangle top = goods.getLast().getHitBounds();
            String text = String.valueOf(goods.size());
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
    }

    /** Etiquetas numa faixa sobre a parte de baixo da carta. */
    private void drawBadges(Graphics2D g) {
        g.setFont(getFont().deriveFont(Font.BOLD, 13f));
        badges.forEach((card, badge) -> {
            CardSprite sprite = spriteByCard.get(card);
            if (sprite == null || sprite.isFlying()) return;
            Rectangle r = sprite.getHitBounds();
            int h = 24;
            int y = r.y + r.height - h - 8;
            g.setColor(badge.color());
            g.fillRoundRect(r.x + 6, y, r.width - 12, h, 10, 10);
            g.setColor(Color.WHITE);
            g.drawRoundRect(r.x + 6, y, r.width - 12, h, 10, 10);
            int tw = g.getFontMetrics().stringWidth(badge.text());
            g.drawString(badge.text(), r.x + (r.width - tw) / 2, y + 17);
        });
    }

    /** Fichas da lateral: título em negrito e detalhe embaixo. */
    private void drawTiles(Graphics2D g) {
        for (int i = 0; i < tiles.size(); i++) {
            Tile tile = tiles.get(i);
            Rectangle r = tileBounds(i);
            g.setColor(tile.color());
            g.fillRoundRect(r.x, r.y, r.width, r.height, 10, 10);
            g.setColor(Color.WHITE);
            g.drawRoundRect(r.x, r.y, r.width, r.height, 10, 10);
            g.setFont(getFont().deriveFont(Font.BOLD, 12f));
            g.drawString(tile.title(), r.x + 8, r.y + 16);
            g.setFont(getFont().deriveFont(Font.PLAIN, 11f));
            g.drawString(tile.detail(), r.x + 8, r.y + 32);
        }
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
        drawBadges(g2);
        drawTiles(g2);
        g2.setColor(Color.WHITE);
        g2.setFont(getFont().deriveFont(Font.BOLD, 14f));
        g2.drawString(status, 20, 20);
        g2.dispose();
    }
}