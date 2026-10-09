package org.cardGames;

import java.util.ArrayList;
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

    /** Se o pagamento cobre o custo da carta planejada com bens que o jogador realmente tem. */
    public boolean canBuild(Map<Building, Integer> payment) {
        if (plannedBuilding == null) return false;
        for (Map.Entry<Building, Integer> e : payment.entrySet()) {
            if (!buildings.contains(e.getKey()) || e.getValue() < 0 || e.getValue() > e.getKey().goodsCount()) {
                return false;
            }
        }
        return paymentValue(payment) >= plannedBuilding.getCost();
    }

    /**
     * Constrói a carta planejada pagando com bens (valor somado >= custo, sem troco).
     * Devolve os bens pagos (quem chama os descarta); o novo estabelecimento produz a partir da próxima rodada.
     */
    public List<Card> buildPlanned(Map<Building, Integer> payment) {
        if (!canBuild(payment)) {
            throw new IllegalStateException("pagamento insuficiente para " + plannedBuilding);
        }
        List<Card> paid = new ArrayList<>();
        payment.forEach((building, n) -> {
            for (int i = 0; i < n; i++) paid.add(building.removeGood());
        });
        build(plannedBuilding);
        plannedBuilding = null;
        return paid;
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
