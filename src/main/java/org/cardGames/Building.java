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
    private boolean producedThisRound; // libera a cadeia de produção até o fim da produção

    public Building(Card card) {
        this.card = card;
    }

    public Card getCard() { return card; }

    public List<Card> getGoods() { return List.copyOf(goods); }
    public int goodsCount() { return goods.size(); }
    public void addGood(Card good) { goods.add(good); }

    /** Retira um bem específico (para levá-lo a uma cadeia de produção); false se não estiver aqui. */
    public boolean removeGood(Card good) { return goods.remove(good); }

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

    /** Se produziu pelo menos 1 bem nesta rodada (condição para usar a cadeia de produção). */
    public boolean hasProducedThisRound() { return producedThisRound; }
    public void setProducedThisRound(boolean produced) { this.producedThisRound = produced; }
}
