package org.cardGames;

import org.cardGames.database.Database;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PlanningTest {

    private static Database database;
    private static List<Card> cards;
    private static List<Card> burners;

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
    void newPlayer() {
        player = new Player();
        charcoal = player.build(burners.getFirst());
    }

    private static Card first(boolean guild) {
        return cards.stream().filter(c -> c.isGuild() == guild).findFirst().orElseThrow();
    }

    @Test
    void trabalhadorComecaForaEAtento() {
        assertNull(player.getWorkerBuilding());
        assertFalse(player.isPlanningComplete());
        assertEquals(Worker.Mode.ATTENTIVE, player.getWorker().getMode());
    }

    @Test
    void alocarTrabalhador() {
        player.placeWorker(charcoal);
        assertSame(charcoal, player.getWorkerBuilding());
        assertSame(player.getWorker(), charcoal.getPerson());
        assertTrue(player.isPlanningComplete());
    }

    @Test
    void trocarDeEstabelecimentoLiberaOAnterior() {
        Building other = player.build(first(false));
        player.placeWorker(charcoal);
        player.placeWorker(other);
        assertFalse(charcoal.isOccupied());
        assertSame(other, player.getWorkerBuilding());
    }

    @Test
    void alternarModo() {
        player.getWorker().toggleMode();
        assertEquals(Worker.Mode.DISTRACTED, player.getWorker().getMode());
        assertEquals(1, player.getWorker().getMode().getGoods());
        assertEquals(1, player.getWorker().getMode().getMissingAllowed());
        player.getWorker().toggleMode();
        assertEquals(Worker.Mode.ATTENTIVE, player.getWorker().getMode());
    }

    @Test
    void naoAlocaEmGuilda() {
        Building guild = player.build(first(true));
        assertFalse(player.canPlaceWorker(guild));
        assertThrows(IllegalStateException.class, () -> player.placeWorker(guild));
    }

    @Test
    void naoAlocaOndeJaHaAssistente() {
        Assistant assistant = new Assistant(1, 2, 1, List.of());
        charcoal.setPerson(assistant);
        assertFalse(player.canPlaceWorker(charcoal));
        assertThrows(IllegalStateException.class, () -> player.placeWorker(charcoal));
    }

    @Test
    void naoAlocaEmEstabelecimentoDeOutro() {
        Building foreign = new Player().build(burners.get(1));
        assertFalse(player.canPlaceWorker(foreign));
    }

    @Test
    void escolherCartaParaConstruirSaiDaMao() {
        Card a = first(false);
        player.receive(a);
        player.planBuilding(a);
        assertSame(a, player.getPlannedBuilding());
        assertFalse(player.getHand().contains(a));
    }

    @Test
    void trocarCartaPlanejadaDevolveAAnterior() {
        Card a = cards.get(0);
        Card b = cards.get(1);
        player.receive(a);
        player.receive(b);
        player.planBuilding(a);
        player.planBuilding(b);
        assertSame(b, player.getPlannedBuilding());
        assertTrue(player.getHand().contains(a));
        assertFalse(player.getHand().contains(b));
    }

    @Test
    void cancelarDevolveParaAMao() {
        Card a = first(false);
        player.receive(a);
        player.planBuilding(a);
        assertSame(a, player.cancelPlannedBuilding());
        assertNull(player.getPlannedBuilding());
        assertTrue(player.getHand().contains(a));
        assertNull(player.cancelPlannedBuilding());
    }

    @Test
    void naoPlanejaCartaForaDaMao() {
        assertThrows(IllegalArgumentException.class, () -> player.planBuilding(first(false)));
    }
}
