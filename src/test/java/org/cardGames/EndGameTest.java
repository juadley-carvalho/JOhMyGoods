package org.cardGames;

import org.cardGames.database.Database;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/** Fim de partida: gatilho dos 8 estabelecimentos, rodada final com cadeias liberadas e pontuação. */
class EndGameTest {

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

    /** Cartas do baralho com valor de bem, para servir de bens nos testes. */
    private static Card cardWith(GameState state, java.util.function.Predicate<Card> test) {
        Card card;
        while (!test.test(card = state.deck().draw())) state.deck().discard(card);
        return card;
    }

    // ------------------------------------------------------- pontuação

    @Test
    void pontuacaoSomaEstabelecimentosAssistentesEBens() {
        GameState state = newGame(2, 1);
        Player p = state.human();
        Card extra = cardWith(state, c -> c.getPoints() > 0);
        p.build(extra);
        Assistant a = new Assistant(99, 3, 4, List.of());
        p.hire(a);

        Scoring.Score score = Scoring.score(p);
        Building charcoal = p.getCharcoalBurner();
        assertEquals(charcoal.getCard().getPoints() + extra.getPoints(), score.buildingPoints());
        assertEquals(4, score.assistantPoints());
        assertEquals(charcoal.goodsValue(), score.coins());
        assertEquals(score.coins() / 5, score.goodsPoints());
        assertEquals(score.coins() % 5, score.leftoverCoins());
        assertEquals(score.buildingPoints() + 4 + score.goodsPoints(), score.total());
    }

    @Test
    void desempatePelaSobraDeMoedas() {
        Card charcoal = burners.getFirst();
        int value = charcoal.getGoodValue();
        Player a = new Player("A");
        Player b = new Player("B");
        Building ba = a.build(charcoal);
        Building bb = b.build(charcoal);
        // Mesmos pontos, mas B tem 1 moeda a mais sobrando: 5 bens x valor vs. 5 bens x valor + 1 bem (se não completar 5 moedas)
        for (int i = 0; i < 5; i++) { ba.addGood(cards.get(i)); bb.addGood(cards.get(10 + i)); }
        bb.addGood(cards.get(20));
        assertTrue(value < 5, "o carvão vale menos de 5 moedas");
        org.junit.jupiter.api.Assumptions.assumeTrue((5 * value) % 5 + value < 5);

        List<Scoring.Score> ranking = Scoring.ranking(List.of(a, b));
        assertEquals(ranking.get(0).total(), ranking.get(1).total());
        assertSame(b, ranking.getFirst().player(), "mais moedas restantes vence o empate");
        assertFalse(Scoring.isTie(ranking));
        assertTrue(Scoring.isTie(Scoring.ranking(List.of(a, a))));
    }

    // ------------------------------------------------------- gatilho e rodada final

    @Test
    void oitoEstabelecimentosDisparamUmaRodadaExtra() {
        GameState state = newGame(2, 3);
        Player p = state.human();
        for (int i = 0; i < GameState.BUILDINGS_TO_END - 1; i++) p.build(state.deck().draw());

        state.endRound(); // rodada em que chegou a 8
        assertTrue(state.isFinalRound());
        assertFalse(state.isGameOver());
        assertTrue(state.players().stream().allMatch(Player::areChainsUnlocked));

        state.endRound(); // rodada extra
        assertTrue(state.isGameOver());
    }

    @Test
    void semGatilhoAPartidaContinua() {
        GameState state = newGame(2, 4);
        state.endRound();
        assertFalse(state.isFinalRound());
        assertFalse(state.isGameOver());
    }

    @Test
    void naRodadaFinalACadeiaValeSemTerProduzido() {
        GameState state = newGame(2, 5);
        Player p = state.human();
        // Uma carta com cadeia cujos itens existem como recurso de outras cartas
        Card chained = cards.stream().filter(c -> !c.getChainResources().isEmpty()
                && c.getChainResources().stream().allMatch(r -> cards.stream().filter(x -> x.getResource() == r)
                        .count() >= c.getChainResources().size())).findFirst().orElseThrow();
        Building building = p.build(chained);
        List<Card> used = new java.util.ArrayList<>();
        for (Resource item : chained.getChainResources()) {
            Card card = cards.stream().filter(c -> c.getResource() == item && !used.contains(c)).findFirst().orElseThrow();
            used.add(card);
            p.receive(card);
        }
        assertTrue(p.runChain(building, List.copyOf(p.getHand())).isEmpty(), "fora da rodada final precisa produzir");

        p.setChainsUnlocked(true);
        List<Card> moved = p.runChain(building, List.copyOf(p.getHand()));
        assertEquals(chained.getChainResources().size(), moved.size());
        assertEquals(moved.size(), building.goodsCount());
    }

    @ParameterizedTest
    @ValueSource(ints = {2, 3, 4})
    void partidaCompletaChegaAoFim(int players) {
        GameState state = newGame(players, 20 + players);
        int total = RoundTest.cardsOnTable(state);
        int rounds = 0;
        boolean triggered = false;
        while (!state.isGameOver()) {
            assertTrue(++rounds < 200, "a partida deveria terminar");
            boolean finalRound = state.isFinalRound();
            playRound(state);
            if (!finalRound && state.isFinalRound()) {
                triggered = true;
                assertTrue(state.players().stream().anyMatch(p -> p.getBuildings().size() >= GameState.BUILDINGS_TO_END));
            }
            assertEquals(total, RoundTest.cardsOnTable(state));
        }
        assertTrue(triggered);
        List<Scoring.Score> ranking = Scoring.ranking(state.players());
        assertEquals(players, ranking.size());
        for (int i = 1; i < ranking.size(); i++) {
            assertTrue(ranking.get(i - 1).total() >= ranking.get(i).total());
        }
        TablePanel.ResultView view = MainWindow.resultView(ranking);
        assertEquals(players, view.rows().size());
        assertEquals(view.header().size(), view.rows().getFirst().size());
        assertFalse(view.winner().isBlank());
    }

    /** Rodada só com a IA, encerrada pelas regras de fim de partida (GameState.endRound). */
    private static void playRound(GameState state) {
        RoundTest.playRound(state); // já passa o jogador inicial; desfaz para endRound decidir
        int n = state.players().size();
        for (int i = 0; i < n - 1; i++) state.passStartingPlayer();
        state.endRound();
    }
}
