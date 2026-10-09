package org.cardGames;

import java.util.List;

/**
 * Áreas da mesa onde uma carta pode estar. Cada área sabe organizar as próprias cartas
 * no layout(); para criar uma área nova basta adicionar uma constante.
 */
public enum Zone {

    /** Pilha de compras: cartas viradas para baixo, canto superior esquerdo. */
    DECK(false, true, 12) {
        @Override
        public void layout(List<CardSprite> cards, int width, int height) {
            pile(cards, SIDE, TOP);
        }
    },

    /** Pilha de descarte: canto superior direito. */
    DISCARD(false, false, 12) {
        @Override
        public void layout(List<CardSprite> cards, int width, int height) {
            pile(cards, width - CardSprite.WIDTH - SIDE - PILE_DEPTH, TOP);
        }
    },

    /** Mercado, 1ª fileira (fase II, Nascer do Sol). */
    MARKET_SUNRISE(false, false, Integer.MAX_VALUE) {
        @Override
        public void layout(List<CardSprite> cards, int width, int height) {
            int margin = SIDE + CardSprite.WIDTH + PILE_GAP;
            centerRow(cards, TOP, margin, width - margin);
        }
    },

    /** Mercado, 2ª fileira (fase III, Pôr do Sol). */
    MARKET_SUNSET(false, false, Integer.MAX_VALUE) {
        @Override
        public void layout(List<CardSprite> cards, int width, int height) {
            int margin = SIDE + CardSprite.WIDTH + PILE_GAP;
            centerRow(cards, TOP + CardSprite.HEIGHT + 10, margin, width - margin);
        }
    },

    /**
     * Bens sobre os estabelecimentos do jogador: pilha virada para baixo, aparecendo
     * por cima da Carvoaria (vem antes de BUILDINGS para ser desenhada atrás dela).
     * Por enquanto só a Carvoaria tem bens; a Fase 2 separa uma pilha por estabelecimento.
     */
    GOODS(false, true, 12) {
        @Override
        public void layout(List<CardSprite> cards, int width, int height) {
            pile(cards, SIDE, bottomRowY(height) - GOODS_PEEK);
        }
    },

    /** Estabelecimentos construídos pelo jogador: canto inferior esquerdo. */
    BUILDINGS(false, false, Integer.MAX_VALUE) {
        @Override
        public void layout(List<CardSprite> cards, int width, int height) {
            for (int i = 0; i < cards.size(); i++) {
                cards.get(i).setSlot(SIDE + i * (CardSprite.WIDTH + 10), bottomRowY(height));
            }
        }
    },

    /** Mão do jogador: leque centralizado embaixo, à direita dos estabelecimentos. Única área com cartas clicáveis. */
    HAND(true, false, Integer.MAX_VALUE) {
        @Override
        public void layout(List<CardSprite> cards, int width, int height) {
            centerRow(cards, bottomRowY(height), SIDE + CardSprite.WIDTH + PILE_GAP, width - HAND_MARGIN);
        }
    };

    private static final int SIDE = 50;
    private static final int TOP = 30;
    private static final int PILE_GAP = 40;
    private static final int PILE_DEPTH = 16;       // espessura máxima visual de uma pilha
    private static final int MAX_SPACING = 40;
    private static final int HAND_MARGIN = 40;
    private static final int HAND_BOTTOM_MARGIN = 20;
    private static final int GOODS_PEEK = 34;        // quanto da pilha de bens aparece acima do estabelecimento

    private final boolean selectable;
    private final boolean faceDown;
    private final int visibleLimit;

    Zone(boolean selectable, boolean faceDown, int visibleLimit) {
        this.selectable = selectable;
        this.faceDown = faceDown;
        this.visibleLimit = visibleLimit;
    }

    /** Se as cartas desta área respondem ao clique de seleção. */
    public boolean isSelectable() { return selectable; }

    /** Se as cartas desta área ficam viradas para baixo. */
    public boolean isFaceDown() { return faceDown; }

    /** Quantas cartas do topo precisam ser desenhadas (numa pilha de 100 cartas, só as últimas aparecem). */
    public int getVisibleLimit() { return visibleLimit; }

    /** Define o "slot" (posição de repouso) de cada carta da área. */
    public abstract void layout(List<CardSprite> cards, int width, int height);

    // ------------------------------------------------------------------ helpers

    private static int bottomRowY(int height) {
        return height - CardSprite.HEIGHT - HAND_BOTTOM_MARGIN;
    }

    /** Fileira sobreposta e centralizada entre minX e maxX; o espaçamento encolhe se houver muitas cartas. */
    private static void centerRow(List<CardSprite> cards, int y, int minX, int maxX) {
        int n = cards.size();
        if (n == 0) return;

        int available = (maxX - minX) - CardSprite.WIDTH;
        int spacing = (n > 1) ? Math.max(4, Math.min(MAX_SPACING, available / (n - 1))) : 0;
        int totalWidth = CardSprite.WIDTH + spacing * (n - 1);
        int startX = minX + ((maxX - minX) - totalWidth) / 2;

        // A ordem da lista é a ordem de desenho: a última carta fica por cima.
        for (int i = 0; i < n; i++) {
            cards.get(i).setSlot(startX + i * spacing, y);
        }
    }

    /** Pilha: o topo (última carta) fica em (x, y); as de baixo escapam um pouco para dar espessura. */
    private static void pile(List<CardSprite> cards, int x, int y) {
        int n = cards.size();
        for (int i = 0; i < n; i++) {
            int depth = (n - 1 - i) / 10;
            int offset = Math.min(depth, PILE_DEPTH / 2) * 2;
            cards.get(i).setSlot(x + offset, y + offset);
        }
    }
}