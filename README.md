# JOhMyGoods

Versão digital do jogo de cartas **Oh My Goods!** (Alexander Pfister), feita em Java.

No jogo, cada jogador administra uma pequena cadeia produtiva. Você junta recursos, constrói estabelecimentos, contrata assistentes e encadeia a produção de bens para somar mais pontos de vitória.

> Projeto de estudo, sem fins comerciais. *Oh My Goods!* © 2016 Lookout Spiele / Mayfair Games; versão brasileira © 2019 PaperGames.

## Status

**v0.1.0:** as fases I a III da rodada já funcionam:

- Fase I: Nova Mão de Cartas
- Fase II: Nascer do Sol
- Fase III: Pôr do Sol

A fase IV (Produzir e Construir) ainda não foi implementada.

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
mvn compile
mvn exec:java -Dexec.mainClass=org.cardGames.Main
```

> Por enquanto, as imagens e o banco de dados são carregados por caminhos relativos (`src/main/...`). Por isso, o jogo precisa ser executado a partir da raiz do projeto. Isso será corrigido na Fase 0 do planejamento.

## Controles

| Tecla | Ação |
|---|---|
| `ESPAÇO` | Avança para a próxima etapa da rodada |
| `R` | Na Fase I, troca a mão inteira (opcional) |

A barra de status da janela mostra a fase atual e as ações disponíveis.

O código está organizado em três camadas:

- **Modelo:** `Card`, `Deck`, `Player`, `Resource`
- **Regras:** `Game`
- **Interface:** `MainWindow`, `TablePanel`, `CardSprite`, `Zone`

As regras não dependem da interface, então podem ser testadas sem abrir a janela.

## Documentação

- [Planejamento por fases](docs/PLANEJAMENTO.md)
- [Manual do jogo (PDF)](docs/oh_my_goods_manual.pdf)
- [Documentação técnica (DOCX)](docs/Documentação%20Técnica%20-%20JOhMyGoods.docx)
