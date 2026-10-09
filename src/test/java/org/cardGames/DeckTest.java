package org.cardGames;

import org.cardGames.database.Database;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DeckTest {

    private static Database database;
    private static List<Card> cards;

    @BeforeAll
    static void loadCards() {
        database = new Database();
        cards = Setup.createDeck(database);
    }

    @AfterAll
    static void close() {
        database.closeConnection();
    }

    @Test
    void bancoCarregaPeloClasspath() {
        assertFalse(cards.isEmpty());
        assertTrue(cards.stream().allMatch(c -> c.getImage() != null), "toda carta deve ter imagem");
    }

    @Test
    void compraTiraDoTopoAteEsvaziar() {
        Deck deck = new Deck(cards);
        int total = deck.drawPileSize();
        assertEquals(cards.size(), total);
        for (int i = 0; i < total; i++) assertNotNull(deck.draw());
        assertNull(deck.draw());
    }

    @Test
    void reembaralhaODescarte() {
        Deck deck = new Deck(cards);
        Card card;
        while ((card = deck.draw()) != null) deck.discard(card);

        assertTrue(deck.needsReshuffle());
        deck.reshuffle();
        assertEquals(cards.size(), deck.drawPileSize());
        assertEquals(0, deck.discardSize());
        assertFalse(deck.needsReshuffle());
    }

    @Test
    void recusaBaralhoComMenosDeDoisSois() {
        List<Card> semSol = cards.stream().filter(c -> !c.isSun()).toList();
        assertThrows(IllegalArgumentException.class, () -> new Deck(semSol));
    }
}
