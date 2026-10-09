package org.cardGames;

import java.util.ArrayList;
import java.util.List;

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
    public void setPlannedBuilding(Card card) { this.plannedBuilding = card; }
}
