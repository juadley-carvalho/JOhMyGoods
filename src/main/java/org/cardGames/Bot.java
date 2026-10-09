package org.cardGames;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Oponente controlado pelo computador: decide com heurísticas simples, usando as mesmas
 * regras de {@link Player} que o jogador humano. Não troca a mão na Fase I nem move assistentes.
 */
public final class Bot {

    private Bot() { }

    /** Onde as cartas saem e entram na mesa: quem chama decide (e anima, se houver tela). */
    public interface Table {
        /** Compra uma carta (bem produzido); null se não houver. */
        Card draw();

        /** Descarta uma carta (recurso gasto, bem usado no pagamento). */
        void discard(Card card);

        /** Um passo da vez do oponente acabou (ex.: "Jogador 2 · PADARIA: produziu 2 bens"), para mostrar na tela. */
        default void step(String text) { }

        /** Como step, com o resumo da vez até este passo (ex.: "Jogador 2: 3 bens até agora"). */
        default void step(String text, String summary) { step(text); }
    }

    // ------------------------------------------------------- Fase II: planejamento

    /**
     * Planejamento (só conhece a 1ª fileira do mercado): separa a carta a construir e
     * aloca o trabalhador onde ele rende mais bens, atento ou distraído.
     */
    public static void plan(Player player, Market market) {
        planBuilding(player);
        placeWorker(player, market);
    }

    /**
     * Carta a construir: a que vale mais pontos (empatando, a mais barata) entre as que
     * os bens atuais pagam, com uma folga de 2 moedas para a produção da rodada.
     */
    static void planBuilding(Player player) {
        int budget = budget(player);
        player.getHand().stream()
                .filter(c -> c.getCost() > 0 && c.getCost() <= budget)
                .max(Comparator.comparingInt(Card::getPoints).thenComparing(Card::getCost, Comparator.reverseOrder()))
                .ifPresent(player::planBuilding);
    }

    /** Moedas em bens, com uma folga de 2 para a produção da rodada. */
    private static int budget(Player player) {
        return player.getBuildings().stream().mapToInt(Building::goodsValue).sum() + 2;
    }

    /** Trabalhador: estabelecimento com mais moedas produzidas; atento se nada falta, distraído se falta 1. */
    static void placeWorker(Player player, Market market) {
        Map<Resource, Integer> free = Production.freeResources(player, market);
        Building best = null;
        Worker.Mode bestMode = Worker.Mode.ATTENTIVE;
        int bestScore = -1;
        for (Building building : player.getBuildings()) {
            if (!player.canPlaceWorker(building)) continue;
            int shortfall = Production.shortfall(building.getCard(), withHand(free, player.getHand()));
            Worker.Mode mode = shortfall == 0 ? Worker.Mode.ATTENTIVE
                    : shortfall == 1 ? Worker.Mode.DISTRACTED : Worker.Mode.ATTENTIVE;
            int score = shortfall <= 1 ? mode.getGoods() * building.getCard().getGoodValue() : 0;
            if (score > bestScore) {
                best = building;
                bestMode = mode;
                bestScore = score;
            }
        }
        if (best == null) return;
        player.placeWorker(best);
        player.getWorker().setMode(bestMode);
    }

    // ------------------------------------------------------- Fase IV: a vez do oponente

    /**
     * A vez do oponente na Fase IV: produz em cada estabelecimento ocupado (oferecendo a mão inteira;
     * só as cartas necessárias são gastas), usa as cadeias enquanto der e depois constrói a carta
     * planejada ou, se não der, contrata um assistente. Devolve um resumo do que foi feito.
     */
    public static String playTurn(Player player, GameState state, Table table) {
        Market market = state.market();
        int[] produced = {0};
        for (Building building : player.producingBuildings()) {
            Production.Result result = player.produce(building, market, List.copyOf(player.getHand()));
            result.usedCards().forEach(table::discard);
            int goods = 0;
            for (int i = 0; i < result.goods(); i++) {
                Card good = table.draw();
                if (good == null) break;
                building.addGood(good);
                goods++;
            }
            produced[0] += goods;
            table.step(where(player, building) + (result.succeeded() ? "produziu " + goods(goods) : "não produziu"),
                    soFar(player, produced[0]));
            if (result.succeeded()) runChains(player, building, table, produced);
        }
        if (player.areChainsUnlocked()) { // rodada final: cadeias em todos os estabelecimentos
            for (Building building : player.getBuildings()) runChains(player, building, table, produced);
        }
        player.finishProduction();

        List<String> summary = new ArrayList<>();
        summary.add(goods(produced[0]));
        String built = build(player, table);
        if (built == null) built = hire(player, state, table);
        if (built != null) summary.add(built);
        String text = player.getName() + ": " + String.join(", ", summary);
        table.step(player.getName() + " " + (built != null ? built : "não construiu nem contratou"), text);
        player.cancelPlannedBuilding();
        return text;
    }

    private static String where(Player player, Building building) {
        return player.getName() + " · " + building.getCard().getName() + ": ";
    }

    private static String soFar(Player player, int produced) {
        return player.getName() + ": " + goods(produced) + " até agora";
    }

    private static String goods(int n) {
        return n + (n == 1 ? " bem" : " bens");
    }

    /** Executa a cadeia de produção enquanto houver itens; soma os bens gerados em produced[0]. */
    private static void runChains(Player player, Building building, Table table, int[] produced) {
        int goods = 0;
        List<Card> moved;
        while (!(moved = player.runChain(building, List.copyOf(player.getHand()))).isEmpty()) {
            goods += moved.size();
        }
        produced[0] += goods;
        if (goods > 0) table.step(where(player, building) + "cadeia de produção, +" + goods(goods), soFar(player, produced[0]));
    }

    private static String build(Player player, Table table) {
        Card planned = player.getPlannedBuilding();
        if (planned == null) return null;
        Map<Building, Integer> payment = player.cheapestPayment(planned.getCost());
        if (!player.canBuild(payment)) return null;
        player.buildPlanned(payment).forEach(table::discard);
        return "construiu " + planned.getName();
    }

    /**
     * Contrata o assistente de mais pontos que puder pagar e o aloca no estabelecimento livre
     * de bem mais valioso. Quem contrata antes (ordem do turno) tem prioridade.
     */
    private static String hire(Player player, GameState state, Table table) {
        for (Assistant assistant : state.availableAssistants().stream()
                .sorted(Comparator.comparingInt(Assistant::getPoints).reversed()).toList()) {
            Map<Building, Integer> payment = player.cheapestPayment(assistant.getCost());
            if (!player.canHire(assistant, payment)) continue;
            Building target = player.getBuildings().stream()
                    .filter(b -> player.canPlaceAssistant(assistant, b) && !b.isOccupied())
                    .max(Comparator.comparingInt(b -> b.getCard().getGoodValue())).orElseThrow();
            player.hireAssistant(assistant, payment, target).forEach(table::discard);
            state.availableAssistants().remove(assistant);
            return "contratou " + assistant;
        }
        return null;
    }

    // ------------------------------------------------------- exaustão

    /**
     * Regra de exaustão: descarta metade da mão, ficando com as cartas mais úteis (empatando,
     * descarta as primeiras). Devolve as cartas descartadas.
     */
    public static List<Card> discardForExhaustion(Player player) {
        int budget = budget(player);
        List<Card> worst = player.getHand().stream()
                .sorted(Comparator.comparingInt(c -> usefulness(player, c, budget)))
                .limit(player.exhaustionDiscards()).toList();
        return player.discardChosen(worst);
    }

    /** Utilidade da carta: quanto os estabelecimentos usam o recurso dela (produção e cadeia) e se já dá para construí-la. */
    static int usefulness(Player player, Card card, int budget) {
        int score = 0;
        Resource resource = card.getResource();
        if (resource != null) {
            for (Building building : player.getBuildings()) {
                score += building.getCard().getRawResources().getOrDefault(resource, 0);
                if (building.getCard().getChainResources().contains(resource)) score++;
            }
        }
        if (card.getCost() > 0 && card.getCost() <= budget) score += card.getPoints() + 1;
        return score;
    }

    private static Map<Resource, Integer> withHand(Map<Resource, Integer> free, List<Card> hand) {
        Map<Resource, Integer> total = new java.util.EnumMap<>(Resource.class);
        total.putAll(free);
        hand.forEach(c -> { if (c.getResource() != null) total.merge(c.getResource(), 1, Integer::sum); });
        return total;
    }
}
