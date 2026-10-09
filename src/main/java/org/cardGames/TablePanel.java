package org.cardGames;

import javax.swing.AbstractAction;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.FontMetrics;
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
import java.util.Iterator;
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
    private static final int NARRATION_HOLD_MS = 900; // quanto a última narração fica na tela

    private final Map<Zone, List<CardSprite>> zones = new EnumMap<>(Zone.class);
    private final Map<Card, CardSprite> spriteByCard = new IdentityHashMap<>();
    private final Timer animationTimer = new Timer(TICK_MS, e -> tick());
    private final Map<Card, List<Badge>> badges = new IdentityHashMap<>();
    private Predicate<Card> clickable = card -> false;
    private Consumer<Card> clickAction = card -> { };
    private Consumer<Card> rightClickAction = card -> { };
    private String statusTitle = "";
    private String statusHint = "";
    private boolean statusWarning;
    private String counter = "";
    private Runnable selectionListener = () -> { };
    private List<Tile> tiles = List.of();
    private IntPredicate tileClickable = i -> false;
    private IntConsumer tileAction = i -> { };
    private Point mouse; // para o detalhe ao passar o mouse

    private final List<Narration> narrations = new ArrayList<>();
    private String narration;  // o que o oponente acabou de fazer (no lugar da dica)
    private String narrationTitle; // etapa mostrada na 1ª linha enquanto há narração
    private int narrationHoldMs;
    private List<OpponentView> pendingOpponents; // resumo novo, mostrado só quando a narração acabar

    private ResultView result; // tela de resultado sobre a mesa, ou null
    private Runnable onPlayAgain = () -> { };
    private Runnable onExit = () -> { };

    private List<OpponentView> opponents = List.of();
    private List<String> tips = List.of(); // quadro de dicas da etapa, abaixo dos assistentes

    private static final int TIPS_GAP = 10;
    private static final int TIPS_MIN_HEIGHT = 50; // com menos espaço que isso, o quadro não aparece

    /**
     * Resumo de um oponente na coluna da esquerda: nome (com destaque se é o inicial), poucas linhas
     * na caixa e o detalhe (estabelecimentos, bens) mostrado ao passar o mouse.
     */
    public record OpponentView(String name, boolean starting, List<String> lines, List<String> detail) { }

    private static final int OPPONENT_X = 10;
    private static final int OPPONENT_WIDTH = 190;
    private static final int OPPONENT_GAP = 6;
    private static final int LINE_HEIGHT = 13;

    /** Ficha da lateral direita (assistente disponível): título, info à direita, cores exigidas e detalhe (ao passar o mouse). */
    public record Tile(String title, String info, String detail, List<Color> chips, Color color) { }

    private static final int TILE_COLUMNS = 2;
    private static final int TILE_WIDTH = 150;
    private static final int TILE_HEIGHT = 36;
    private static final int TILE_PAD = 8;
    private static final int TILE_GAP = 6;
    private static final int TILE_SIDE = 50;

    /** Tela de resultado: cabeçalho e linhas da tabela, frase do vencedor e uma nota de rodapé. */
    public record ResultView(List<String> header, List<List<String>> rows, String winner, String note) { }

    private static final int[] RESULT_COLUMNS = {40, 150, 80, 70, 140, 60, 60};
    private static final int RESULT_ROW = 28;

    /** Etiqueta desenhada sobre uma carta (ex.: o trabalhador alocado no estabelecimento). */
    private record Badge(String text, Color color) { }

    /**
     * Texto que aparece na barra de status depois de delayMs (passo de um oponente), com a etapa na 1ª linha;
     * opponents (se não for null) é o resumo dos oponentes logo depois desse passo.
     */
    private static final class Narration {
        final String title;
        final String text;
        final List<OpponentView> opponents;
        int delayMs;

        Narration(String title, String text, List<OpponentView> opponents, int delayMs) {
            this.title = title;
            this.text = text;
            this.opponents = opponents;
            this.delayMs = delayMs;
        }
    }

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

        MouseAdapter mouseAdapter = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                onPress(e.getPoint(), SwingUtilities.isRightMouseButton(e));
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                mouse = e.getPoint();
                updateCursor(e.getPoint());
                repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                mouse = null;
                repaint();
            }
        };
        addMouseListener(mouseAdapter);
        addMouseMotionListener(mouseAdapter);
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

    /** Desmarca as cartas selecionadas da mão (elas descem para o lugar). */
    public void clearSelection() {
        for (CardSprite sprite : zones.get(Zone.HAND)) {
            sprite.getCard().setSelected(false);
            sprite.refreshTarget();
        }
        startAnimation();
    }

    /** True enquanto alguma carta está esperando ou se movendo (ou uma narração está na tela). */
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

    /** Clique com o botão direito numa carta clicável (ex.: tirar um bem do pagamento). */
    public void onCardRightClick(Consumer<Card> action) {
        this.rightClickAction = action;
    }

    /** Acrescenta uma etiqueta à carta; com mais de uma, elas se empilham de baixo para cima. */
    public void setBadge(Card card, String text, Color color) {
        badges.computeIfAbsent(card, c -> new ArrayList<>()).add(new Badge(text, color));
        repaint();
    }

    public void clearBadges() {
        badges.clear();
        repaint();
    }

    /** Fichas da lateral (assistentes). */
    public void setTiles(List<Tile> tiles) {
        this.tiles = List.copyOf(tiles);
        repaint();
    }

    /** Dicas da etapa atual: opções do jogador e lembretes de regra, uma por item. */
    public void setTips(List<String> tips) {
        this.tips = List.copyOf(tips);
        repaint();
    }

    /** Resumo dos oponentes; durante a narração, o novo resumo espera ela acabar (para não adiantar o resultado). */
    public void setOpponents(List<OpponentView> opponents) {
        if (isNarrating()) {
            pendingOpponents = List.copyOf(opponents);
        } else {
            this.opponents = List.copyOf(opponents);
        }
        repaint();
    }

    private boolean isNarrating() {
        return !narrations.isEmpty() || narrationHoldMs > 0;
    }

    /** A narração acabou: mostra o resumo dos oponentes que estava esperando. */
    private void endNarration() {
        narrations.clear();
        narration = null;
        narrationTitle = null;
        narrationHoldMs = 0;
        if (pendingOpponents != null) {
            opponents = pendingOpponents;
            pendingOpponents = null;
        }
    }

    /** Quais fichas respondem ao clique e o que fazer (recebe o índice). */
    public void onTileClick(IntPredicate clickable, IntConsumer action) {
        this.tileClickable = clickable;
        this.tileAction = action;
    }

    /** Barra de status: a 1ª linha diz a etapa; a 2ª, o que fazer (em destaque se for um aviso). */
    public void setStatus(String title, String hint, boolean warning) {
        this.statusTitle = title;
        this.statusHint = hint;
        this.statusWarning = warning;
        repaint();
    }

    /** Contador do jogador (moedas, pontos, cartas), alinhado à direita na 1ª linha da barra de status. */
    public void setCounter(String counter) {
        this.counter = counter;
        repaint();
    }

    /**
     * Mostra o texto na barra de status depois de delayMs (no lugar da dica, com title na 1ª linha),
     * enquanto as cartas se movem: é como os passos dos oponentes aparecem um de cada vez.
     * A mesa fica ocupada até a última narração ter ficado um tempo na tela.
     */
    public void narrate(String title, String text, int delayMs) {
        narrate(title, text, null, delayMs);
    }

    /** Como narrate, trocando também as caixas dos oponentes pelo resumo de logo depois do passo. */
    public void narrate(String title, String text, List<OpponentView> opponents, int delayMs) {
        narrations.add(new Narration(title, text, opponents == null ? null : List.copyOf(opponents), delayMs));
        startAnimation();
    }

    /** Mostra a tela de resultado sobre a mesa, com os botões "Jogar de novo" e "Sair". */
    public void showResult(ResultView view, Runnable playAgain, Runnable exit) {
        this.result = view;
        this.onPlayAgain = playAgain;
        this.onExit = exit;
        repaint();
    }

    /** Chamado quando o jogador seleciona ou desmarca uma carta da mão. */
    public void onSelectionChange(Runnable listener) {
        this.selectionListener = listener;
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

    // ------------------------------------------------- capturas de tela (testes)

    /** Termina na hora todas as animações (as cartas pulam para o destino). */
    void finishAnimations() {
        layoutAll(false);
        endNarration();
        animationTimer.stop();
    }

    /** Simula um clique perto da parte de baixo da carta. */
    void press(Card card) {
        Rectangle r = spriteByCard.get(card).getHitBounds();
        onPress(new Point(r.x + 20, r.y + r.height - 20), false);
    }

    /** Simula um clique com o botão direito perto da parte de baixo da carta. */
    void pressRight(Card card) {
        Rectangle r = spriteByCard.get(card).getHitBounds();
        onPress(new Point(r.x + 20, r.y + r.height - 20), true);
    }

    /** Simula um clique no ponto (x, y). */
    void pressAt(int x, int y) {
        onPress(new Point(x, y), false);
    }

    /** Avança a animação ms milissegundos de uma vez (para capturar a tela no meio dela). */
    void advanceTime(int ms) {
        for (int t = 0; t < ms && animationTimer.isRunning(); t += TICK_MS) tick();
    }

    /** A 2ª linha da barra de status (para as capturas). */
    String statusHint() {
        return statusHint;
    }

    /** Simula o mouse parado no ponto (x, y), para mostrar o detalhe. */
    void hoverAt(int x, int y) {
        mouse = new Point(x, y);
    }

    /** Área da carta (para as capturas). */
    Rectangle cardArea(Card card) {
        return spriteByCard.get(card).getHitBounds();
    }

    /** Área da i-ésima ficha de assistente (para as capturas). */
    Rectangle tileArea(int i) {
        return tileBounds(i);
    }

    /** Área da caixa do i-ésimo oponente (para as capturas). */
    Rectangle opponentArea(int i) {
        return opponentBounds().get(i);
    }

    /** Área do botão "Jogar de novo" (para as capturas). */
    Rectangle playAgainArea() {
        return resultButtons()[0];
    }

    // ------------------------------------------------------------- internos

    /** Pede a cada área que recalcule as posições. Com animate=false, as cartas pulam direto ao destino. */
    private void layoutAll(boolean animate) {
        int buildings = zones.get(Zone.BUILDINGS).size();
        int hand = zones.get(Zone.HAND).size();
        for (Zone zone : Zone.values()) {
            List<CardSprite> cards = zones.get(zone);
            zone.layout(cards, getWidth(), getHeight(), buildings, hand);
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

    /** Um quadro de animação. O timer se desliga sozinho quando nada mais se move nem há narração pendente. */
    private void tick() {
        boolean moving = false;
        for (List<CardSprite> cards : zones.values()) {
            for (CardSprite sprite : cards) {
                moving |= sprite.update(TICK_MS);
            }
        }
        for (Iterator<Narration> it = narrations.iterator(); it.hasNext(); ) {
            Narration n = it.next();
            n.delayMs -= TICK_MS;
            if (n.delayMs <= 0) {
                narration = n.text;
                narrationTitle = n.title;
                narrationHoldMs = NARRATION_HOLD_MS;
                if (n.opponents != null) opponents = n.opponents;
                it.remove();
            }
        }
        if (narrationHoldMs > 0) narrationHoldMs -= TICK_MS;
        moving |= isNarrating();
        repaint();
        if (!moving) {
            endNarration();
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

    private void onPress(Point p, boolean right) {
        if (result != null) {
            Rectangle[] buttons = resultButtons();
            if (buttons[0].contains(p)) onPlayAgain.run();
            else if (buttons[1].contains(p)) onExit.run();
            return;
        }
        if (isBusy()) return;
        int tile = tileAt(p);
        if (tile >= 0) {
            if (!right && tileClickable.test(tile)) tileAction.accept(tile);
            return;
        }
        CardSprite sprite = topCardAt(p);
        if (sprite == null) return;

        if (sprite.getZone().isSelectable()) {
            sprite.getCard().toggleSelected();
            sprite.refreshTarget();
            startAnimation();
            selectionListener.run();
        } else if (clickable.test(sprite.getCard())) {
            (right ? rightClickAction : clickAction).accept(sprite.getCard());
        }
    }

    private void updateCursor(Point p) {
        boolean canClick;
        if (result != null) {
            Rectangle[] buttons = resultButtons();
            canClick = buttons[0].contains(p) || buttons[1].contains(p);
        } else {
            CardSprite sprite = topCardAt(p);
            int tile = tileAt(p);
            canClick = tile >= 0 ? tileClickable.test(tile)
                    : sprite != null && (sprite.getZone().isSelectable() || clickable.test(sprite.getCard()));
        }
        setCursor(Cursor.getPredefinedCursor(canClick ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
    }

    // ------------------------------------------------------------- desenho

    /** Quantidade de bens, num selo no canto esquerdo da faixa de cada pilha (o direito pode ficar coberto). */
    private void drawGoodsCount(Graphics2D g) {
        Map<Integer, List<CardSprite>> piles = new TreeMap<>();
        for (CardSprite sprite : zones.get(Zone.GOODS)) {
            piles.computeIfAbsent(sprite.getGroup(), k -> new ArrayList<>()).add(sprite);
        }
        g.setFont(getFont().deriveFont(Font.BOLD, 14f));
        for (List<CardSprite> goods : piles.values()) {
            if (goods.getLast().isFlying()) continue;
            Rectangle top = goods.getLast().getHitBounds();
            String text = String.valueOf(goods.size());
            int size = 24;
            int cx = top.x + 6;
            int cy = top.y + 5;
            g.setColor(new Color(0x222222));
            g.fillOval(cx, cy, size, size);
            g.setColor(Color.WHITE);
            g.drawOval(cx, cy, size, size);
            int tw = g.getFontMetrics().stringWidth(text);
            g.drawString(text, cx + (size - tw) / 2, cy + size / 2 + 5);
        }
    }

    /**
     * Largura visível de um estabelecimento: quando eles se sobrepõem, o próximo cobre a parte direita
     * (o último só fica inteiro se não houver carta a construir ao lado).
     */
    private int visibleWidth(CardSprite sprite) {
        if (sprite == hoveredBuilding()) return CardSprite.WIDTH;
        List<CardSprite> buildings = zones.get(Zone.BUILDINGS);
        int index = buildings.indexOf(sprite);
        if (index < 0) return CardSprite.WIDTH;
        boolean last = index == buildings.size() - 1 && zones.get(Zone.PLANNED).isEmpty();
        int step = Zone.buildingStep(getWidth(), buildings.size(), zones.get(Zone.HAND).size());
        return last ? CardSprite.WIDTH : Math.min(CardSprite.WIDTH, step);
    }

    /**
     * Estabelecimento sob o mouse: é desenhado inteiro, por cima dos vizinhos que o cobrem quando eles se
     * sobrepõem (os bens dele continuam atrás, aparecendo em cima).
     */
    private CardSprite hoveredBuilding() {
        if (mouse == null || result != null) return null;
        CardSprite sprite = topCardAt(mouse);
        return sprite != null && sprite.getZone() == Zone.BUILDINGS && !sprite.isFlying() ? sprite : null;
    }

    /** Etiquetas de todas as cartas, menos a do estabelecimento sob o mouse (desenhadas depois dele). */
    private void drawBadges(Graphics2D g, CardSprite skip) {
        badges.forEach((card, list) -> {
            CardSprite sprite = spriteByCard.get(card);
            if (sprite != null && sprite != skip) drawBadges(g, sprite, list);
        });
    }

    /** Etiquetas numa faixa sobre a parte de baixo da carta, empilhadas para cima; a fonte diminui se o texto não cabe. */
    private void drawBadges(Graphics2D g, CardSprite sprite, List<Badge> list) {
        if (sprite.isFlying()) return;
        Rectangle r = sprite.getHitBounds();
        int w = visibleWidth(sprite) - 10;
        int h = 24;
        int x = r.x + 5;
        for (int i = 0; i < list.size(); i++) {
            Badge badge = list.get(i);
            int y = r.y + r.height - h - 8 - i * (h + 4);
            float size = 13f;
            g.setFont(getFont().deriveFont(Font.BOLD, size));
            while (size > 9f && g.getFontMetrics().stringWidth(badge.text()) > w - 6) {
                size -= 1f;
                g.setFont(getFont().deriveFont(Font.BOLD, size));
            }
            g.setColor(badge.color());
            g.fillRoundRect(x, y, w, h, 10, 10);
            g.setColor(Color.WHITE);
            g.drawRoundRect(x, y, w, h, 10, 10);
            String text = ellipsize(g.getFontMetrics(), badge.text(), w - 6);
            int tw = g.getFontMetrics().stringWidth(text);
            g.drawString(text, x + (w - tw) / 2, y + 17);
        }
    }

    /** Fichas em duas colunas, logo abaixo do descarte. */
    private Rectangle tileBounds(int i) {
        int left = getWidth() - TILE_SIDE - TILE_COLUMNS * TILE_WIDTH - (TILE_COLUMNS - 1) * TILE_GAP;
        int col = i % TILE_COLUMNS;
        int row = i / TILE_COLUMNS;
        return new Rectangle(left + col * (TILE_WIDTH + TILE_GAP), Zone.BELOW_PILES + row * (TILE_HEIGHT + TILE_GAP),
                TILE_WIDTH, TILE_HEIGHT);
    }

    private int tileAt(Point p) {
        for (int i = 0; i < tiles.size(); i++) {
            if (tileBounds(i).contains(p)) return i;
        }
        return -1;
    }

    /** Fichas da lateral: título em negrito, info alinhada à direita e as cores exigidas em quadradinhos. */
    private void drawTiles(Graphics2D g) {
        for (int i = 0; i < tiles.size(); i++) {
            Tile tile = tiles.get(i);
            Rectangle r = tileBounds(i);
            g.setColor(tile.color());
            g.fillRoundRect(r.x, r.y, r.width, r.height, 10, 10);
            g.setColor(Color.WHITE);
            g.drawRoundRect(r.x, r.y, r.width, r.height, 10, 10);
            g.setFont(getFont().deriveFont(Font.BOLD, 12f));
            g.drawString(tile.title(), r.x + TILE_PAD, r.y + 15);
            g.setFont(getFont().deriveFont(Font.PLAIN, 11f));
            int iw = g.getFontMetrics().stringWidth(tile.info());
            g.drawString(tile.info(), r.x + r.width - TILE_PAD - iw, r.y + 15);
            int cx = r.x + TILE_PAD;
            for (Color chip : tile.chips()) {
                g.setColor(chip);
                g.fillRoundRect(cx, r.y + 21, 14, 9, 4, 4);
                g.setColor(Color.WHITE);
                g.drawRoundRect(cx, r.y + 21, 14, 9, 4, 4);
                cx += 18;
            }
        }
    }

    /**
     * Quadro de dicas: na coluna dos assistentes, logo abaixo da última fileira de fichas. Desce até
     * o pé da mesa se a fileira de baixo (estabelecimentos e mão) termina antes da coluna; senão para
     * acima da mão (contando a carta selecionada, que sobe). Sobe quando os assistentes são contratados.
     */
    private Rectangle tipsBounds() {
        int width = TILE_COLUMNS * TILE_WIDTH + (TILE_COLUMNS - 1) * TILE_GAP;
        int left = getWidth() - TILE_SIDE - width;
        int rows = (tiles.size() + TILE_COLUMNS - 1) / TILE_COLUMNS;
        int top = Zone.BELOW_PILES + rows * (TILE_HEIGHT + TILE_GAP) + (rows > 0 ? TIPS_GAP - TILE_GAP : 0);
        int bottom = bottomRowRight() + TIPS_GAP <= left
                ? Zone.bottomRowY(getHeight()) + CardSprite.HEIGHT
                : Zone.bottomRowY(getHeight()) - CardSprite.LIFT - TIPS_GAP;
        return new Rectangle(left, top, width, bottom - top);
    }

    /** Até onde vai, à direita, a fileira de baixo do jogador (pelas posições de repouso das cartas). */
    private int bottomRowRight() {
        int right = 0;
        for (Zone zone : new Zone[]{Zone.BUILDINGS, Zone.PLANNED, Zone.HAND}) {
            for (CardSprite sprite : zones.get(zone)) {
                right = Math.max(right, sprite.getSlotX() + CardSprite.WIDTH);
            }
        }
        return right;
    }

    /** Dicas em itens, quebradas na largura; o que não cabe na altura é cortado com "...". */
    private void drawTips(Graphics2D g) {
        Rectangle area = tipsBounds();
        if (tips.isEmpty() || area.height < TIPS_MIN_HEIGHT) return;
        g.setFont(opponentFont());
        FontMetrics fm = g.getFontMetrics();
        int bullet = 10;
        List<String> lines = new ArrayList<>();
        List<Boolean> starts = new ArrayList<>(); // a linha começa um item (leva o marcador)
        for (String tip : tips) {
            List<String> wrapped = wrap(fm, tip, area.width - 12 - bullet);
            for (int i = 0; i < wrapped.size(); i++) {
                lines.add(wrapped.get(i));
                starts.add(i == 0);
            }
        }
        int fit = Math.max(0, (area.height - 25) / LINE_HEIGHT);
        int shown = Math.min(fit, lines.size());
        if (shown < lines.size() && shown > 0) {
            lines.set(shown - 1, ellipsize(fm, lines.get(shown - 1) + " ...", area.width - 12 - bullet));
        }
        int h = 20 + shown * LINE_HEIGHT + 5;
        g.setColor(new Color(0, 0, 0, 90));
        g.fillRoundRect(area.x, area.y, area.width, h, 10, 10);
        g.setColor(new Color(0xC8E6C9));
        g.drawRoundRect(area.x, area.y, area.width, h, 10, 10);
        g.setFont(getFont().deriveFont(Font.BOLD, 12f));
        g.drawString("Dicas", area.x + 6, area.y + 15);
        g.setFont(opponentFont());
        String help = "H: regras completas";
        g.drawString(help, area.x + area.width - 6 - fm.stringWidth(help), area.y + 15);
        g.setColor(Color.WHITE);
        for (int i = 0; i < shown; i++) {
            int y = area.y + 20 + (i + 1) * LINE_HEIGHT - 2;
            if (starts.get(i)) g.drawString("•", area.x + 6, y);
            g.drawString(lines.get(i), area.x + 6 + bullet, y);
        }
    }

    private Font opponentFont() {
        return getFont().deriveFont(Font.PLAIN, 11f);
    }

    /** Caixas dos oponentes, uma embaixo da outra, abaixo da pilha de compras. */
    private List<Rectangle> opponentBounds() {
        List<Rectangle> bounds = new ArrayList<>();
        int y = Zone.BELOW_PILES;
        for (OpponentView view : opponents) {
            int h = 20 + view.lines().size() * LINE_HEIGHT + 5;
            bounds.add(new Rectangle(OPPONENT_X, y, OPPONENT_WIDTH, h));
            y += h + OPPONENT_GAP;
        }
        return bounds;
    }

    /** Caixas resumidas dos oponentes: cada linha é cortada na largura (o detalhe aparece ao passar o mouse). */
    private void drawOpponents(Graphics2D g) {
        List<Rectangle> bounds = opponentBounds();
        for (int k = 0; k < opponents.size(); k++) {
            OpponentView view = opponents.get(k);
            Rectangle r = bounds.get(k);
            g.setColor(new Color(0, 0, 0, 90));
            g.fillRoundRect(r.x, r.y, r.width, r.height, 10, 10);
            g.setColor(view.starting() ? new Color(0xFFD54F) : Color.WHITE);
            g.drawRoundRect(r.x, r.y, r.width, r.height, 10, 10);
            g.setFont(getFont().deriveFont(Font.BOLD, 12f));
            g.drawString(view.name() + (view.starting() ? "  (inicial)" : ""), r.x + 6, r.y + 15);
            g.setColor(Color.WHITE);
            g.setFont(opponentFont());
            for (int i = 0; i < view.lines().size(); i++) {
                String line = ellipsize(g.getFontMetrics(), view.lines().get(i), r.width - 12);
                g.drawString(line, r.x + 6, r.y + 20 + (i + 1) * LINE_HEIGHT - 2);
            }
        }
    }

    /** Detalhe do oponente ou da ficha sob o mouse, numa caixa ao lado. */
    private void drawHover(Graphics2D g) {
        if (mouse == null || result != null) return;
        List<Rectangle> bounds = opponentBounds();
        for (int k = 0; k < bounds.size(); k++) {
            Rectangle r = bounds.get(k);
            if (r.contains(mouse)) {
                OpponentView view = opponents.get(k);
                drawPopup(g, view.name(), view.detail(), r.x + r.width + 6, r.y, false);
                return;
            }
        }
        int tile = tileAt(mouse);
        if (tile >= 0 && !tiles.get(tile).detail().isEmpty()) {
            Rectangle r = tileBounds(tile);
            drawPopup(g, tiles.get(tile).title(), List.of(tiles.get(tile).detail()), r.x - 6, r.y, true);
        }
    }

    /** Caixa de detalhe com título e linhas (quebradas em até 340 px); alignRight: x é a borda direita. */
    private void drawPopup(Graphics2D g, String title, List<String> text, int x, int y, boolean alignRight) {
        g.setFont(opponentFont());
        FontMetrics fm = g.getFontMetrics();
        List<String> lines = new ArrayList<>();
        for (String line : text) lines.addAll(wrap(fm, line, 340));
        int w = 12 + Math.max(fm.stringWidth(title) + 20, lines.stream().mapToInt(fm::stringWidth).max().orElse(0));
        int h = 20 + lines.size() * LINE_HEIGHT + 5;
        int left = alignRight ? x - w : x;
        int top = Math.min(y, getHeight() - h - 4);
        g.setColor(new Color(20, 20, 20, 235));
        g.fillRoundRect(left, top, w, h, 10, 10);
        g.setColor(Color.WHITE);
        g.drawRoundRect(left, top, w, h, 10, 10);
        g.setFont(getFont().deriveFont(Font.BOLD, 12f));
        g.drawString(title, left + 6, top + 15);
        g.setFont(opponentFont());
        for (int i = 0; i < lines.size(); i++) {
            g.drawString(lines.get(i), left + 6, top + 20 + (i + 1) * LINE_HEIGHT - 2);
        }
    }

    /**
     * Barra de status em duas linhas; a 2ª mostra o aviso (amarelo) ou o passo do oponente (azul) quando há.
     * O contador do jogador fica à direita da 1ª linha.
     */
    private void drawStatus(Graphics2D g) {
        int width = getWidth() - 40;
        g.setColor(new Color(0xFFE082));
        g.setFont(getFont().deriveFont(Font.BOLD, 12f));
        int counterWidth = g.getFontMetrics().stringWidth(counter);
        g.drawString(counter, getWidth() - 20 - counterWidth, 18);
        g.setColor(Color.WHITE);
        g.setFont(getFont().deriveFont(Font.BOLD, 14f));
        String title = narration != null ? narrationTitle : statusTitle;
        g.drawString(ellipsize(g.getFontMetrics(), title, width - counterWidth - 20), 20, 18);
        String hint = statusHint;
        if (narration != null) {
            g.setColor(new Color(0x90CAF9));
            g.setFont(getFont().deriveFont(Font.BOLD, 12f));
            hint = "> " + narration;
        } else if (statusWarning) {
            g.setColor(new Color(0xFFD54F));
            g.setFont(getFont().deriveFont(Font.BOLD, 12f));
        } else {
            g.setFont(getFont().deriveFont(Font.PLAIN, 12f));
        }
        g.drawString(ellipsize(g.getFontMetrics(), hint, width), 20, 37);
    }

    private Rectangle resultPanel() {
        int width = 0;
        for (int c : RESULT_COLUMNS) width += c;
        width += 40;
        int height = 60 + RESULT_ROW * (result.rows().size() + 1) + 70 + 60;
        return new Rectangle((getWidth() - width) / 2, (getHeight() - height) / 2, width, height);
    }

    /** Botões "Jogar de novo" e "Sair", no pé da tela de resultado. */
    private Rectangle[] resultButtons() {
        if (result == null) return new Rectangle[]{new Rectangle(), new Rectangle()};
        Rectangle p = resultPanel();
        int y = p.y + p.height - 55;
        int again = 180;
        int exit = 110;
        int left = p.x + (p.width - again - exit - 20) / 2;
        return new Rectangle[]{new Rectangle(left, y, again, 38), new Rectangle(left + again + 20, y, exit, 38)};
    }

    /** Tela de resultado: a mesa escurece e a tabela de pontos aparece no meio, com os botões. */
    private void drawResult(Graphics2D g) {
        if (result == null) return;
        g.setColor(new Color(0, 0, 0, 160));
        g.fillRect(0, 0, getWidth(), getHeight());
        Rectangle p = resultPanel();
        g.setColor(new Color(0x1F3A2A));
        g.fillRoundRect(p.x, p.y, p.width, p.height, 16, 16);
        g.setColor(new Color(0xFFD54F));
        g.drawRoundRect(p.x, p.y, p.width, p.height, 16, 16);
        g.setFont(getFont().deriveFont(Font.BOLD, 22f));
        g.drawString("Resultado", p.x + 20, p.y + 36);

        int y = p.y + 60;
        drawResultRow(g, result.header(), p.x + 20, y, true);
        for (List<String> row : result.rows()) {
            y += RESULT_ROW;
            drawResultRow(g, row, p.x + 20, y, false);
        }
        y += RESULT_ROW + 30;
        g.setColor(new Color(0xFFD54F));
        g.setFont(getFont().deriveFont(Font.BOLD, 18f));
        g.drawString(result.winner(), p.x + 20, y);
        g.setColor(Color.WHITE);
        g.setFont(getFont().deriveFont(Font.PLAIN, 12f));
        g.drawString(result.note(), p.x + 20, y + 22);

        Rectangle[] buttons = resultButtons();
        String[] labels = {"Jogar de novo", "Sair"};
        for (int i = 0; i < buttons.length; i++) {
            Rectangle b = buttons[i];
            boolean hover = mouse != null && b.contains(mouse);
            g.setColor(i == 0 ? new Color(hover ? 0x2E7D32 : 0x1B5E20) : new Color(hover ? 0x666666 : 0x444444));
            g.fillRoundRect(b.x, b.y, b.width, b.height, 10, 10);
            g.setColor(Color.WHITE);
            g.drawRoundRect(b.x, b.y, b.width, b.height, 10, 10);
            g.setFont(getFont().deriveFont(Font.BOLD, 14f));
            int tw = g.getFontMetrics().stringWidth(labels[i]);
            g.drawString(labels[i], b.x + (b.width - tw) / 2, b.y + 24);
        }
    }

    /** Uma linha da tabela de resultado; as colunas de número (da 3ª em diante) ficam à direita. */
    private void drawResultRow(Graphics2D g, List<String> cells, int x, int y, boolean header) {
        g.setColor(header ? new Color(0xC8E6C9) : Color.WHITE);
        g.setFont(getFont().deriveFont(header ? Font.BOLD : Font.PLAIN, 13f));
        int cx = x;
        for (int c = 0; c < cells.size() && c < RESULT_COLUMNS.length; c++) {
            String text = ellipsize(g.getFontMetrics(), cells.get(c), RESULT_COLUMNS[c] - 8);
            int tw = g.getFontMetrics().stringWidth(text);
            g.drawString(text, c >= 2 ? cx + RESULT_COLUMNS[c] - 8 - tw : cx, y);
            cx += RESULT_COLUMNS[c];
        }
    }

    /** Corta o texto com "..." para caber em width pixels. */
    private static String ellipsize(FontMetrics fm, String text, int width) {
        if (fm.stringWidth(text) <= width) return text;
        String end = "...";
        int n = text.length();
        while (n > 0 && fm.stringWidth(text.substring(0, n) + end) > width) n--;
        return text.substring(0, n) + end;
    }

    /** Quebra o texto em linhas que caibam em width pixels (separando por espaços). */
    private static List<String> wrap(FontMetrics fm, String text, int width) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (!line.isEmpty() && fm.stringWidth(candidate) > width) {
                lines.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(candidate);
            }
        }
        if (!line.isEmpty()) lines.add(line.toString());
        return lines;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g); // pinta o fundo
        Graphics2D g2 = (Graphics2D) g.create();
        for (CardSprite sprite : paintOrder()) {
            sprite.draw(g2);
        }
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        drawGoodsCount(g2);
        CardSprite hovered = hoveredBuilding();
        drawBadges(g2, hovered);
        if (hovered != null) {
            hovered.draw(g2);
            drawBadges(g2, hovered, badges.getOrDefault(hovered.getCard(), List.of()));
        }
        drawTiles(g2);
        drawTips(g2);
        drawOpponents(g2);
        drawStatus(g2);
        drawHover(g2);
        drawResult(g2);
        g2.dispose();
    }
}
