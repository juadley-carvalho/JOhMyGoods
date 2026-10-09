package org.cardGames;

/** Quem pode ser alocado num estabelecimento: o trabalhador do jogador ou um assistente. */
public sealed interface Person permits Worker, Assistant {

    /** Bens produzidos quando a produção é bem-sucedida. */
    int goodsProduced();

    /** Quantas unidades de recurso podem faltar na produção. */
    int missingAllowed();
}
