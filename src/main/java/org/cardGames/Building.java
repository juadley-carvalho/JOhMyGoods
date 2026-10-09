package org.cardGames;

import java.util.ArrayList;
import java.util.List;

/**
 * Estabelecimento construído na área do jogador: a carta, os bens acumulados sobre ela
 * (cartas viradas para baixo) e a pessoa alocada nela, se houver (no máximo uma).
 */
public class Building {

    private final Card card;
    private final List<Card> goods = new ArrayList<>();
    private Person person;

    public Building(Card card) {
        this.card = card;
    }

    public Card getCard() { return card; }

    public List<Card> getGoods() { return List.copyOf(goods); }
    public int goodsCount() { return goods.size(); }
    public void addGood(Card good) { goods.add(good); }

    /** Retira um bem (para pagar uma construção); null se não houver. */
    public Card removeGood() {
        return goods.isEmpty() ? null : goods.removeLast();
    }

    /** Valor em moedas de todos os bens acumulados. */
    public int goodsValue() { return goods.size() * card.getGoodValue(); }

    public Person getPerson() { return person; }
    public boolean isOccupied() { return person != null; }

    public void setPerson(Person person) {
        if (person != null && this.person != null && this.person != person) {
            throw new IllegalStateException(card.getName() + " já tem alguém trabalhando");
        }
        this.person = person;
    }

    public void clearPerson() { this.person = null; }
}
