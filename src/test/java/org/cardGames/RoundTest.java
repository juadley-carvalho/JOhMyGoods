package org.cardGames;

import org.cardGames.database.Database;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/** Rodada completa com oponentes (Bot): ordem do turno, jogador inicial, prioridade na contratação. */
class RoundTest {

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

    private static GameState newGame(int players, long seed) {
        return Setup.newGame(cards, burners, assistants, players, new Random(seed));
    }

    /** Mesa sem tela: compra (reembaralhando o descarte se preciso) e descarta direto no baralho. */
    static Bot.Table tableOf(Deck deck) {
        return new Bot.Table() {
            @Override
            public Card draw() {
                if (deck.needsReshuffle()) deck.reshuffle();
                return deck.draw();
            }

            @Override
            public void discard(Card card) {
                deck.discard(card);
            }
        };
    }

    private static void openRow(GameState state, Bot.Table table, boolean sunrise) {
        int suns = 0;
        while (suns < 2) {
            Card card = table.draw();
            if (card == null) break;
            if (sunrise) state.market().addSunrise(card); else state.market().addSunset(card);
            if (card.isSun()) suns++;
        }
    }

    /** Uma rodada inteira, todos os jogadores controlados pelo computador. Devolve os resumos da Fase IV. */
    static List<String> playRound(GameState state) {
        Bot.Table table = tableOf(state.deck());
        for (Player p : state.turnOrder()) {
            int n = 2 + p.newHandBonus();
            for (int i = 0; i < n; i++) {
                Card card = table.draw();
                if (card != null) p.receive(card);
            }
        }
        openRow(state, table, true);
        state.players().forEach(p -> Bot.plan(p, state.market()));
        openRow(state, table, false);
        List<String> summary = new ArrayList<>();
        for (Player p : state.turnOrder()) summary.add(Bot.playTurn(p, state, table));
        state.market().clear().forEach(table::discard);
        state.passStartingPlayer();
        return summary;
    }

    /** Todas as cartas do jogo, onde quer que estejam. */
    static int cardsOnTable(GameState state) {
        int total = state.deck().drawPileSize() + state.deck().discardSize() + state.market().getAll().size();
        for (Player p : state.players()) {
            total += p.getHand().size() + (p.getPlannedBuilding() == null ? 0 : 1);
            for (Building b : p.getBuildings()) total += 1 + b.goodsCount();
        }
        return total;
    }

    @Test
    void jogadorInicialAlternaACadaRodada() {
        GameState state = newGame(3, 1);
        List<Player> players = state.players();
        assertEquals(players, state.turnOrder());
        state.passStartingPlayer();
        assertEquals(List.of(players.get(1), players.get(2), players.get(0)), state.turnOrder());
        state.passStartingPlayer();
        state.passStartingPlayer();
        assertSame(players.getFirst(), state.startingPlayer());
    }

    @Test
    void planejamentoDoOponenteAlocaOTrabalhador() {
        GameState state = newGame(2, 3);
        Player bot = state.opponents().getFirst();
        Bot.plan(bot, state.market());
        assertTrue(bot.isPlanningComplete(), "a Carvoaria sempre aceita o trabalhador");
    }

    @Test
    void quemJogaPrimeiroTemPrioridadeNaContratacao() {
        GameState state = newGame(2, 5);
        state.availableAssistants().clear();
        Color charcoal = state.human().getCharcoalBurner().getCard().getColor(); // todas as Carvoarias têm a mesma cor
        Assistant only = new Assistant(99, 1, 3, List.of(charcoal));
        state.availableAssistants().add(only);
        state.passStartingPlayer(); // o oponente começa

        // Os dois têm carvões para pagar e a Carvoaria livre para o assistente
        Bot.Table table = tableOf(state.deck());
        for (Player p : state.turnOrder()) Bot.playTurn(p, state, table);

        Player first = state.opponents().getFirst();
        assertTrue(first.getAssistants().contains(only), "o jogador inicial contrata primeiro");
        assertTrue(state.human().getAssistants().isEmpty());
        assertTrue(state.availableAssistants().isEmpty());
    }

    @ParameterizedTest
    @ValueSource(ints = {2, 3, 4})
    void rodadasCompletasSemIntervencao(int players) {
        GameState state = newGame(players, 7 + players);
        int total = cardsOnTable(state);
        for (int round = 1; round <= 15; round++) {
            Player starting = state.startingPlayer();
            List<String> summary = playRound(state);
            assertEquals(players, summary.size());
            assertNotSame(starting, state.startingPlayer(), "o jogador inicial muda a cada rodada");
            assertEquals(total, cardsOnTable(state), "nenhuma carta some nem aparece (rodada " + round + ")");
            assertTrue(state.market().isEmpty());
            for (Player p : state.players()) {
                assertNull(p.getPlannedBuilding(), "a carta não construída volta para a mão");
                assertNull(p.getWorkerBuilding(), "o trabalhador sai ao fim da produção");
            }
        }
        assertTrue(state.players().stream().anyMatch(p -> p.getBuildings().size() > 1
                        || !p.getAssistants().isEmpty()),
                "em 15 rodadas alguém construiu ou contratou");
    }

    @Test
    void oponenteConstroiACartaPlanejadaQuandoTemBens() {
        GameState state = newGame(2, 11);
        Player bot = state.opponents().getFirst();
        Card cheap = state.deck().draw();
        while (cheap.getCost() <= 0 || cheap.getCost() > Setup.INITIAL_COAL) {
            state.deck().discard(cheap);
            cheap = state.deck().draw();
        }
        bot.receive(cheap);
        bot.planBuilding(cheap);
        int before = bot.getBuildings().size();
        String summary = Bot.playTurn(bot, state, tableOf(state.deck()));
        assertEquals(before + 1, bot.getBuildings().size(), summary);
        assertTrue(summary.contains("construiu " + cheap.getName()));
        assertNull(bot.getPlannedBuilding());
    }
}
