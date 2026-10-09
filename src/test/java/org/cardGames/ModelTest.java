package org.cardGames;

import org.cardGames.database.Database;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ModelTest {

    private static Database database;
    private static List<Card> cards;
    private static List<Card> burners;

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

    private static Card byNumber(int number) {
        return cards.stream().filter(c -> c.getNumber() == number).findFirst().orElseThrow();
    }

    @Test
    void carvoariaExigeDuasLasEUmaMadeira() {
        Card charcoal = burners.getFirst();
        assertEquals(Map.of(Resource.LA, 2, Resource.MADEIRA, 1), charcoal.getRawResources());
        assertEquals(List.of(Resource.MADEIRA), charcoal.getChainResources());
        assertEquals(Resource.CARVAO, charcoal.getProduct());
        assertEquals(1, charcoal.getGoodValue());
        assertTrue(charcoal.isProducer());
    }

    @Test
    void padariaTemCadeiaDeDoisItens() {
        Card bakery = byNumber(5);
        assertEquals("PADARIA", bakery.getName());
        assertEquals(9, bakery.getCost());
        assertEquals(3, bakery.getPoints());
        assertEquals(Resource.TRIGO, bakery.getResource());
        assertEquals(List.of(Resource.CARVAO, Resource.FARINHA), bakery.getChainResources());
        assertEquals(4, bakery.getGoodValue());
    }

    @Test
    void guildasNaoProduzem() {
        assertTrue(cards.stream().filter(Card::isGuild).noneMatch(Card::isProducer));
        assertTrue(cards.stream().filter(Card::isGuild).allMatch(c -> c.getRawResources().isEmpty()));
    }

    @Test
    void mercadoContaRecursosDasDuasFileiras() {
        Market market = new Market();
        Card wheat = byNumber(5);  // recurso TRIGO
        Card wool = byNumber(20);  // recurso LA
        market.addSunrise(wheat);
        market.addSunrise(wool);
        market.addSunset(wool);
        assertEquals(Map.of(Resource.TRIGO, 1, Resource.LA, 2), market.resourceCount());

        assertEquals(3, market.clear().size());
        assertTrue(market.isEmpty());
        assertTrue(market.resourceCount().isEmpty());
    }

    @Test
    void estabelecimentoAceitaUmaPessoaPorVez() {
        Building building = new Building(burners.getFirst());
        Worker worker = new Worker();
        building.setPerson(worker);
        assertTrue(building.isOccupied());
        assertThrows(IllegalStateException.class,
                () -> building.setPerson(new Assistant(1, 2, 3, List.of(Color.VERDE))));
        building.clearPerson();
        assertFalse(building.isOccupied());
    }

    @Test
    void trabalhadorAlternaAtentoEDistraido() {
        Worker worker = new Worker();
        assertEquals(Worker.Mode.ATTENTIVE, worker.getMode());
        assertEquals(2, worker.getMode().getGoods());
        worker.toggleMode();
        assertEquals(Worker.Mode.DISTRACTED, worker.getMode());
        assertEquals(1, worker.getMode().getGoods());
        assertEquals(1, worker.getMode().getMissingAllowed());
    }

    @Test
    void assistenteContaCoresExigidas() {
        Assistant assistant = new Assistant(2, 2, 3, List.of(Color.VERDE, Color.AZUL_CLARO, Color.PRETO, Color.PRETO));
        assertEquals(Map.of(Color.VERDE, 1, Color.AZUL_CLARO, 1, Color.PRETO, 2), assistant.getRequiredColorCount());
    }
}
