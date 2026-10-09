package org.cardGames;

/** Quem pode ser alocado num estabelecimento: o trabalhador do jogador ou um assistente. */
public sealed interface Person permits Worker, Assistant {
}
