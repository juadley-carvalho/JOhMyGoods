package org.cardGames;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Tudo o que pertence a um jogador: mão, estabelecimentos, trabalhador, assistentes e a carta a construir. */
public class Player {

    private final String name;
    private final List<Card> hand = new ArrayList<>();
    private final List<Building> buildings = new ArrayList<>();
    private final Worker worker = new Worker();
    private final List<Assistant> assistants = new ArrayList<>();
    private Card plannedBuilding;

    public Player() {
        this("Jogador");
    }

    public Player(String name) {
        this.name = name;
    }

    public String getName() { return name; }

    public List<Card> getHand() {
        return hand;
    }

    public void receive(Card card) {
        hand.add(card);
    }

    /** Esvazia a mão e devolve as cartas que estavam nela. */
    public List<Card> takeHand() {
        List<Card> all = new ArrayList<>(hand);
        hand.clear();
        return all;
    }

    public List<Building> getBuildings() { return List.copyOf(buildings); }

    public Building build(Card card) {
        Building building = new Building(card);
        buildings.add(building);
        return building;
    }

    /** A Carvoaria inicial (sempre o primeiro estabelecimento). */
    public Building getCharcoalBurner() { return buildings.getFirst(); }

    public Worker getWorker() { return worker; }

    public List<Assistant> getAssistants() { return List.copyOf(assistants); }
    public void hire(Assistant assistant) { assistants.add(assistant); }

    /** Custo em moedas para mover um assistente na fase de planejamento. */
    public static final int MOVE_ASSISTANT_COST = 2;

    /** Carta escolhida na Fase II para construir (virada para baixo), ou null. */
    public Card getPlannedBuilding() { return plannedBuilding; }

    // ------------------------------------------------------- planejamento (Fase II)

    /** Estabelecimento onde o trabalhador está, ou null. */
    public Building getWorkerBuilding() {
        return buildings.stream().filter(b -> b.getPerson() == worker).findFirst().orElse(null);
    }

    /** Se o estabelecimento pode receber o trabalhador: é do jogador, produz e não tem outra pessoa. */
    public boolean canPlaceWorker(Building building) {
        return buildings.contains(building)
                && building.getCard().isProducer()
                && (!building.isOccupied() || building.getPerson() == worker);
    }

    /** Coloca o trabalhador no estabelecimento, tirando-o de onde estava. */
    public void placeWorker(Building building) {
        if (!canPlaceWorker(building)) {
            throw new IllegalStateException("o trabalhador não pode ir para " + building.getCard().getName());
        }
        removeWorker();
        building.setPerson(worker);
    }

    public void removeWorker() {
        Building current = getWorkerBuilding();
        if (current != null) current.clearPerson();
    }

    /**
     * Separa uma carta da mão para construir (fica virada para baixo).
     * Se já havia outra escolhida, ela volta para a mão.
     */
    public void planBuilding(Card card) {
        if (!hand.contains(card)) {
            throw new IllegalArgumentException(card + " não está na mão");
        }
        cancelPlannedBuilding();
        hand.remove(card);
        plannedBuilding = card;
    }

    /** Devolve a carta a construir para a mão; retorna a carta devolvida, ou null. */
    public Card cancelPlannedBuilding() {
        Card card = plannedBuilding;
        if (card != null) {
            hand.add(card);
            plannedBuilding = null;
        }
        return card;
    }

    // ------------------------------------------------------- produção (Fase IV)

    /**
     * Tenta produzir no estabelecimento com a pessoa alocada nele, oferecendo cartas da mão.
     * Só as cartas necessárias saem da mão (quem chama as descarta); os bens não são colocados aqui:
     * quem chama compra {@code result.goods()} cartas e as põe sobre o estabelecimento.
     */
    public Production.Result produce(Building building, Market market, List<Card> offered) {
        if (!buildings.contains(building) || !building.isOccupied()) {
            throw new IllegalStateException("ninguém produz em " + building.getCard().getName());
        }
        if (!hand.containsAll(offered)) {
            throw new IllegalArgumentException("só cartas da mão podem ser usadas na produção");
        }
        Map<Resource, Integer> free = Production.freeResources(this, market);
        Person person = building.getPerson();
        List<Card> used = Production.cardsToUse(building.getCard(), free, offered, person.missingAllowed());
        int goods = Production.goods(building, free, used);
        if (goods == 0) return new Production.Result(List.of(), 0);
        hand.removeAll(used);
        building.setProducedThisRound(true);
        return new Production.Result(used, goods);
    }

    /**
     * Executa uma vez a cadeia de produção do estabelecimento (só se ele produziu nesta rodada),
     * usando as cartas oferecidas da mão e, na falta, bens de outros estabelecimentos.
     * As cartas usadas saem da mão/estabelecimento de origem e viram bens sobre o estabelecimento.
     * Devolve as cartas movidas (vazio se não deu).
     */
    public List<Card> runChain(Building building, List<Card> offered) {
        if (!buildings.contains(building)) {
            throw new IllegalArgumentException(building.getCard().getName() + " não é do jogador");
        }
        if (!hand.containsAll(offered)) {
            throw new IllegalArgumentException("só cartas da mão podem ser usadas na cadeia");
        }
        List<Card> items = Production.chainItems(this, building, offered);
        for (Card card : items) {
            if (!hand.remove(card)) {
                buildings.stream().filter(b -> b.removeGood(card)).findFirst();
            }
            building.addGood(card);
        }
        return items;
    }

    /** Fim da produção: o trabalhador volta (atento para a próxima rodada); assistentes permanecem. */
    public void finishProduction() {
        removeWorker();
        worker.setMode(Worker.Mode.ATTENTIVE);
        buildings.forEach(b -> b.setProducedThisRound(false));
    }

    // ------------------------------------------------------- construção (Fase IV)

    /** Valor em moedas de um pagamento: quantos bens sairão de cada estabelecimento. */
    public static int paymentValue(Map<Building, Integer> payment) {
        return payment.entrySet().stream()
                .mapToInt(e -> e.getValue() * e.getKey().getCard().getGoodValue()).sum();
    }

    /** Se o pagamento usa só bens que o jogador realmente tem e vale pelo menos amount moedas. */
    public boolean covers(Map<Building, Integer> payment, int amount) {
        for (Map.Entry<Building, Integer> e : payment.entrySet()) {
            if (!buildings.contains(e.getKey()) || e.getValue() < 0 || e.getValue() > e.getKey().goodsCount()) {
                return false;
            }
        }
        return paymentValue(payment) >= amount;
    }

    /** Se o pagamento cobre o custo da carta planejada com bens que o jogador realmente tem. */
    public boolean canBuild(Map<Building, Integer> payment) {
        return plannedBuilding != null && covers(payment, plannedBuilding.getCost());
    }

    /** Retira os bens do pagamento; devolve as cartas (quem chama as descarta). */
    private List<Card> pay(Map<Building, Integer> payment) {
        List<Card> paid = new ArrayList<>();
        payment.forEach((building, n) -> {
            for (int i = 0; i < n; i++) paid.add(building.removeGood());
        });
        return paid;
    }

    /**
     * Pagamento escolhido pelo jogo para amount moedas: o que paga menos acima do valor
     * (e, empatando, com menos bens). Vazio se os bens não bastam.
     */
    public Map<Building, Integer> cheapestPayment(int amount) {
        List<Building> sources = buildings.stream()
                .filter(b -> b.goodsCount() > 0 && b.getCard().getGoodValue() > 0).toList();
        Map<Building, Integer> best = new LinkedHashMap<>();
        int[] bestScore = {Integer.MAX_VALUE, Integer.MAX_VALUE};
        search(sources, 0, amount, new LinkedHashMap<>(), best, bestScore);
        return best;
    }

    private static void search(List<Building> sources, int i, int amount, Map<Building, Integer> current,
                               Map<Building, Integer> best, int[] bestScore) {
        int value = paymentValue(current);
        if (value >= amount || i == sources.size()) {
            int cards = current.values().stream().mapToInt(Integer::intValue).sum();
            if (value >= amount && (value < bestScore[0] || value == bestScore[0] && cards < bestScore[1])) {
                bestScore[0] = value;
                bestScore[1] = cards;
                best.clear();
                best.putAll(current);
            }
            return;
        }
        Building b = sources.get(i);
        int v = b.getCard().getGoodValue();
        int max = Math.min(b.goodsCount(), (amount - value + v - 1) / v);
        for (int n = 0; n <= max; n++) {
            if (n > 0) current.put(b, n); else current.remove(b);
            search(sources, i + 1, amount, current, best, bestScore);
        }
        current.remove(b);
    }

    /**
     * Constrói a carta planejada pagando com bens (valor somado >= custo, sem troco).
     * Devolve os bens pagos (quem chama os descarta); o novo estabelecimento produz a partir da próxima rodada.
     */
    public List<Card> buildPlanned(Map<Building, Integer> payment) {
        if (!canBuild(payment)) {
            throw new IllegalStateException("pagamento insuficiente para " + plannedBuilding);
        }
        List<Card> paid = pay(payment);
        build(plannedBuilding);
        plannedBuilding = null;
        return paid;
    }

    // ------------------------------------------------------- assistentes

    /** Estabelecimento onde o assistente está, ou null. */
    public Building getAssistantBuilding(Assistant assistant) {
        return buildings.stream().filter(b -> b.getPerson() == assistant).findFirst().orElse(null);
    }

    /** Se o jogador tem estabelecimentos de todas as cores exigidas pelo assistente (com repetição). */
    public boolean hasColorsFor(Assistant assistant) {
        for (Map.Entry<Color, Integer> need : assistant.getRequiredColorCount().entrySet()) {
            long have = buildings.stream().filter(b -> b.getCard().getColor() == need.getKey()).count();
            if (have < need.getValue()) return false;
        }
        return true;
    }

    /** Se o estabelecimento pode receber o assistente: é do jogador, produz e está livre (ou já é dele). */
    public boolean canPlaceAssistant(Assistant assistant, Building building) {
        return buildings.contains(building)
                && building.getCard().isProducer()
                && (!building.isOccupied() || building.getPerson() == assistant);
    }

    /** Se há algum estabelecimento livre que possa receber um assistente. */
    public boolean hasFreeBuilding() {
        return buildings.stream().anyMatch(b -> b.getCard().isProducer() && !b.isOccupied());
    }

    /** Contratar (em vez de construir): cores exigidas, pagamento >= custo e um estabelecimento livre para ele. */
    public boolean canHire(Assistant assistant, Map<Building, Integer> payment) {
        return !assistants.contains(assistant)
                && hasColorsFor(assistant)
                && hasFreeBuilding()
                && covers(payment, assistant.getCost());
    }

    /**
     * Contrata o assistente pagando com bens e o aloca imediatamente no estabelecimento livre.
     * Devolve os bens pagos (quem chama os descarta).
     */
    public List<Card> hireAssistant(Assistant assistant, Map<Building, Integer> payment, Building target) {
        if (!canHire(assistant, payment)) {
            throw new IllegalStateException("não é possível contratar " + assistant);
        }
        if (!canPlaceAssistant(assistant, target)) {
            throw new IllegalStateException(assistant + " não pode ir para " + target.getCard().getName());
        }
        List<Card> paid = pay(payment);
        assistants.add(assistant);
        target.setPerson(assistant);
        return paid;
    }

    /** Mover um assistente (Fase II) custa 2 moedas em bens. */
    public boolean canMoveAssistant(Assistant assistant, Building target, Map<Building, Integer> payment) {
        return assistants.contains(assistant)
                && getAssistantBuilding(assistant) != target
                && canPlaceAssistant(assistant, target)
                && covers(payment, MOVE_ASSISTANT_COST);
    }

    /** Move o assistente para outro estabelecimento livre, pagando 2 moedas; devolve os bens pagos. */
    public List<Card> moveAssistant(Assistant assistant, Building target, Map<Building, Integer> payment) {
        if (!canMoveAssistant(assistant, target, payment)) {
            throw new IllegalStateException(assistant + " não pode ir para " + target.getCard().getName());
        }
        List<Card> paid = pay(payment);
        Building current = getAssistantBuilding(assistant);
        if (current != null) current.clearPerson();
        target.setPerson(assistant);
        return paid;
    }

    /** Estabelecimentos com alguém para produzir: o do trabalhador primeiro, depois os dos assistentes. */
    public List<Building> producingBuildings() {
        List<Building> list = new ArrayList<>();
        Building working = getWorkerBuilding();
        if (working != null) list.add(working);
        buildings.stream().filter(b -> b.getPerson() instanceof Assistant).forEach(list::add);
        return list;
    }

    // ------------------------------------------------------- nova mão (Fase I) e exaustão

    /** Cartas extras na Fase I: +1 por guilda de carta, se o jogador tem no máximo 3 cartas no início da fase. */
    public int newHandBonus() {
        if (hand.size() > 3) return 0;
        return (int) buildings.stream().filter(b -> b.getCard().isCardGuild()).count();
    }

    /** Regra de exaustão: descarta metade da mão (arredondada para baixo). Devolve as cartas descartadas. */
    public List<Card> discardHalf() {
        List<Card> discarded = new ArrayList<>(hand.subList(0, hand.size() / 2));
        hand.removeAll(discarded);
        return discarded;
    }

    /** O planejamento só termina com o trabalhador alocado (a construção é opcional). */
    public boolean isPlanningComplete() {
        return getWorkerBuilding() != null;
    }
}
