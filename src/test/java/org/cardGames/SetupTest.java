package org.cardGames;

import org.cardGames.database.Database;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SetupTest {

    private static Database database;
    private static List<Card> cards;
    private static List<Card> burners;
    private static List<Assistant> assistants;

    @BeforeAll
    static void load() {
        database = new Database();
        cards = database.getCards();
        burners = database.getCharcoalBurners();
        assistants = database.getAssistants();
    }

    @AfterAll
    static void close() {
        database.closeConnection();
    }

    private static GameState newGame(int players) {
        return Setup.newGame(cards, burners, assistants, players, new Random(42));
    }

    @Test
    void bancoTemTodasAsCartas() {
        assertEquals(94, cards.size(), "estabelecimentos + guildas");
        assertEquals(17, cards.stream().filter(Card::isGuild).count(), "guildas (cartas pretas)");
        assertEquals(4, burners.size());
        assertTrue(burners.stream().allMatch(c -> c.getName().equals("CARVOARIA") && c.getResource() == null));
        assertEquals(16, assistants.size());
        assertTrue(assistants.stream().allMatch(a -> !a.getRequiredColors().isEmpty()));
    }

    @ParameterizedTest
    @ValueSource(ints = {2, 3, 4})
    void cadaJogadorComecaComCarvoariaSeteCarvoesECincoCartas(int n) {
        GameState state = newGame(n);
        assertEquals(n, state.players().size());

        for (Player player : state.players()) {
            assertEquals(1, player.getBuildings().size());
            Building charcoal = player.getCharcoalBurner();
            assertEquals("CARVOARIA", charcoal.getCard().getName());
            assertEquals(Setup.INITIAL_COAL, charcoal.goodsCount());
            assertEquals(Setup.INITIAL_COAL, charcoal.goodsValue(), "cada carvão vale 1 moeda");
            assertFalse(charcoal.isOccupied());
            assertEquals(Setup.INITIAL_HAND, player.getHand().size());
            assertNull(player.getPlannedBuilding());
            assertTrue(player.getAssistants().isEmpty());
        }
        long distinct = state.players().stream().map(p -> p.getCharcoalBurner().getCard()).distinct().count();
        assertEquals(n, distinct, "Carvoarias diferentes para cada jogador");
    }

    @ParameterizedTest
    @ValueSource(ints = {2, 3, 4})
    void sorteiaDoisAssistentesPorJogador(int n) {
        GameState state = newGame(n);
        assertEquals(2 * n, state.availableAssistants().size());
        assertEquals(2 * n, state.availableAssistants().stream().distinct().count());
    }

    @Test
    void nenhumaCartaSomeOuDuplica() {
        GameState state = newGame(4);
        Set<Card> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        seen.addAll(state.deck().getDrawPile());
        int total = state.deck().drawPileSize();
        for (Player player : state.players()) {
            seen.addAll(player.getHand());
            seen.addAll(player.getCharcoalBurner().getGoods());
            total += player.getHand().size() + player.getCharcoalBurner().goodsCount();
        }
        assertEquals(cards.size(), total);
        assertEquals(cards.size(), seen.size());
        assertTrue(state.market().isEmpty());
    }

    @Test
    void mesmaSementeRepeteAPartida() {
        GameState a = newGame(3);
        GameState b = newGame(3);
        assertEquals(a.deck().getDrawPile(), b.deck().getDrawPile());
        for (int i = 0; i < 3; i++) {
            assertEquals(a.players().get(i).getHand(), b.players().get(i).getHand());
        }
    }

    @Test
    void recusaNumeroInvalidoDeJogadores() {
        assertThrows(IllegalArgumentException.class, () -> newGame(1));
        assertThrows(IllegalArgumentException.class, () -> newGame(5));
    }
}
