package org.cardGames;

import org.cardGames.database.Database;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Fase IV, parte 2: cadeias de produção (manual p. 8–9). */
class ChainTest {

    private static Database database;
    private static List<Card> cards;
    private static List<Card> burners;

    private List<Card> pool; // cartas ainda não usadas no teste, para não repetir a mesma instância
    private Player player;
    private Market market;
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
        market = new Market();
        charcoal = player.build(burners.getFirst());
    }

    private Card take(java.util.function.Predicate<Card> filter) {
        Card card = pool.stream().filter(filter).findFirst().orElseThrow();
        pool.remove(card);
        return card;
    }

    private Card byName(String name) {
        return take(c -> c.getName().equals(name));
    }

    private Card hand(Resource resource) {
        Card card = take(c -> !c.isGuild() && c.getResource() == resource);
        player.receive(card);
        return card;
    }

    /** Coloca n bens (cartas quaisquer) sobre o estabelecimento. */
    private void goods(Building building, int n) {
        for (int i = 0; i < n; i++) building.addGood(take(c -> true));
    }

    /** Faz o estabelecimento produzir com o mercado completo, liberando a cadeia. */
    private Building producing(String name) {
        Card card = byName(name);
        Building building = player.build(card);
        card.getRawResources().forEach((resource, n) -> {
            for (int i = 0; i < n; i++) {
                market.addSunrise(take(c -> c.getResource() != null && (resource == Resource.QUALQUER || c.getResource() == resource)));
            }
        });
        player.placeWorker(building);
        assertTrue(player.produce(building, market, List.of()).succeeded());
        return building;
    }

    @Test
    void moinhoTransformaTrigoDaMaoEmFarinhaQuantasVezesQuiser() {
        Building mill = producing("MOINHO");
        Card wheat1 = hand(Resource.TRIGO);
        Card wheat2 = hand(Resource.TRIGO);
        Card wool = hand(Resource.LA);

        assertEquals(List.of(wheat1), player.runChain(mill, List.of(wheat1, wool)));
        assertEquals(List.of(wheat2), player.runChain(mill, List.of(wheat2)));
        assertTrue(player.runChain(mill, List.of(wool)).isEmpty(), "lã não serve para o Moinho");

        assertEquals(List.of(wool), player.getHand());
        assertTrue(mill.getGoods().containsAll(List.of(wheat1, wheat2)));
    }

    @Test
    void sapatariaUsaCouroDoCurtume() {
        Building tannery = player.build(byName("CURTUME"));
        goods(tannery, 2);
        Building shoes = producing("SAPATARIA");
        int before = shoes.goodsCount();

        assertEquals(1, player.runChain(shoes, List.of()).size());
        assertEquals(1, player.runChain(shoes, List.of()).size());
        assertTrue(player.runChain(shoes, List.of()).isEmpty(), "o couro acabou");

        assertEquals(0, tannery.goodsCount());
        assertEquals(before + 2, shoes.goodsCount());
    }

    @Test
    void couroEmDoisCurtumesOJogadorEscolheAOrigem() {
        Building first = player.build(byName("CURTUME"));
        Building second = player.build(byName("CURTUME"));
        goods(first, 2);
        goods(second, 2);
        Building shoes = producing("SAPATARIA");

        assertEquals(List.of(Resource.COURO), Production.itemsFromGoods(shoes, List.of()));
        assertEquals(List.of(first, second), Production.goodSources(player, shoes, Resource.COURO));

        Card fromSecond = second.getGoods().getLast();
        assertEquals(List.of(fromSecond), player.runChain(shoes, List.of(), java.util.Map.of(Resource.COURO, second)));
        assertEquals(2, first.goodsCount(), "o 1º Curtume não foi tocado");
        assertEquals(1, second.goodsCount());

        player.runChain(shoes, List.of(), java.util.Map.of(Resource.COURO, second));
        assertEquals(List.of(first), Production.goodSources(player, shoes, Resource.COURO), "o 2º esvaziou");
        assertEquals(1, player.runChain(shoes, List.of(), java.util.Map.of(Resource.COURO, second)).size(),
                "sem bens na origem escolhida, sai de outro");
        assertEquals(1, first.goodsCount());
    }

    @Test
    void itensDaCadeiaCobertosPelaMaoNaoPrecisamDeOrigem() {
        goods(charcoal, 1);
        Building pottery = producing("OLARIA");
        Card clay = hand(Resource.ARGILA);

        assertEquals(List.of(Resource.CARVAO), Production.itemsFromGoods(pottery, List.of(clay)));
        assertEquals(2, Production.itemsFromGoods(pottery, List.of()).size());
    }

    @Test
    void olariaPrecisaDeArgilaECarvaoJuntosEGera2Bens() {
        goods(charcoal, 2);
        Building pottery = producing("OLARIA");
        int before = pottery.goodsCount();
        List<Card> clay = List.of(hand(Resource.ARGILA), hand(Resource.ARGILA), hand(Resource.ARGILA));

        assertEquals(2, player.runChain(pottery, List.of(clay.get(0))).size());
        assertEquals(2, player.runChain(pottery, List.of(clay.get(1))).size());
        assertTrue(player.runChain(pottery, List.of(clay.get(2))).isEmpty(), "sem carvão, a 3ª argila não serve");

        assertEquals(before + 4, pottery.goodsCount(), "2 argilas + 2 carvões = 4 tijolos");
        assertEquals(0, charcoal.goodsCount());
        assertEquals(List.of(clay.get(2)), player.getHand(), "a argila que sobrou fica na mão");
    }

    @Test
    void cadeiaSoDepoisDeProduzir() {
        Building mill = player.build(byName("MOINHO"));
        Card wheat = hand(Resource.TRIGO);

        assertTrue(player.runChain(mill, List.of(wheat)).isEmpty());
        assertTrue(player.getHand().contains(wheat));
    }

    @Test
    void mercadoEGuildasNaoValemParaCadeia() {
        Building mill = producing("MOINHO");
        player.build(take(c -> c.isGuild() && c.getGuildResource() == Resource.TRIGO));
        market.addSunrise(take(c -> !c.isGuild() && c.getResource() == Resource.TRIGO));

        assertTrue(player.runChain(mill, List.of()).isEmpty());
    }

    @Test
    void cadeiaAcabaAoFimDaProducao() {
        Building mill = producing("MOINHO");
        Card wheat = hand(Resource.TRIGO);
        player.finishProduction();

        assertTrue(player.runChain(mill, List.of(wheat)).isEmpty());
    }
}
