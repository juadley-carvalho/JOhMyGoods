package org.cardGames;

import java.util.Comparator;
import java.util.List;

/**
 * Pontuação final (manual, fim de partida): pontos dos estabelecimentos + pontos dos assistentes
 * + 1 ponto a cada 5 moedas em bens restantes. Desempate: mais moedas que sobraram após a conversão.
 */
public final class Scoring {

    public static final int COINS_PER_POINT = 5;

    private Scoring() { }

    /** Detalhamento da pontuação de um jogador. */
    public record Score(Player player, int buildingPoints, int assistantPoints, int coins) {
        public int goodsPoints() { return coins / COINS_PER_POINT; }
        public int leftoverCoins() { return coins % COINS_PER_POINT; }
        public int total() { return buildingPoints + assistantPoints + goodsPoints(); }
    }

    public static Score score(Player player) {
        int buildings = player.getBuildings().stream().mapToInt(b -> b.getCard().getPoints()).sum();
        int assistants = player.getAssistants().stream().mapToInt(Assistant::getPoints).sum();
        int coins = player.getBuildings().stream().mapToInt(Building::goodsValue).sum();
        return new Score(player, buildings, assistants, coins);
    }

    /** Classificação: maior total primeiro; empatando, mais moedas restantes. */
    public static List<Score> ranking(List<Player> players) {
        return players.stream().map(Scoring::score)
                .sorted(Comparator.comparingInt(Score::total).thenComparingInt(Score::leftoverCoins).reversed())
                .toList();
    }

    /** Se os dois primeiros empatam também no desempate (vitória compartilhada). */
    public static boolean isTie(List<Score> ranking) {
        return ranking.size() > 1 && ranking.get(0).total() == ranking.get(1).total()
                && ranking.get(0).leftoverCoins() == ranking.get(1).leftoverCoins();
    }
}
