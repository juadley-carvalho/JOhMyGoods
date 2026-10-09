package org.cardGames;

/**
 * O trabalhador do jogador. Na Fase II ele vai para um estabelecimento,
 * atento (exige todos os recursos, produz 2 bens) ou distraído (1 recurso a menos, produz 1 bem).
 */
public final class Worker implements Person {

    public enum Mode {
        ATTENTIVE(2, 0),
        DISTRACTED(1, 1);

        private final int goods;
        private final int missingAllowed;

        Mode(int goods, int missingAllowed) {
            this.goods = goods;
            this.missingAllowed = missingAllowed;
        }

        /** Bens produzidos quando a produção é bem-sucedida. */
        public int getGoods() { return goods; }

        /** Quantos recursos podem faltar. */
        public int getMissingAllowed() { return missingAllowed; }

        public Mode toggle() { return this == ATTENTIVE ? DISTRACTED : ATTENTIVE; }
    }

    private Mode mode = Mode.ATTENTIVE;

    public Mode getMode() { return mode; }
    public void setMode(Mode mode) { this.mode = mode; }
    public void toggleMode() { mode = mode.toggle(); }
}
