package org.cardGames;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Áreas da mesa onde uma carta pode estar. Cada área sabe organizar as próprias cartas
 * no layout(); para criar uma área nova basta adicionar uma constante.
 */
public enum Zone {

    /** Pilha de compras: cartas viradas para baixo, canto superior esquerdo. */
    DECK(false, true, 12) {
        @Override
        public void layout(List<CardSprite> cards, int width, int height, int buildings) {
            pile(cards, SIDE, TOP);
        }
    },

    /** Pilha de descarte: canto superior direito. */
    DISCARD(false, false, 12) {
        @Override
        public void layout(List<CardSprite> cards, int width, int height, int buildings) {
            pile(cards, width - CardSprite.WIDTH - SIDE - PILE_DEPTH, TOP);
        }
    },

    /** Mercado, 1ª fileira (fase II, Nascer do Sol). */
    MARKET_SUNRISE(false, false, Integer.MAX_VALUE) {
        @Override
        public void layout(List<CardSprite> cards, int width, int height, int buildings) {
            int margin = SIDE + CardSprite.WIDTH + PILE_GAP;
            centerRow(cards, TOP, margin, width - margin);
        }
    },

    /** Mercado, 2ª fileira (fase III, Pôr do Sol). */
    MARKET_SUNSET(false, false, Integer.MAX_VALUE) {
        @Override
        public void layout(List<CardSprite> cards, int width, int height, int buildings) {
            int margin = SIDE + CardSprite.WIDTH + PILE_GAP;
            centerRow(cards, TOP + CardSprite.HEIGHT + 10, margin, width - margin);
        }
    },

    /**
     * Bens sobre os estabelecimentos do jogador: uma pilha virada para baixo por estabelecimento
     * (o grupo da carta é o índice do estabelecimento), aparecendo por cima dele
     * (vem antes de BUILDINGS para ser desenhada atrás).
     */
    GOODS(false, true, Integer.MAX_VALUE) {
        @Override
        public void layout(List<CardSprite> cards, int width, int height, int buildings) {
            Map<Integer, List<CardSprite>> piles = new TreeMap<>();
            for (CardSprite card : cards) {
                piles.computeIfAbsent(card.getGroup(), g -> new ArrayList<>()).add(card);
            }
            piles.forEach((group, pile) -> pile(pile, buildingX(group), bottomRowY(height) - GOODS_PEEK));
        }

        @Override
        public boolean isVisible(List<CardSprite> cards, int index) {
            // Só as cartas do topo de cada pilha precisam ser desenhadas
            int group = cards.get(index).getGroup();
            int above = 0;
            for (int i = index + 1; i < cards.size(); i++) {
                if (cards.get(i).getGroup() == group) above++;
            }
            return above < PILE_VISIBLE;
        }
    },

    /** Estabelecimentos construídos pelo jogador: canto inferior esquerdo. */
    BUILDINGS(false, false, Integer.MAX_VALUE) {
        @Override
        public void layout(List<CardSprite> cards, int width, int height, int buildings) {
            for (int i = 0; i < cards.size(); i++) {
                cards.get(i).setSlot(buildingX(i), bottomRowY(height));
            }
        }
    },

    /** Carta escolhida para construir (Fase II): virada para baixo, logo à direita dos estabelecimentos. */
    PLANNED(false, true, Integer.MAX_VALUE) {
        @Override
        public void layout(List<CardSprite> cards, int width, int height, int buildings) {
            for (CardSprite card : cards) {
                card.setSlot(buildingX(buildings), bottomRowY(height));
            }
        }
    },

    /**
     * Cartas dos oponentes (mão, bens e estabelecimentos): não são desenhadas; as cartas voam até a
     * área dos oponentes, à esquerda, e somem. O que cada oponente tem aparece no painel da mesa.
     */
    OPPONENTS(false, true, 0) {
        @Override
        public void layout(List<CardSprite> cards, int width, int height, int buildings) {
            for (CardSprite card : cards) card.setSlot(SIDE, TOP + CardSprite.HEIGHT + 10);
        }
    },

    /** Mão do jogador: leque centralizado embaixo, à direita dos estabelecimentos e da carta a construir. Única área com cartas selecionáveis. */
    HAND(true, false, Integer.MAX_VALUE) {
        @Override
        public void layout(List<CardSprite> cards, int width, int height, int buildings) {
            centerRow(cards, bottomRowY(height), buildingX(buildings + 1) - BUILDING_GAP + PILE_GAP, width - HAND_MARGIN);
        }
    };

    private static final int SIDE = 50;
    private static final int TOP = 30;
    private static final int PILE_GAP = 40;
    private static final int PILE_DEPTH = 16;       // espessura máxima visual de uma pilha
    private static final int MAX_SPACING = 40;
    private static final int HAND_MARGIN = 40;
    private static final int HAND_BOTTOM_MARGIN = 20;
    private static final int GOODS_PEEK = 34;
    private static final int BUILDING_GAP = 10;      // espaço entre estabelecimentos lado a lado
    private static final int PILE_VISIBLE = 12;      // cartas desenhadas em cada pilha de bens        // quanto da pilha de bens aparece acima do estabelecimento

    private final boolean selectable;
    private final boolean faceDown;
    private final int visibleLimit; // quantas cartas do topo precisam ser desenhadas

    Zone(boolean selectable, boolean faceDown, int visibleLimit) {
        this.selectable = selectable;
        this.faceDown = faceDown;
        this.visibleLimit = visibleLimit;
    }

    /** Se as cartas desta área respondem ao clique de seleção. */
    public boolean isSelectable() { return selectable; }

    /** Se as cartas desta área ficam viradas para baixo. */
    public boolean isFaceDown() { return faceDown; }

    /** Se a carta na posição index precisa ser desenhada (numa pilha grande, só o topo aparece). */
    public boolean isVisible(List<CardSprite> cards, int index) {
        return index >= cards.size() - visibleLimit;
    }

    /**
     * Define o "slot" (posição de repouso) de cada carta da área.
     * buildings é o nº de estabelecimentos na mesa: as áreas de baixo se deslocam conforme ele cresce.
     */
    public abstract void layout(List<CardSprite> cards, int width, int height, int buildings);

    // ------------------------------------------------------------------ helpers

    /** Posição horizontal do i-ésimo estabelecimento. */
    public static int buildingX(int i) {
        return SIDE + i * (CardSprite.WIDTH + BUILDING_GAP);
    }

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