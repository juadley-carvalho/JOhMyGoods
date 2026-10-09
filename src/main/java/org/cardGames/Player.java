package org.cardGames;

import java.util.ArrayList;
import java.util.List;

public class Player {

    private final List<Card> hand = new ArrayList<>();

    public List<Card> getHand() {
        return hand;
    }

    public void receive(Card card) {
        hand.add(card);
    }

    /** Esvazia a mão e devolve as cartas que estavam nela. */
    public List<Card> takeHand() {
        List<Card> all = new ArrayList<>(hand);
        hand.clear();
        return all;
    }
}