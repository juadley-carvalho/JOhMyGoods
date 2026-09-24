package org.cardGames;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Player {

    private final List<Card> hand = new ArrayList<>();

    public Player(List<Card> deck) {
        Collections.shuffle(deck);

        if (!deck.isEmpty()) {
            for (int x = 0; x < 5; x++) {
                this.hand.add(deck.removeFirst());
            }
        }
    }

    public List<Card> getHand() {
        return hand;
    }
}
