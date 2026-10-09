package org.cardGames;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Mercado: fileira do Nascer do Sol (Fase II) e do Pôr do Sol (Fase III). Recursos compartilhados por todos. */
public class Market {

    private final List<Card> sunrise = new ArrayList<>();
    private final List<Card> sunset = new ArrayList<>();

    public void addSunrise(Card card) { sunrise.add(card); }
    public void addSunset(Card card) { sunset.add(card); }

    public List<Card> getSunrise() { return List.copyOf(sunrise); }
    public List<Card> getSunset() { return List.copyOf(sunset); }

    public List<Card> getAll() {
        List<Card> all = new ArrayList<>(sunrise);
        all.addAll(sunset);
        return all;
    }

    /** Quantidade de cada recurso disponível no mercado. */
    public Map<Resource, Integer> resourceCount() {
        Map<Resource, Integer> count = new EnumMap<>(Resource.class);
        for (Card card : getAll()) {
            if (card.getResource() != null) count.merge(card.getResource(), 1, Integer::sum);
        }
        return Collections.unmodifiableMap(count);
    }

    /** Esvazia as duas fileiras e devolve as cartas (para o descarte). */
    public List<Card> clear() {
        List<Card> all = getAll();
        sunrise.clear();
        sunset.clear();
        return all;
    }

    public boolean isEmpty() { return sunrise.isEmpty() && sunset.isEmpty(); }
}
