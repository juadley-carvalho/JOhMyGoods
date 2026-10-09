# Planejamento por Fases — JOhMyGoods

Implementação digital do jogo de cartas **Oh My Goods!** (Alexander Pfister) em **Java 21 + Swing**, com as cartas em **SQLite**.

Fontes: `docs/Documentação Técnica - JOhMyGoods.docx` e `docs/oh_my_goods_manual.pdf`.

> **Divergência:** a Documentação Técnica cita *C++/Qt*, mas o projeto real é Java/Swing/Maven. Este plano segue o código existente (Java).

Cada fase termina com um **critério de validação**. Só avançamos depois que ele for verificado.

---

## Como o jogo está organizado (visão rápida)

Um jogo de cartas digital é uma **máquina de estados**: o jogo está sempre em uma fase (Nova Mão, Nascer do Sol, ...), e cada ação do jogador (tecla, clique) aplica uma regra e muda o estado. A tela só **desenha** esse estado.

| Camada | Responsabilidade | Classes atuais |
|---|---|---|
| **Modelo** (dados) | Cartas, baralho, jogador, mercado | `Card`, `Deck`, `Player`, `Resource`, `Color` |
| **Regras** (controlador) | Fases, validações, pontuação | `Game` |
| **Interface** (visão) | Desenhar a mesa, animar, receber cliques | `MainWindow`, `TablePanel`, `CardSprite`, `Zone` |
| **Dados** | Ler as cartas do banco | `Database`, `Setup`, `ohmygoods.db` |

Regra de ouro: **as regras não dependem da tela**. Assim conseguimos testar a lógica com testes automatizados, sem abrir janela.

---

## Estado atual (v0.1.0)

**Pronto**
- Leitura das cartas do SQLite (94 cartas de estabelecimento + 4 Carvoarias; tabela `AJUDANTE` com 16 assistentes).
- Baralho com compra, descarte e reembaralhamento.
- Mesa animada: pilha de compras, descarte, mão, duas fileiras do mercado.
- Fase I (trocar a mão inteira + receber 2 cartas), Fase II e III (abrir o mercado até 2 meios sóis), fim de rodada (descartar o mercado).

**Falta**
- Carvoaria, trabalhador, carvões iniciais, estabelecimentos construídos.
- Planejamento (trabalhador atento/distraído, escolha do que construir).
- Produção, cadeias de produção, construção, pagamento com bens.
- Assistentes, guildas (cartas cinza), oponentes, fim de partida e pontuação.
- Testes automatizados.

---

## Fase 0 — Ambiente e base técnica

**Objetivo:** compilar, rodar e testar o projeto de forma confiável, por linha de comando e pela IDE.

- [x] Colocar o JDK no `PATH` / `JAVA_HOME` (JDK 25 instalado em `C:\Program Files\Eclipse Adoptium\`, mas `java` não é encontrado no terminal).
- [x] Instalar o Maven (ou usar o Maven embutido do IntelliJ) e adicionar o *Maven Wrapper* (`mvnw`) ao projeto.
- [x] Adicionar **JUnit 5** ao `pom.xml` e um primeiro teste (ex.: `DeckTest`).
- [x] Configurar o plugin de execução (`exec-maven-plugin`) para rodar com `mvnw exec:java`.
- [x] Carregar imagens e banco pelo *classpath* (`getResource`) em vez de caminhos fixos `src/main/...` — necessário para o jogo funcionar fora da IDE.
- [x] Mover `ohmygoods.db` para `src/main/resources/`.
- [x] Limpeza: código de teste solto em `Deck` (`testList`), `card.info()` no mercado, imports não usados.

**Validação:** `mvnw test` passa e `mvnw exec:java` abre a mesa funcionando como na v0.1.0.

---

## Fase 1 — Modelo de domínio completo

**Objetivo:** representar em código tudo o que existe na mesa, sem ainda aplicar as regras de produção.

- [ ] `Card`: getters para custo, pontos, recurso, produto, insumos e cadeia; método `isProducer()` (cartas cinza/guildas não produzem).
- [ ] `Building` (estabelecimento construído): carta + bens acumulados sobre ela + pessoa alocada.
- [ ] `Worker` (trabalhador: atento/distraído) e `Assistant` (custo, pontos, cores exigidas — tabela `AJUDANTE`).
- [ ] `Player`: mão, estabelecimentos, trabalhador, assistentes, carta "a construir".
- [ ] `Market`: duas fileiras, contagem de recursos disponíveis.
- [ ] Preparação completa (manual p. 2): Carvoaria aleatória por jogador, 7 carvões sobre ela, 5 cartas na mão, assistentes sorteados (4/6/8 conforme o nº de jogadores).
- [ ] Conferir a base de dados contra o manual: as 110 cartas físicas incluem trabalhadores e assistentes; verificar se as guildas estão no banco e se faltam imagens (assistentes, trabalhador, verso da carta).
- [ ] Testes unitários do setup.

**Validação:** ao abrir o jogo, a Carvoaria aparece na área do jogador com 7 carvões; testes do setup passam.

---

## Fase 2 — Planejamento (Fase II do manual)

**Objetivo:** o jogador decide onde trabalhar e o que construir, depois do Nascer do Sol.

- [ ] Área de estabelecimentos do jogador na mesa (nova `Zone`).
- [ ] Clicar em um estabelecimento para alocar o trabalhador; alternar **atento** (todos os recursos → 2 bens) / **distraído** (1 recurso a menos → 1 bem).
- [ ] Escolher 1 carta da mão para construir (fica virada para baixo).
- [ ] Mover assistente pagando 2 moedas (pode ficar para a Fase 6).
- [ ] Bloquear avanço inválido (ex.: duas pessoas no mesmo estabelecimento).

**Validação:** é possível alocar, trocar o modo e escolher a construção; o estado aparece na tela e persiste até a Fase IV.

---

## Fase 3 — Motor de produção (Fase IV, parte 1)

**Objetivo:** validar recursos e produzir bens. Esta é a regra central do jogo — será feita com testes primeiro.

- [ ] Calcular recursos disponíveis: mercado (compartilhado, não é consumido) + cartas da mão (descartadas, valem para um único estabelecimento).
- [ ] Atento: exige 100% → 2 bens. Distraído: ignora 1 unidade à escolha → 1 bem. Assistente: exige 100% → 1 bem.
- [ ] Recurso "QUALQUER" (Vidraçaria: 11 recursos quaisquer).
- [ ] Guildas de recurso: +1 recurso só para o dono, válido apenas para iniciar produção.
- [ ] Interface para escolher quais cartas da mão completar os recursos faltantes.
- [ ] Bens: cartas da pilha de compras colocadas viradas sobre o estabelecimento.
- [ ] Trabalhador sai do estabelecimento ao fim da produção; assistente permanece.

**Validação:** testes cobrindo os exemplos do manual (p. 6–7, Carvoaria atenta/distraída) passam e a produção funciona na tela.

---

## Fase 4 — Cadeias de produção

**Objetivo:** gerar bens extras a partir da mão ou de bens já produzidos.

- [ ] Só disponível se o estabelecimento produziu ≥ 1 bem no turno.
- [ ] Cadeia com 1 item: recurso da mão (ex.: trigo → Moinho) ou bem de outro estabelecimento (ex.: couro do Curtume → Sapataria).
- [ ] Cadeia com 2 itens: ambos juntos, gerando 2 bens por vez (ex.: Olaria: argila + carvão).
- [ ] Mercado e guildas **não** valem para cadeias.
- [ ] Repetir quantas vezes o jogador quiser.

**Validação:** testes reproduzindo os exemplos do manual (p. 8–9: Moinho, Sapataria, Olaria) passam.

---

## Fase 5 — Construção e economia

**Objetivo:** construir o estabelecimento planejado pagando com bens.

- [ ] Pagar com bens cujo valor somado ≥ custo (sem troco). Valor do bem = moeda no rodapé do estabelecimento.
- [ ] Interface para escolher quais bens usar no pagamento.
- [ ] Se não puder/quiser construir, a carta volta para a mão.
- [ ] Guilda de "+1 carta na Fase I" (se tiver ≤ 3 cartas no início da fase).
- [ ] Regra de exaustão: compras e descarte vazios → cada jogador descarta metade da mão.

**Validação:** exemplo do manual (p. 9: custo 7 pago com 3 + 3 + 2) funciona; o estabelecimento passa a produzir na rodada seguinte.

---

## Fase 6 — Assistentes

**Objetivo:** contratar assistentes no lugar de construir.

- [ ] Exibir os assistentes disponíveis na lateral da mesa.
- [ ] Contratar (máx. 1 por rodada, em vez de construir): pagar moedas + possuir as cores exigidas.
- [ ] Alocar imediatamente em um estabelecimento livre; mover por 2 moedas na fase de planejamento.
- [ ] Assistente produz 1 bem e pode iniciar cadeia de produção.

**Validação:** contratar, alocar, produzir e mover um assistente funciona e os testes passam.

---

## Fase 7 — Oponentes e rodada completa

**Objetivo:** partida com 2 a 4 jogadores.

- [ ] Definir o modelo de oponente (ver *Decisões em aberto*).
- [ ] Jogador inicial alterna a cada rodada; Fase IV na ordem do turno.
- [ ] Decisões "simultâneas" da Fase II.
- [ ] Prioridade na contratação do mesmo assistente (jogador inicial, depois sentido horário).
- [ ] Área de cada oponente na mesa (estabelecimentos, bens, nº de cartas na mão).

**Validação:** uma rodada completa com 2 jogadores roda do início ao fim sem intervenção manual nos dados.

---

## Fase 8 — Fim de partida e pontuação

**Objetivo:** encerrar e declarar o vencedor.

- [ ] Gatilho: algum jogador com 8 estabelecimentos (contando a Carvoaria) → termina a rodada atual + 1 rodada extra.
- [ ] Na rodada final, cadeias de produção liberadas em todos os estabelecimentos.
- [ ] Pontuação: pontos dos estabelecimentos + assistentes + (soma das moedas dos bens restantes ÷ 5).
- [ ] Desempate: mais moedas restantes após a conversão.
- [ ] Tela de resultado com o detalhamento e opção de nova partida.

**Validação:** testes de pontuação passam; uma partida completa chega à tela de resultado.

---

## Fase 9 — Polimento

- [ ] Feedback visual: produção atenta/distraída, recursos faltantes destacados, contador de moedas.
- [ ] Tela inicial (nº de jogadores, nomes).
- [ ] Ajuda/regras resumidas dentro do jogo.
- [ ] Empacotar como `.jar` executável.
- [ ] Atualizar a Documentação Técnica (stack Java).

---

## Decisões em aberto

1. **Oponentes (Fase 7):** IA simples controlada pelo computador, ou vários jogadores humanos no mesmo computador (*hotseat*)? Rede/online fica fora do escopo inicial.
2. **Imagens faltantes:** existem imagens para assistentes, trabalhador e verso da carta?
3. **Documentação Técnica:** atualizar para Java/Swing?
