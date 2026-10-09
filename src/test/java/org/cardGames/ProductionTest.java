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

/** Fase IV, parte 1: iniciar a produção (manual p. 6–7). */
class ProductionTest {

    private static Database database;
    private static List<Card> cards;
    private static List<Card> burners;

    private List<Card> pool; // cartas ainda não usadas no teste, para não repetir a mesma instância
    private Player player;
    private Market market;
    private Building charcoal; // Carvoaria: 2 lã + 1 madeira

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
        Card burner = burners.stream()
                .filter(c -> c.getRawResources().equals(Map.of(Resource.LA, 2, Resource.MADEIRA, 1)))
                .findFirst().orElseThrow();
        charcoal = player.build(burner);
    }

    /** Uma carta de estabelecimento (não guilda) que oferece o recurso, ainda não usada. */
    private Card resource(Resource resource) {
        Card card = pool.stream().filter(c -> !c.isGuild() && c.getResource() == resource).findFirst().orElseThrow();
        pool.remove(card);
        return card;
    }

    private Card byName(String name) {
        Card card = pool.stream().filter(c -> c.getName().equals(name)).findFirst().orElseThrow();
        pool.remove(card);
        return card;
    }

    private void market(Resource... resources) {
        for (Resource r : resources) market.addSunrise(resource(r));
    }

    private Card hand(Resource resource) {
        Card card = resource(resource);
        player.receive(card);
        return card;
    }

    @Test
    void atentoComMercadoCompletoProduz2SemGastarCartas() {
        market(Resource.LA, Resource.LA, Resource.MADEIRA);
        Card kept = hand(Resource.LA);
        player.placeWorker(charcoal);

        Production.Result result = player.produce(charcoal, market, List.of(kept));

        assertEquals(2, result.goods());
        assertTrue(result.usedCards().isEmpty(), "carta oferecida mas desnecessária fica na mão");
        assertTrue(player.getHand().contains(kept));
    }

    @Test
    void atentoCompletaComCartaDaMao() {
        market(Resource.LA, Resource.LA);
        Card wood = hand(Resource.MADEIRA);
        player.placeWorker(charcoal);

        Production.Result result = player.produce(charcoal, market, List.of(wood));

        assertEquals(2, result.goods());
        assertEquals(List.of(wood), result.usedCards());
        assertFalse(player.getHand().contains(wood));
    }

    @Test
    void atentoFaltandoUmNaoProduz() {
        market(Resource.LA, Resource.MADEIRA);
        Card wood = hand(Resource.MADEIRA);
        player.placeWorker(charcoal);

        Production.Result result = player.produce(charcoal, market, List.of(wood));

        assertFalse(result.succeeded());
        assertTrue(player.getHand().contains(wood), "produção falha não gasta cartas");
    }

    @Test
    void distraidoIgnoraUmRecursoEProduz1() {
        market(Resource.LA, Resource.MADEIRA);
        player.placeWorker(charcoal);
        player.getWorker().toggleMode();

        Production.Result result = player.produce(charcoal, market, List.of());

        assertEquals(1, result.goods());
    }

    @Test
    void distraidoNaoIgnoraDois() {
        market(Resource.MADEIRA);
        player.placeWorker(charcoal);
        player.getWorker().toggleMode();

        assertFalse(player.produce(charcoal, market, List.of()).succeeded());
    }

    @Test
    void cartaDaMaoValeParaUmUnicoRecurso() {
        market(Resource.MADEIRA);
        Card wool = hand(Resource.LA);
        player.placeWorker(charcoal);

        assertFalse(player.produce(charcoal, market, List.of(wool)).succeeded());

        Card wool2 = hand(Resource.LA);
        assertEquals(2, player.produce(charcoal, market, List.of(wool, wool2)).goods());
    }

    @Test
    void assistenteExigeTudoEProduz1() {
        Assistant assistant = new Assistant(1, 0, 0, List.of());
        charcoal.setPerson(assistant);

        market(Resource.LA, Resource.MADEIRA);
        assertFalse(player.produce(charcoal, market, List.of()).succeeded());

        market(Resource.LA);
        assertEquals(1, player.produce(charcoal, market, List.of()).goods());
    }

    @Test
    void guildaDeRecursoContaSoParaODono() {
        Card woolGuild = pool.stream()
                .filter(c -> c.isGuild() && c.getGuildResource() == Resource.LA).findFirst().orElseThrow();
        player.build(woolGuild);
        market(Resource.LA, Resource.MADEIRA);
        player.placeWorker(charcoal);

        assertEquals(2, Production.freeResources(player, market).get(Resource.LA),
                "1 lã do mercado + 1 da guilda");
        assertEquals(2, player.produce(charcoal, market, List.of()).goods());

        Player other = new Player();
        assertEquals(1, Production.freeResources(other, market).get(Resource.LA));
    }

    @Test
    void vidracariaAceitaQuaisquerRecursos() {
        Card glass = byName("VIDRACARIA");
        int needed = glass.getRawResources().get(Resource.QUALQUER);
        Building glassworks = player.build(glass);
        player.placeWorker(glassworks);

        Resource[] mix = {Resource.LA, Resource.MADEIRA, Resource.TRIGO, Resource.ARGILA};
        for (int i = 0; i < needed - 1; i++) market.addSunrise(resource(mix[i % mix.length]));
        assertEquals(Map.of(Resource.QUALQUER, 1), Production.missing(glass, Production.freeResources(player, market)));
        assertFalse(player.produce(glassworks, market, List.of()).succeeded());

        Card any = hand(Resource.TRIGO);
        Production.Result result = player.produce(glassworks, market, List.of(any));
        assertEquals(2, result.goods());
        assertEquals(List.of(any), result.usedCards());
    }

    @Test
    void guildaNaoProduz() {
        Card guild = byName("GUILDA");
        assertFalse(Production.canProduce(guild, Map.of(), List.of(), 1));
    }

    @Test
    void trabalhadorSaiAoFimDaProducaoEAssistentePermanece() {
        player.placeWorker(charcoal);
        player.getWorker().toggleMode();
        Building other = player.build(resource(Resource.TRIGO));
        Assistant assistant = new Assistant(1, 0, 0, List.of());
        other.setPerson(assistant);

        player.finishProduction();

        assertNull(player.getWorkerBuilding());
        assertEquals(Worker.Mode.ATTENTIVE, player.getWorker().getMode());
        assertSame(assistant, other.getPerson());
    }
}
