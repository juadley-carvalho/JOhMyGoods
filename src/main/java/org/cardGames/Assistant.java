package org.cardGames;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Assistente (tabela AJUDANTE): custa moedas, vale pontos e só pode ser contratado
 * por quem possui estabelecimentos das cores exigidas. Sempre produz 1 bem e exige 100% dos recursos.
 */
public final class Assistant implements Person {

    public static final int GOODS = 1;

    private final int number;
    private final int cost;
    private final int points;
    private final List<Color> requiredColors;

    public Assistant(int number, int cost, int points, List<Color> requiredColors) {
        this.number = number;
        this.cost = cost;
        this.points = points;
        this.requiredColors = List.copyOf(requiredColors);
    }

    public int getNumber() { return number; }
    public int getCost() { return cost; }
    public int getPoints() { return points; }
    public List<Color> getRequiredColors() { return requiredColors; }

    /** Cores exigidas com a quantidade de estabelecimentos de cada uma. */
    public Map<Color, Integer> getRequiredColorCount() {
        Map<Color, Integer> count = new EnumMap<>(Color.class);
        requiredColors.forEach(c -> count.merge(c, 1, Integer::sum));
        return count;
    }

    @Override
    public String toString() {
        return "Assistente #" + number;
    }
}
