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

/** Fase IV, parte 3: construção paga com bens (manual p. 9), guilda de carta e regra de exaustão. */
class BuildTest {

    private static Database database;
    private static List<Card> cards;
    private static List<Card> burners;

    private List<Card> pool;
    private Player player;

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
        player.build(burners.getFirst());
    }

    private Card take(java.util.function.Predicate<Card> filter) {
        Card card = pool.stream().filter(filter).findFirst().orElseThrow();
        pool.remove(card);
        return card;
    }

    /** Estabelecimento cujo bem vale {@code value} moedas, com n bens sobre ele. */
    private Building withGoods(int value, int n) {
        Building building = player.build(take(c -> c.isProducer() && c.getGoodValue() == value));
        for (int i = 0; i < n; i++) building.addGood(take(c -> true));
        return building;
    }

    private Card plan(int cost) {
        Card card = take(c -> c.getCost() == cost);
        player.receive(card);
        player.planBuilding(card);
        return card;
    }

    @Test
    void custo7PagoCom3Mais3Mais2() {
        Building iron = withGoods(3, 2);
        Building planks = withGoods(2, 1);
        Card planned = plan(7);
        int before = player.getBuildings().size();
        Map<Building, Integer> payment = Map.of(iron, 2, planks, 1);

        assertEquals(8, Player.paymentValue(payment));
        List<Card> paid = player.buildPlanned(payment);

        assertEquals(3, paid.size());
        assertEquals(0, iron.goodsCount());
        assertEquals(0, planks.goodsCount());
        assertEquals(before + 1, player.getBuildings().size());
        assertSame(planned, player.getBuildings().getLast().getCard());
        assertNull(player.getPlannedBuilding());
    }

    @Test
    void pagamentoInsuficienteNaoConstroi() {
        Building iron = withGoods(3, 2);
        plan(7);

        assertFalse(player.canBuild(Map.of(iron, 2)), "6 < 7");
        assertThrows(IllegalStateException.class, () -> player.buildPlanned(Map.of(iron, 2)));
        assertEquals(2, iron.goodsCount());
    }

    @Test
    void naoPagaComBensQueNaoTem() {
        Building iron = withGoods(3, 1);
        plan(3);
        assertFalse(player.canBuild(Map.of(iron, 2)));
        assertTrue(player.canBuild(Map.of(iron, 1)));
    }

    @Test
    void semTroco() {
        Building iron = withGoods(3, 3);
        plan(7);
        assertEquals(3, player.buildPlanned(Map.of(iron, 3)).size(), "paga 9 por um custo 7, sem troco");
    }

    @Test
    void naoConstruirDevolveACartaParaAMao() {
        Card planned = plan(7);
        player.cancelPlannedBuilding();
        assertTrue(player.getHand().contains(planned));
    }

    @Test
    void novoEstabelecimentoRecebeTrabalhadorNaRodadaSeguinte() {
        Building iron = withGoods(3, 3);
        plan(7);
        player.buildPlanned(Map.of(iron, 3));
        assertTrue(player.canPlaceWorker(player.getBuildings().getLast()));
    }

    @Test
    void guildaDeCartaDa1ExtraComAte3Cartas() {
        assertEquals(0, player.newHandBonus());
        player.build(take(Card::isCardGuild));
        for (int i = 0; i < 3; i++) player.receive(take(c -> true));
        assertEquals(1, player.newHandBonus());
        player.receive(take(c -> true));
        assertEquals(0, player.newHandBonus(), "com 4 cartas não ganha");
    }

    @Test
    void existem3GuildasDeCarta() {
        assertEquals(3, cards.stream().filter(Card::isCardGuild).count());
    }

    @Test
    void exaustaoDescartaMetadeDaMao() {
        for (int i = 0; i < 5; i++) player.receive(take(c -> true));
        assertEquals(2, player.discardHalf().size());
        assertEquals(3, player.getHand().size());
    }

    @Test
    void exaustaoComAsCartasEscolhidas() {
        List<Card> hand = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            Card card = take(c -> true);
            player.receive(card);
            hand.add(card);
        }
        assertEquals(2, player.exhaustionDiscards(), "metade de 5, arredondada para baixo");
        Card outside = take(c -> true);
        assertThrows(IllegalArgumentException.class, () -> player.discardChosen(List.of(hand.get(4))), "só 1 carta");
        assertThrows(IllegalArgumentException.class, () -> player.discardChosen(List.of(hand.get(4), outside)), "fora da mão");
        assertThrows(IllegalArgumentException.class, () -> player.discardChosen(List.of(hand.get(4), hand.get(4))), "repetida");
        assertEquals(5, player.getHand().size(), "nada saiu nas tentativas inválidas");

        assertEquals(List.of(hand.get(4), hand.get(1)), player.discardChosen(List.of(hand.get(4), hand.get(1))));
        assertEquals(List.of(hand.get(0), hand.get(2), hand.get(3)), player.getHand());
    }

    @Test
    void baralhoExaurido() {
        Deck deck = new Deck(new ArrayList<>(cards));
        assertFalse(deck.isExhausted());
        while (deck.draw() != null) { }
        assertTrue(deck.isExhausted());
        deck.discard(cards.getFirst());
        assertFalse(deck.isExhausted());
    }
}
