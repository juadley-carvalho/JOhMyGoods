package org.cardGames;

public enum Resource {

    ALIMENTO(8),
    ARGILA(0),
    ARMA(4),
    BARRIL(5),
    CARNE(7),
    CARVAO(1),
    COURO(6),
    FARINHA(2),
    FERRAMENTA(6),
    FERRO(3),
    FORNO(5),
    GADO(3),
    LA(0),
    MADEIRA(0),
    PAO(4),
    PEDRA(0),
    ROUPA(4),
    SAPATO(8),
    TABUA(2),
    TECIDO(3),
    TIJOLO(2),
    TRIGO(0),
    VIDRO(4),
    VITRAL(5),
    QUALQUER(0);


    private final int value;

    Resource(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    public static Resource fromString(String name) {
        return (name == null) ? null : Resource.valueOf(name.trim().toUpperCase());
    }
}
