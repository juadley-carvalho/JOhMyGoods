# JOhMyGoods

Versão digital do jogo de cartas **Oh My Goods!** (Alexander Pfister), feita em Java.

No jogo, cada jogador administra uma pequena cadeia produtiva. Você junta recursos, constrói estabelecimentos, contrata assistentes e encadeia a produção de bens para somar mais pontos de vitória.

> Projeto de estudo, sem fins comerciais. *Oh My Goods!* © 2016 Lookout Spiele / Mayfair Games; versão brasileira © 2019 PaperGames.

## Status

**v1.0:** partida completa de 2 a 4 jogadores (você contra oponentes controlados pelo computador): as quatro fases da rodada, cadeias de produção, construção, assistentes, rodada final e pontuação.

## Tecnologias

| Item | Uso |
|---|---|
| Java 21+ | Linguagem (JDK 25 também funciona) |
| Swing | Interface gráfica e animações da mesa |
| SQLite (`sqlite-jdbc`) | Dados das cartas e dos assistentes |
| Maven | Build e dependências |

## Requisitos

- **JDK 21 ou superior**, com `JAVA_HOME` configurado e o `bin` do JDK no `PATH`.
- **Maven 3.9+**, ou o Maven embutido do IntelliJ IDEA.

Para conferir se está tudo instalado:

```bash
java -version
mvn -v
```

## Como executar

### Pelo IntelliJ IDEA (recomendado)

1. Abra a pasta do projeto. O IntelliJ reconhece o `pom.xml` sozinho.
2. Rode a classe `org.cardGames.Main`.

### Pela linha de comando

```bash
./mvnw exec:java
```

### Como `.jar` executável

```bash
./mvnw package
java -jar target/OhMyGoods.jar
```

O `.jar` já inclui o banco de cartas, as imagens e o driver do SQLite; basta ter Java 21+ instalado.

## Controles

| Tecla | Ação |
|---|---|
| `ESPAÇO` | Avança para a próxima etapa da rodada |
| `R` | Na Fase I, troca a mão inteira (opcional) |
| `C` | No planejamento, separa a carta selecionada para construir |
| `K` | Usa a cadeia de produção do estabelecimento da vez |
| `N` | Não produzir / não construir |
| `H` | Ajuda com as regras resumidas |

Clique nas cartas da mão para selecioná-las e nos estabelecimentos para alocar o trabalhador e os assistentes. A barra de status mostra a fase atual e as ações disponíveis; o título da janela mostra suas moedas em bens, pontos e cartas na mão.

O código está organizado em três camadas:

- **Modelo:** `Card`, `Deck`, `Player`, `Building`, `Assistant`, `Worker`, `Market`, `Resource`, `GameState`
- **Regras:** `Game`, `Production`, `Bot` (oponentes), `Scoring`, `Setup`
- **Interface:** `MainWindow`, `StartScreen`, `Help`, `TablePanel`, `CardSprite`, `Zone`

As regras não dependem da interface, então podem ser testadas sem abrir a janela.

## Documentação

- [Planejamento por fases](docs/PLANEJAMENTO.md)
- [Manual do jogo (PDF)](docs/oh_my_goods_manual.pdf)
- [Documentação técnica (DOCX)](docs/Documentação%20Técnica%20-%20JOhMyGoods.docx)
