package org.cardGames;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Regras de produção (Fase IV, parte 1). Para iniciar a produção, um estabelecimento usa:
 * o mercado (compartilhado, não é consumido), as guildas de recurso do dono (+1 cada, também não consumidas)
 * e cartas da mão (descartadas; cada uma vale para um único estabelecimento).
 */
public final class Production {

    private Production() { }

    /** Resultado de uma produção: cartas da mão gastas e quantos bens foram gerados (0 se falhou). */
    public record Result(List<Card> usedCards, int goods) {
        public boolean succeeded() { return goods > 0; }
    }

    /** Recursos que o dono tem sem gastar cartas: mercado + guildas de recurso. */
    public static Map<Resource, Integer> freeResources(Player owner, Market market) {
        Map<Resource, Integer> free = new EnumMap<>(Resource.class);
        free.putAll(market.resourceCount());
        for (Building building : owner.getBuildings()) {
            Resource bonus = building.getCard().getGuildResource();
            if (bonus != null) free.merge(bonus, 1, Integer::sum);
        }
        return Collections.unmodifiableMap(free);
    }

    /**
     * O que falta para produzir, por recurso. Na Vidraçaria (recurso QUALQUER)
     * a falta aparece como QUALQUER, já que qualquer carta serve.
     */
    public static Map<Resource, Integer> missing(Card building, Map<Resource, Integer> available) {
        Map<Resource, Integer> missing = new EnumMap<>(Resource.class);
        for (Map.Entry<Resource, Integer> need : building.getRawResources().entrySet()) {
            int have = need.getKey() == Resource.QUALQUER
                    ? available.values().stream().mapToInt(Integer::intValue).sum()
                    : available.getOrDefault(need.getKey(), 0);
            if (have < need.getValue()) missing.put(need.getKey(), need.getValue() - have);
        }
        return missing;
    }

    /** Total de unidades faltando. */
    public static int shortfall(Card building, Map<Resource, Integer> available) {
        return missing(building, available).values().stream().mapToInt(Integer::intValue).sum();
    }

    /** Se os recursos livres + as cartas oferecidas bastam, aceitando até missingAllowed unidades a menos. */
    public static boolean canProduce(Card building, Map<Resource, Integer> free, List<Card> handCards, int missingAllowed) {
        return building.isProducer() && shortfall(building, plus(free, handCards)) <= missingAllowed;
    }

    /**
     * Das cartas oferecidas, só as que realmente ajudam (para não descartar à toa).
     * Se mesmo com todas não der para produzir, devolve lista vazia.
     */
    public static List<Card> cardsToUse(Card building, Map<Resource, Integer> free, List<Card> offered, int missingAllowed) {
        if (!canProduce(building, free, offered, missingAllowed)) return List.of();
        List<Card> used = new ArrayList<>();
        for (Card card : offered) {
            Map<Resource, Integer> available = plus(free, used);
            if (shortfall(building, available) <= missingAllowed) break;
            if (helps(building, available, card)) used.add(card);
        }
        return List.copyOf(used);
    }

    /** Quantos bens o estabelecimento produz com essas cartas (0 se não puder produzir). */
    public static int goods(Building building, Map<Resource, Integer> free, List<Card> handCards) {
        Person person = building.getPerson();
        if (person == null) return 0;
        return canProduce(building.getCard(), free, handCards, person.missingAllowed()) ? person.goodsProduced() : 0;
    }

    /**
     * Previsão da produção no planejamento: free = moedas dos estabelecimentos ocupados que produzem só com
     * o mercado e as guildas; withHand = moedas dos que as cartas da mão completam (used, gastas na ordem da
     * fila de produção); handLeft = o que sobra na mão. A 2ª fileira do mercado, ainda fechada, não entra.
     */
    public record Forecast(int free, int withHand, List<Card> used, List<Card> handLeft) { }

    public static Forecast forecast(Player owner, Market market) {
        Map<Resource, Integer> free = freeResources(owner, market);
        int freeCoins = 0;
        int handCoins = 0;
        List<Card> left = new ArrayList<>(owner.getHand());
        List<Card> used = new ArrayList<>();
        for (Building building : owner.producingBuildings()) {
            int value = building.getCard().getGoodValue();
            int goods = goods(building, free, List.of());
            if (goods > 0) {
                freeCoins += goods * value;
                continue;
            }
            List<Card> cards = cardsToUse(building.getCard(), free, left, building.getPerson().missingAllowed());
            goods = goods(building, free, cards);
            if (goods == 0) continue;
            handCoins += goods * value;
            cards.forEach(left::remove);
            used.addAll(cards);
        }
        return new Forecast(freeCoins, handCoins, List.copyOf(used), List.copyOf(left));
    }

    // ------------------------------------------------------- cadeia de produção (manual p. 8)

    /**
     * Cartas para uma execução da cadeia do estabelecimento (que precisa ter produzido nesta rodada,
     * exceto na rodada final): para cada item da cadeia, uma carta da mão
     * entre as oferecidas (recurso igual ao item) ou, na falta, um bem de outro estabelecimento do dono
     * (produto igual ao item). Mercado e guildas não valem. Lista vazia se a cadeia não puder ser feita.
     * Cada carta devolvida vira 1 bem no estabelecimento (cadeia de 2 itens: 2 bens por vez).
     */
    public static List<Card> chainItems(Player owner, Building target, List<Card> offered) {
        return chainItems(owner, target, offered, Map.of());
    }

    /** Como chainItems, tirando cada bem primeiro do estabelecimento escolhido em sources (item -> origem). */
    public static List<Card> chainItems(Player owner, Building target, List<Card> offered, Map<Resource, Building> sources) {
        List<Resource> chain = target.getCard().getChainResources();
        if (chain.isEmpty() || !target.hasProducedThisRound() && !owner.areChainsUnlocked()) return List.of();
        List<Card> hand = new ArrayList<>(offered);
        List<Card> items = new ArrayList<>();
        for (Resource item : chain) {
            Card card = hand.stream().filter(c -> c.getResource() == item).findFirst()
                    .orElseGet(() -> goodFrom(owner, target, item, items, sources.get(item)));
            if (card == null) return List.of();
            hand.remove(card);
            items.add(card);
        }
        return List.copyOf(items);
    }

    /** Itens da cadeia que as cartas oferecidas não cobrem: terão de sair dos bens de outros estabelecimentos. */
    public static List<Resource> itemsFromGoods(Building target, List<Card> offered) {
        List<Card> hand = new ArrayList<>(offered);
        List<Resource> fromGoods = new ArrayList<>();
        for (Resource item : target.getCard().getChainResources()) {
            Card card = hand.stream().filter(c -> c.getResource() == item).findFirst().orElse(null);
            if (card == null) fromGoods.add(item); else hand.remove(card);
        }
        return fromGoods;
    }

    /** Outros estabelecimentos do dono com bens do item: de onde a cadeia pode tirá-lo. */
    public static List<Building> goodSources(Player owner, Building target, Resource item) {
        return owner.getBuildings().stream()
                .filter(b -> b != target && b.getCard().getProduct() == item && b.goodsCount() > 0).toList();
    }

    /** Um bem do item, em outro estabelecimento do dono (o preferido primeiro), que ainda não foi escolhido. */
    private static Card goodFrom(Player owner, Building target, Resource item, List<Card> taken, Building preferred) {
        List<Building> sources = new ArrayList<>(goodSources(owner, target, item));
        if (sources.remove(preferred)) sources.addFirst(preferred);
        for (Building source : sources) {
            for (Card good : source.getGoods().reversed()) {
                if (!taken.contains(good)) return good;
            }
        }
        return null;
    }

    private static boolean helps(Card building, Map<Resource, Integer> available, Card card) {
        Resource resource = card.getResource();
        if (resource == null) return false;
        Map<Resource, Integer> missing = missing(building, available);
        return missing.containsKey(resource) || missing.containsKey(Resource.QUALQUER);
    }

    private static Map<Resource, Integer> plus(Map<Resource, Integer> free, List<Card> cards) {
        Map<Resource, Integer> total = new EnumMap<>(Resource.class);
        total.putAll(free);
        for (Card card : cards) {
            if (card.getResource() != null) total.merge(card.getResource(), 1, Integer::sum);
        }
        return total;
    }
}
