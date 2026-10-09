package org.cardGames;

import org.cardGames.database.Database;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Assistentes: contratar (cores + custo), alocar, mover por 2 moedas, produzir 1 bem e iniciar cadeia. */
class AssistantTest {

    private static Database database;
    private static List<Card> cards;
    private static List<Card> burners;

    private List<Card> pool;
    private Player player;
    private Building charcoal;

    @BeforeAll
    static void load() {
        database = new Database();
        cards = database.getCards();
        burners = database.getCharcoalBurners();
    }

    @AfterAll
    static void close() {
        database.closeConnection();
    }

    @BeforeEach
    void setUp() {
        pool = new ArrayList<>(cards);
        player = new Player();
        charcoal = player.build(burners.getFirst());
    }

    private Card take(java.util.function.Predicate<Card> filter) {
        Card card = pool.stream().filter(filter).findFirst().orElseThrow();
        pool.remove(card);
        return card;
    }

    private void addGoods(Building building, int n) {
        for (int i = 0; i < n; i++) building.addGood(take(c -> true));
    }

    /** Assistente que exige só as cores dadas. */
    private static Assistant assistant(int cost, Color... colors) {
        return new Assistant(99, cost, 2, List.of(colors));
    }

    @Test
    void hiringNeedsColorsCostAndFreeBuilding() {
        Color color = charcoal.getCard().getColor();
        addGoods(charcoal, 5); // carvão vale 1
        Assistant a = assistant(3, color);

        // A Carvoaria está livre: contrata pagando 3
        assertFalse(player.canHire(a, Map.of(charcoal, 2)));
        assertTrue(player.canHire(a, Map.of(charcoal, 3)));

        // Cor que o jogador não tem
        Color other = color == Color.VERDE ? Color.AMARELO : Color.VERDE;
        assertFalse(player.canHire(assistant(1, other), Map.of(charcoal, 3)));

        // Duas vezes a mesma cor exige dois estabelecimentos dela
        assertFalse(player.hasColorsFor(assistant(1, color, color)));

        // Sem estabelecimento livre não dá para contratar
        player.placeWorker(charcoal);
        assertFalse(player.canHire(a, Map.of(charcoal, 3)));
    }

    @Test
    void hireAllocatesImmediatelyAndPays() {
        addGoods(charcoal, 5);
        Assistant a = assistant(3, charcoal.getCard().getColor());
        List<Card> paid = player.hireAssistant(a, Map.of(charcoal, 3), charcoal);

        assertEquals(3, paid.size());
        assertEquals(2, charcoal.goodsCount());
        assertEquals(List.of(a), player.getAssistants());
        assertSame(charcoal, player.getAssistantBuilding(a));
        // O trabalhador não pode ir para o estabelecimento do assistente
        assertFalse(player.canPlaceWorker(charcoal));
    }

    @Test
    void assistantProducesOneGoodWithFullResources() {
        Assistant a = assistant(0, charcoal.getCard().getColor());
        player.hireAssistant(a, Map.of(), charcoal);

        Market market = new Market();
        assertFalse(player.produce(charcoal, market, List.of()).succeeded());

        // Com todos os recursos da Carvoaria na mão, produz 1 bem e libera a cadeia
        List<Card> needed = new ArrayList<>();
        charcoal.getCard().getRawResources().forEach((r, n) -> {
            for (int i = 0; i < n; i++) needed.add(take(c -> c.getResource() == r));
        });
        needed.forEach(player::receive);
        Production.Result result = player.produce(charcoal, market, needed);
        assertEquals(Assistant.GOODS, result.goods());
        assertTrue(charcoal.hasProducedThisRound());

        // O assistente permanece depois da produção
        player.finishProduction();
        assertSame(charcoal, player.getAssistantBuilding(a));
    }

    @Test
    void moveCostsTwoCoins() {
        Building other = player.build(take(Card::isProducer));
        Assistant a = assistant(0, charcoal.getCard().getColor());
        player.hireAssistant(a, Map.of(), charcoal);

        assertFalse(player.canMoveAssistant(a, other, player.cheapestPayment(Player.MOVE_ASSISTANT_COST)));

        addGoods(charcoal, 3);
        Map<Building, Integer> cost = player.cheapestPayment(Player.MOVE_ASSISTANT_COST);
        assertEquals(Map.of(charcoal, 2), cost);
        List<Card> paid = player.moveAssistant(a, other, cost);

        assertEquals(2, paid.size());
        assertSame(other, player.getAssistantBuilding(a));
        assertFalse(charcoal.isOccupied());
    }

    @Test
    void producingBuildingsListsWorkerThenAssistants() {
        Building other = player.build(take(c -> c.isProducer()));
        Assistant a = assistant(0, charcoal.getCard().getColor());
        player.hireAssistant(a, Map.of(), charcoal);
        player.placeWorker(other);

        assertEquals(List.of(other, charcoal), player.producingBuildings());
    }

    @Test
    void cheapestPaymentAvoidsOverpaying() {
        Building three = player.build(take(c -> c.isProducer() && c.getGoodValue() == 3));
        addGoods(three, 2);
        addGoods(charcoal, 2);
        assertEquals(Map.of(charcoal, 2), player.cheapestPayment(2));
        assertEquals(Map.of(three, 1), player.cheapestPayment(3));
        assertTrue(player.cheapestPayment(20).isEmpty());
    }
}
