package org.cardGames;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Pilha de compras e pilha de descarte. O topo de cada pilha é o ÚLTIMO elemento da lista,
 * o que casa com a ordem de desenho da tela (a última carta da lista fica por cima).
 */
public class Deck {

    private final List<Card> drawPile = new ArrayList<>();
    private final List<Card> discardPile = new ArrayList<>();

    public Deck(List<Card> cards) {
        drawPile.addAll(cards);
        Collections.shuffle(drawPile);

        long suns = drawPile.stream().filter(Card::isSun).count();

        List<Card> testList = new ArrayList<>(List.copyOf(drawPile));

        while (!testList.isEmpty()) {
            Card card = testList.removeFirst();
            if (card.isSun()) {
               //card.info();
            }
        }
        if (suns < 2) {
            throw new IllegalArgumentException("01 Baralho com " + suns + " meio(s) sol(is): verifique CAR_SOL no banco");
        }
    }

    /** Compra a carta do topo, ou null se não houver nenhuma. Não reembaralha sozinho: veja needsReshuffle(). */
    public Card draw() {
        return drawPile.isEmpty() ? null : drawPile.removeFirst();
    }

    public void discard(Card card) {
        discardPile.add(card);
    }

    /** Manual, fase I: quando a pilha de compras acaba, o descarte é embaralhado para formar uma nova pilha. */
    public boolean needsReshuffle() {
        return drawPile.isEmpty() && !discardPile.isEmpty();
    }

    /** Embaralha o descarte para dentro da pilha de compras e devolve a nova ordem (fundo -> topo). */
    public List<Card> reshuffle() {
        Collections.shuffle(discardPile);
        drawPile.addAll(discardPile);
        discardPile.clear();
        return List.copyOf(drawPile);
    }

    public List<Card> getDrawPile() { return List.copyOf(drawPile); }
    public int drawPileSize() { return drawPile.size(); }
    public int discardSize() { return discardPile.size(); }
}
