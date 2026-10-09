package org.cardGames;

import javax.swing.AbstractAction;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.BasicStroke;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    private List<String> statusFlags = List.of();
    private String statusContext;
    private String statusHint = "";
    private boolean statusWarning;
    private CounterView counter;
    private Runnable selectionListener = () -> { };
    private List<Tile> tiles = List.of();
    private IntPredicate tileClickable = i -> false;
    private IntConsumer tileAction = i -> { };
    private Point mouse; // para o detalhe ao passar o mouse

    private final List<Narration> narrations = new ArrayList<>();
    private String narration;  // o que o oponente acabou de fazer (no lugar da dica)
    private String narrationTitle; // etapa mostrada na plaqueta enquanto há narração
    private int narrationHoldMs;
    private List<OpponentView> pendingOpponents; // resumo novo, mostrado só quando a narração acabar

    private ResultView result; // tela de resultado sobre a mesa, ou null
    private Runnable onPlayAgain = () -> { };
    private Runnable onExit = () -> { };

    private List<OpponentView> opponents = List.of();
    private CardSprite held; // estabelecimento destacado: continua destacado enquanto o mouse estiver na área inteira dele
    private List<String> tips = List.of(); // quadro de dicas da etapa, abaixo dos assistentes

    private static final int TIPS_GAP = 10;
    private static final int TIPS_MIN_HEIGHT = 60; // com menos espaço que isso, o quadro não aparece
    private static final int TIPS_PAD = 12;
    private static final int TIPS_HEADER = 42;     // título "Dicas" e o traço abaixo dele
    private static final int TIPS_BULLET = 14;
    private static final int TIPS_ITEM_GAP = 7;
    private static final float TIPS_MAX_FONT = 15f;
    private static final float TIPS_MIN_FONT = 11f;

    /**
     * Resumo de um oponente na coluna da esquerda: nome (com destaque se é o inicial), os números com ícones
     * (pontos, estabelecimentos, moedas em bens, mão, assistentes), se tem carta a construir, o que fez por
     * último (ou null) e o detalhe (estabelecimentos, bens) mostrado ao passar o mouse.
     */
    public record OpponentView(String name, boolean starting, boolean planning, int points, int buildings, int coins,
                               int hand, int assistants, String last, List<String> detail) { }

    private static final int OPPONENT_X = 10;
    private static final int OPPONENT_WIDTH = 214;
    private static final int OPPONENT_GAP = 6;
    private static final int LINE_HEIGHT = 16; // linhas dos detalhes ao passar o mouse
    private static final int OPPONENT_HEAD = 30;  // avatar, nome e marcas
    private static final int OPPONENT_STATS = 22; // números com ícones
    private static final int OPPONENT_LAST = 17;  // o que fez por último
    private static final Color[] AVATARS = {new Color(0xC62828), new Color(0x1565C0), new Color(0xEF6C00)};

    /** Ficha da lateral direita (assistente disponível): título, custo, pontos, cores exigidas e detalhe (ao passar o mouse). */
    public record Tile(String title, int cost, int points, String detail, List<Color> chips, Color color) { }

    /** Painel do jogador no canto superior direito: nome, moedas em bens, pontos e cartas na mão. */
    public record CounterView(String name, int coins, int points, int cards) { }

    private static final int HUD_Y = 12;       // topo da caixa de mensagem e do painel do jogador
    private static final int HUD_HEIGHT = 72;
    private static final int HUD_ROWS = 3;     // linhas de texto da caixa de mensagem
    private static final int HUD_ROW = 18;
    private static final int COUNTER_WIDTH = 320;

    private static final int TILE_COLUMNS = 2;
    private static final int TILE_WIDTH = 150;
    private static final int TILE_HEIGHT = 38;
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
     * Texto que aparece na caixa de mensagem depois de delayMs (passo de um oponente), com a etapa na plaqueta;
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

    /**
     * Caixa de mensagem: a etapa vai na plaqueta (com as marcas, ex.: rodada final), o contexto (ou null) em
     * destaque e o que fazer em itens separados por "   |   " ("TECLA: texto" vira uma tecla desenhada).
     * Com warning, o hint é o aviso, em âmbar.
     */
    public void setStatus(String title, List<String> flags, String context, String hint, boolean warning) {
        this.statusTitle = title;
        this.statusFlags = List.copyOf(flags);
        this.statusContext = context;
        this.statusHint = hint;
        this.statusWarning = warning;
        repaint();
    }

    /** Painel do jogador (moedas, pontos, cartas), no canto superior direito. */
    public void setCounter(CounterView counter) {
        this.counter = counter;
        repaint();
    }

    /**
     * Mostra o texto na caixa de mensagem depois de delayMs (no lugar da dica, com title na plaqueta),
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

    /** O que fazer (ou o aviso) da caixa de mensagem (para as capturas). */
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
     * passarem por baixo das outras. Na mão, as selecionadas vêm por cima das vizinhas, para aparecerem inteiras.
     */
    private List<CardSprite> paintOrder() {
        List<CardSprite> order = new ArrayList<>();
        for (Zone zone : Zone.values()) {
            List<CardSprite> cards = zones.get(zone);
            for (int i = 0; i < cards.size(); i++) {
                if (zone.isVisible(cards, i) && !cards.get(i).isFlying() && !isRaised(cards.get(i))) order.add(cards.get(i));
            }
        }
        for (CardSprite sprite : zones.get(Zone.HAND)) {
            if (isRaised(sprite)) order.add(sprite);
        }
        for (Zone zone : Zone.values()) {
            for (CardSprite sprite : zones.get(zone)) {
                if (sprite.isFlying()) order.add(sprite);
            }
        }
        return order;
    }

    /**
     * A carta mais "por cima" sob o ponto, ou null. O estabelecimento destacado (desenhado inteiro, por cima
     * dos vizinhos) vem primeiro em toda a área dele, para o clique ir para a carta que aparece.
     */
    private CardSprite topCardAt(Point p) {
        if (isHeld(held) && held.getHitBounds().contains(p)) return held;
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
        g.setFont(Hud.heavy(14f));
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
        if (mouse == null || result != null) {
            held = null;
            return null;
        }
        CardSprite sprite = topCardAt(mouse);
        held = isHeld(sprite) ? sprite : null;
        return held;
    }

    /** Carta selecionada parada na mão: desenhada por cima das vizinhas, com contorno dourado. */
    private static boolean isRaised(CardSprite sprite) {
        return sprite.getZone() == Zone.HAND && sprite.getCard().isSelected() && !sprite.isFlying();
    }

    private static void drawRaisedOutline(Graphics2D g, CardSprite sprite) {
        Rectangle r = sprite.getBounds();
        g.setStroke(new BasicStroke(3f));
        g.setColor(Hud.GOLD);
        g.drawRoundRect(r.x - 2, r.y - 2, r.width + 3, r.height + 3, 10, 10);
        g.setStroke(new BasicStroke(1f));
    }

    private static boolean isHeld(CardSprite sprite) {
        return sprite != null && sprite.getZone() == Zone.BUILDINGS && !sprite.isFlying();
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
            g.setFont(Hud.bold(size));
            while (size > 9f && g.getFontMetrics().stringWidth(badge.text()) > w - 6) {
                size -= 1f;
                g.setFont(Hud.bold(size));
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

    /** Fichas da lateral: número, custo (moeda), pontos (escudo) e as cores exigidas; acendem sob o mouse se clicáveis. */
    private void drawTiles(Graphics2D g) {
        for (int i = 0; i < tiles.size(); i++) {
            Tile tile = tiles.get(i);
            Rectangle r = tileBounds(i);
            boolean hover = mouse != null && result == null && r.contains(mouse) && tileClickable.test(i);
            Hud.panel(g, r, 12, blend(tile.color(), Color.WHITE, hover ? 0.35f : 0.15f), tile.color(),
                    new Color(255, 255, 255, hover ? 210 : 70));
            g.setColor(Color.WHITE);
            g.setFont(Hud.heavy(14f));
            g.drawString(tile.title(), r.x + TILE_PAD, r.y + 17);

            g.setFont(Hud.bold(13f));
            FontMetrics fm = g.getFontMetrics();
            String points = String.valueOf(tile.points());
            String cost = String.valueOf(tile.cost());
            int x = r.x + r.width - TILE_PAD - fm.stringWidth(points);
            g.setColor(Color.WHITE);
            g.drawString(points, x, r.y + 17);
            x -= 17;
            Hud.Icon.SHIELD.draw(g, x, r.y + 5, 13);
            x -= 10 + fm.stringWidth(cost);
            g.setColor(Color.WHITE);
            g.drawString(cost, x, r.y + 17);
            x -= 17;
            Hud.Icon.COIN.draw(g, x, r.y + 5, 13);

            int cx = r.x + TILE_PAD;
            for (Color chip : tile.chips()) {
                g.setColor(chip);
                g.fillRoundRect(cx, r.y + 24, 16, 9, 9, 9);
                g.setColor(new Color(255, 255, 255, 170));
                g.drawRoundRect(cx, r.y + 24, 16, 9, 9, 9);
                cx += 20;
            }
        }
    }

    /** Mistura a com b (fração de b entre 0 e 1), mantendo a transparência de a. */
    private static Color blend(Color a, Color b, float f) {
        return new Color(Math.round(a.getRed() + (b.getRed() - a.getRed()) * f),
                Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * f),
                Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * f), a.getAlpha());
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

    /**
     * Dicas em itens, na maior fonte (de 15 a 11) em que todas cabem: o quadro ocupa a coluna até embaixo.
     * Se nem a menor fonte basta, o que não cabe na altura é cortado com "...".
     */
    private void drawTips(Graphics2D g) {
        Rectangle area = tipsBounds();
        if (tips.isEmpty() || area.height < TIPS_MIN_HEIGHT) return;
        int textWidth = area.width - 2 * TIPS_PAD - TIPS_BULLET;
        int room = area.height - TIPS_HEADER - TIPS_PAD;
        float size = TIPS_MAX_FONT;
        List<List<String>> wrapped;
        while (true) {
            FontMetrics fm = g.getFontMetrics(Hud.semibold(size));
            wrapped = tips.stream().map(tip -> wrap(fm, tip, textWidth)).toList();
            int lines = wrapped.stream().mapToInt(List::size).sum();
            if (lines * tipLine(size) + (tips.size() - 1) * TIPS_ITEM_GAP <= room || size <= TIPS_MIN_FONT) break;
            size -= 0.5f;
        }

        Hud.panel(g, area, new Color(255, 255, 255, 50));
        Hud.Icon.BULB.draw(g, area.x + TIPS_PAD, area.y + 9, 17);
        g.setFont(Hud.title(15f));
        g.setColor(Hud.GOLD);
        g.drawString("Dicas", area.x + TIPS_PAD + 24, area.y + 23);
        g.setFont(Hud.regular(12f));
        String rules = "regras completas";
        int rx = area.x + area.width - TIPS_PAD - g.getFontMetrics().stringWidth(rules);
        g.setColor(Hud.SOFT);
        g.drawString(rules, rx, area.y + 22);
        Hud.keycap(g, "H", rx - 6 - Hud.keycapWidth(g, "H"), area.y + 23);
        g.setColor(new Color(255, 255, 255, 30));
        g.drawLine(area.x + TIPS_PAD, area.y + TIPS_HEADER - 8, area.x + area.width - TIPS_PAD, area.y + TIPS_HEADER - 8);

        g.setFont(Hud.semibold(size));
        FontMetrics fm = g.getFontMetrics();
        int line = tipLine(size);
        int bottom = area.y + area.height - TIPS_PAD;
        int y = area.y + TIPS_HEADER + fm.getAscent();
        for (List<String> tip : wrapped) {
            if (y + fm.getDescent() > bottom) break;
            g.setColor(Hud.GOLD);
            g.fillOval(area.x + TIPS_PAD + 1, y - fm.getAscent() / 2 - 3, 6, 6);
            g.setColor(Color.WHITE);
            for (int i = 0; i < tip.size(); i++) {
                String text = tip.get(i);
                boolean lastFitting = y + line + fm.getDescent() > bottom;
                if (lastFitting && (i < tip.size() - 1 || tip != wrapped.getLast())) {
                    g.drawString(ellipsize(fm, text + " ...", textWidth), area.x + TIPS_PAD + TIPS_BULLET, y);
                    return;
                }
                g.drawString(text, area.x + TIPS_PAD + TIPS_BULLET, y);
                y += line;
            }
            y += TIPS_ITEM_GAP;
        }
    }

    private static int tipLine(float size) {
        return Math.round(size * 1.35f);
    }

    /** Caixas dos oponentes, uma embaixo da outra, abaixo da pilha de compras (a linha "última vez" só se houver). */
    private List<Rectangle> opponentBounds() {
        List<Rectangle> bounds = new ArrayList<>();
        int y = Zone.BELOW_PILES;
        for (OpponentView view : opponents) {
            int h = OPPONENT_HEAD + OPPONENT_STATS + (view.last() != null ? OPPONENT_LAST : 0) + 6;
            bounds.add(new Rectangle(OPPONENT_X, y, OPPONENT_WIDTH, h));
            y += h + OPPONENT_GAP;
        }
        return bounds;
    }

    /**
     * Caixas dos oponentes: avatar com a inicial, nome e marcas (inicial, carta a construir), os números com
     * ícones e o que fez por último. O detalhe aparece ao passar o mouse.
     */
    private void drawOpponents(Graphics2D g) {
        List<Rectangle> bounds = opponentBounds();
        for (int k = 0; k < opponents.size(); k++) {
            OpponentView view = opponents.get(k);
            Rectangle r = bounds.get(k);
            boolean hover = mouse != null && result == null && r.contains(mouse);
            Hud.panel(g, r, hover ? Hud.SOFT : new Color(255, 255, 255, 50));
            if (view.starting()) {
                g.setColor(Hud.GOLD);
                g.fillRoundRect(r.x + 1, r.y + 8, 4, r.height - 16, 4, 4);
            }

            int as = 20;
            int ax = r.x + 10;
            int ay = r.y + 5;
            g.setColor(AVATARS[k % AVATARS.length]);
            g.fillOval(ax, ay, as, as);
            g.setColor(new Color(255, 255, 255, 180));
            g.drawOval(ax, ay, as, as);
            g.setFont(Hud.heavy(11.5f));
            FontMetrics fm = g.getFontMetrics();
            String initial = view.name().isEmpty() ? "?" : view.name().substring(0, 1).toUpperCase();
            g.setColor(Color.WHITE);
            g.drawString(initial, ax + (as - fm.stringWidth(initial)) / 2 + 1, ay + 15);

            int right = r.x + r.width - 8;
            Font pillFont = Hud.heavy(10f);
            List<String> marks = new ArrayList<>();
            if (view.planning()) marks.add("+1 OBRA");
            if (view.starting()) marks.add("INICIAL");
            for (String mark : marks) {
                int w = g.getFontMetrics(pillFont).stringWidth(mark) + 16;
                right -= w;
                boolean start = mark.equals("INICIAL");
                Hud.pill(g, mark, right, r.y + 7, 16, pillFont, start ? Hud.GOLD : new Color(0x1565C0),
                        start ? Hud.INK : Color.WHITE);
                right -= 4;
            }
            g.setFont(Hud.bold(14f));
            g.setColor(Color.WHITE);
            int nameX = ax + as + 7;
            g.drawString(ellipsize(g.getFontMetrics(), view.name(), right - nameX - 2), nameX, r.y + 20);

            int sy = r.y + OPPONENT_HEAD;
            int[] values = {view.points(), view.buildings(), view.coins(), view.hand(), view.assistants()};
            Hud.Icon[] icons = {Hud.Icon.SHIELD, Hud.Icon.HOUSE, Hud.Icon.COIN, Hud.Icon.CARDS, Hud.Icon.PERSON};
            g.setColor(new Color(0, 0, 0, 60));
            g.fillRoundRect(r.x + 6, sy - 2, r.width - 12, OPPONENT_STATS - 2, 8, 8);
            g.setFont(Hud.heavy(13.5f));
            FontMetrics nm = g.getFontMetrics();
            int[] widths = new int[values.length];
            for (int i = 0; i < values.length; i++) widths[i] = 17 + nm.stringWidth(String.valueOf(values[i]));
            int x = r.x + 11;
            for (int i = 0; i < values.length; i++) {
                icons[i].draw(g, x, sy + 2, 14);
                g.setColor(Color.WHITE);
                g.drawString(String.valueOf(values[i]), x + 17, sy + 14);
                x += widths[i] + spread(r.width - 22, widths, i);
            }

            if (view.last() != null) {
                int ly = sy + OPPONENT_STATS + 12;
                Hud.Icon.PLAY.draw(g, r.x + 10, ly - 8, 8);
                g.setFont(Hud.semibold(12f));
                g.setColor(Hud.SOFT);
                g.drawString(ellipsize(g.getFontMetrics(), view.last(), r.width - 32), r.x + 24, ly);
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
                drawPopup(g, view.name(), view.detail(), r.x + r.width + 8, r.y, false);
                return;
            }
        }
        int tile = tileAt(mouse);
        if (tile >= 0 && !tiles.get(tile).detail().isEmpty()) {
            Rectangle r = tileBounds(tile);
            drawPopup(g, tiles.get(tile).title(), List.of(tiles.get(tile).detail()), r.x - 8, r.y, true);
        }
    }

    /** Caixa de detalhe com título e linhas (quebradas em até 340 px); alignRight: x é a borda direita. */
    private void drawPopup(Graphics2D g, String title, List<String> text, int x, int y, boolean alignRight) {
        Font body = Hud.regular(12.5f);
        FontMetrics fm = g.getFontMetrics(body);
        FontMetrics titleFm = g.getFontMetrics(Hud.bold(14f));
        List<String> lines = new ArrayList<>();
        for (String line : text) lines.addAll(wrap(fm, line, 340));
        int w = 24 + Math.max(titleFm.stringWidth(title) + 20, lines.stream().mapToInt(fm::stringWidth).max().orElse(0));
        int h = 34 + lines.size() * LINE_HEIGHT;
        int left = alignRight ? x - w : x;
        int top = Math.min(y, getHeight() - h - 4);
        Rectangle box = new Rectangle(left, top, w, h);
        Hud.panel(g, box, 12, new Color(26, 30, 28, 245), new Color(14, 18, 16, 245), Hud.GOLD);
        g.setFont(Hud.bold(14f));
        g.setColor(Hud.GOLD);
        g.drawString(title, left + 12, top + 20);
        g.setColor(new Color(255, 255, 255, 35));
        g.drawLine(left + 12, top + 27, left + w - 12, top + 27);
        g.setFont(body);
        g.setColor(Color.WHITE);
        for (int i = 0; i < lines.size(); i++) {
            g.drawString(lines.get(i), left + 12, top + 28 + (i + 1) * LINE_HEIGHT - 2);
        }
    }

    // ---------------------------------------------------------- caixa de mensagem

    /** Item da caixa de mensagem: texto, tecla desenhada antes (ou null), ícone (ou null); newRow começa linha nova. */
    private record MessageItem(String key, String text, Font font, Color color, Hud.Icon icon, boolean newRow) { }

    /** "TECLA: o que faz", como em "ESPAÇO: continuar" (a tecla vira um desenho de tecla). */
    private static final Pattern KEY_ITEM = Pattern.compile("([A-ZÇ]{1,6}): (.+)");
    private static final String SEPARATOR = "   \\|   ";
    private static final int ITEM_GAP = 22;

    private Rectangle counterBox() {
        return new Rectangle(getWidth() - 16 - COUNTER_WIDTH, HUD_Y, COUNTER_WIDTH, HUD_HEIGHT);
    }

    private Rectangle messageBox() {
        return new Rectangle(16, HUD_Y, counterBox().x - 12 - 16, HUD_HEIGHT);
    }

    /**
     * O que vai na caixa: a narração do oponente; ou o contexto em destaque e depois o aviso (âmbar) ou os
     * itens do que fazer, numa linha nova.
     */
    private List<MessageItem> messageItems() {
        List<MessageItem> items = new ArrayList<>();
        if (narration != null) {
            items.add(new MessageItem(null, narration, Hud.bold(15f), Hud.NARRATION, Hud.Icon.PLAY, true));
            return items;
        }
        if (statusContext != null) {
            for (String part : statusContext.split(SEPARATOR)) {
                items.add(new MessageItem(null, part, Hud.bold(15f), Color.WHITE, null, items.isEmpty()));
            }
        }
        if (statusWarning) {
            String text = statusHint.startsWith(ATTENTION) ? statusHint.substring(ATTENTION.length()) : statusHint;
            items.add(new MessageItem(null, text, Hud.bold(14.5f), Hud.WARNING, Hud.Icon.ALERT, true));
            return items;
        }
        boolean first = true;
        for (String part : statusHint.split(SEPARATOR)) {
            Matcher m = KEY_ITEM.matcher(part);
            items.add(m.matches()
                    ? new MessageItem(m.group(1), m.group(2), Hud.semibold(14f), Color.WHITE, null, first)
                    : new MessageItem(null, part, Hud.semibold(14f), new Color(0xDCEDC8), null, first));
            first = false;
        }
        return items;
    }

    private static final String ATTENTION = "ATENÇÃO: ";

    private static int itemWidth(Graphics2D g, MessageItem item) {
        int w = g.getFontMetrics(item.font()).stringWidth(item.text());
        if (item.key() != null) w += Hud.keycapWidth(g, item.key()) + 6;
        if (item.icon() != null) w += 20;
        return w;
    }

    /**
     * Caixa de mensagem no alto, como a de um jogo narrado: a etapa numa plaqueta sobre a borda (com as marcas,
     * ex.: rodada final, ao lado) e até 3 linhas com o contexto e o que fazer. A borda muda de cor na narração
     * dos oponentes (azul) e nos avisos (âmbar).
     */
    private void drawStatus(Graphics2D g) {
        boolean narrating = narration != null;
        Color accent = narrating ? Hud.NARRATION : statusWarning ? Hud.WARNING : Hud.GOLD;
        Rectangle box = messageBox();
        Hud.panel(g, box, 16, Hud.PANEL_TOP, Hud.PANEL_BOTTOM, accent);

        String title = narrating ? narrationTitle : statusTitle;
        g.setFont(Hud.title(13f));
        FontMetrics fm = g.getFontMetrics();
        title = ellipsize(fm, title, box.width - 60);
        Rectangle plate = new Rectangle(box.x + 18, box.y - 10, fm.stringWidth(title) + 24, 21);
        Hud.panel(g, plate, 21, blend(accent, Color.WHITE, 0.3f), accent, null);
        g.setColor(Hud.INK);
        g.drawString(title, plate.x + 12, plate.y + 15);
        int fx = plate.x + plate.width + 8;
        if (!narrating) {
            Font flagFont = Hud.heavy(10.5f);
            for (String flag : statusFlags) {
                boolean urgent = flag.equals(flag.toUpperCase());
                if (fx + g.getFontMetrics(flagFont).stringWidth(flag) + 17 > box.x + box.width - 10) break;
                fx += Hud.pill(g, flag, fx, plate.y + 2, 17, flagFont, urgent ? new Color(0xC62828) : new Color(0x2E4D3A),
                        urgent ? Color.WHITE : Hud.GOLD) + 6;
            }
        }

        // Posições: cada item segue na mesma linha enquanto couber
        List<MessageItem> items = messageItems();
        int left = box.x + 18;
        int right = box.x + box.width - 16;
        int[] rows = new int[items.size()];
        int[] xs = new int[items.size()];
        int shown = 0;
        int row = 0;
        int x = left;
        for (MessageItem item : items) {
            int w = itemWidth(g, item);
            if (x > left && (item.newRow() || x + ITEM_GAP + w > right)) {
                row++;
                x = left;
            } else if (x > left) {
                x += ITEM_GAP;
            }
            if (row >= HUD_ROWS) break;
            rows[shown] = row;
            xs[shown] = x;
            x += w;
            shown++;
        }
        int used = shown == 0 ? 0 : rows[shown - 1] + 1;
        int contentTop = box.y + 12;
        int firstBaseline = contentTop + (HUD_HEIGHT - 18 - used * HUD_ROW) / 2 + 14;

        for (int i = 0; i < shown; i++) {
            MessageItem item = items.get(i);
            int baseline = firstBaseline + rows[i] * HUD_ROW;
            int ix = xs[i];
            if (i > 0 && rows[i - 1] == rows[i]) {
                g.setColor(new Color(255, 255, 255, 90));
                g.fillOval(ix - ITEM_GAP / 2 - 2, baseline - 7, 4, 4);
            }
            if (item.icon() != null) {
                item.icon().draw(g, ix, baseline - 13, 14);
                ix += 20;
            }
            if (item.key() != null) ix += Hud.keycap(g, item.key(), ix, baseline) + 6;
            g.setFont(item.font());
            g.setColor(item.color());
            g.drawString(ellipsize(g.getFontMetrics(), item.text(), right - ix), ix, baseline);
        }
    }

    /** Painel do jogador: nome, atalho da ajuda e os números (moedas em bens, pontos, cartas) com ícones. */
    private void drawCounter(Graphics2D g) {
        Rectangle r = counterBox();
        Hud.panel(g, r, 16, Hud.PANEL_TOP, Hud.PANEL_BOTTOM, new Color(255, 255, 255, 60));
        if (counter == null) return;
        g.setFont(Hud.semibold(12f));
        String help = "ajuda";
        int hx = r.x + r.width - 14 - g.getFontMetrics().stringWidth(help);
        g.setColor(Hud.SOFT);
        g.drawString(help, hx, r.y + 22);
        int keyX = hx - 6 - Hud.keycapWidth(g, "H");
        Hud.keycap(g, "H", keyX, r.y + 23);
        g.setFont(Hud.title(14f));
        g.setColor(Hud.GOLD);
        g.drawString(ellipsize(g.getFontMetrics(), counter.name(), keyX - r.x - 24), r.x + 14, r.y + 22);
        g.setColor(new Color(255, 255, 255, 30));
        g.drawLine(r.x + 12, r.y + 31, r.x + r.width - 12, r.y + 31);

        int baseline = r.y + 58;
        Hud.Icon[] icons = {Hud.Icon.COIN, Hud.Icon.SHIELD, Hud.Icon.CARDS};
        int[] values = {counter.coins(), counter.points(), counter.cards()};
        String[] labels = {"em bens", "pontos", "cartas"};
        FontMetrics nm = g.getFontMetrics(Hud.heavy(21f));
        FontMetrics lm = g.getFontMetrics(Hud.regular(12f));
        int[] widths = new int[values.length];
        for (int i = 0; i < values.length; i++) {
            widths[i] = 23 + nm.stringWidth(String.valueOf(values[i])) + 4 + lm.stringWidth(labels[i]);
        }
        int x = r.x + 14;
        for (int i = 0; i < values.length; i++) {
            icons[i].draw(g, x, baseline - 16, 18);
            g.setFont(Hud.heavy(21f));
            g.setColor(Color.WHITE);
            String text = String.valueOf(values[i]);
            g.drawString(text, x + 23, baseline);
            g.setFont(Hud.regular(12f));
            g.setColor(Hud.SOFT);
            g.drawString(labels[i], x + 23 + nm.stringWidth(text) + 4, baseline);
            x += widths[i] + spread(r.width - 28, widths, i);
        }
    }

    /** Espaço depois do i-ésimo de vários blocos que se espalham por width (igual entre eles; nunca menos de 6). */
    private static int spread(int width, int[] widths, int i) {
        if (i == widths.length - 1) return 0;
        int total = 0;
        for (int w : widths) total += w;
        return Math.max(6, (width - total) / (widths.length - 1));
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
        Hud.panel(g, p, 18, new Color(0x24452F), new Color(0x163022), Hud.GOLD);
        g.setColor(Hud.GOLD);
        g.setFont(Hud.title(26f));
        g.drawString("Resultado", p.x + 20, p.y + 36);

        int y = p.y + 60;
        drawResultRow(g, result.header(), p.x + 20, y, true);
        for (List<String> row : result.rows()) {
            y += RESULT_ROW;
            drawResultRow(g, row, p.x + 20, y, false);
        }
        y += RESULT_ROW + 30;
        g.setColor(new Color(0xFFD54F));
        g.setFont(Hud.heavy(19f));
        g.drawString(result.winner(), p.x + 20, y);
        g.setColor(Color.WHITE);
        g.setFont(Hud.regular(13f));
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
            g.setFont(Hud.bold(15f));
            int tw = g.getFontMetrics().stringWidth(labels[i]);
            g.drawString(labels[i], b.x + (b.width - tw) / 2, b.y + 24);
        }
    }

    /** Uma linha da tabela de resultado; as colunas de número (da 3ª em diante) ficam à direita. */
    private void drawResultRow(Graphics2D g, List<String> cells, int x, int y, boolean header) {
        g.setColor(header ? new Color(0xC8E6C9) : Color.WHITE);
        g.setFont(header ? Hud.bold(13.5f) : Hud.semibold(13.5f));
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
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        for (CardSprite sprite : paintOrder()) {
            sprite.draw(g2);
            if (isRaised(sprite)) drawRaisedOutline(g2, sprite);
        }
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
        drawCounter(g2);
        drawHover(g2);
        drawResult(g2);
        g2.dispose();
    }
}
