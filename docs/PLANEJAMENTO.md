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

- [x] `Card`: getters para custo, pontos, recurso, produto, insumos e cadeia; método `isProducer()` (cartas cinza/guildas não produzem).
- [x] `Building` (estabelecimento construído): carta + bens acumulados sobre ela + pessoa alocada.
- [x] `Worker` (trabalhador: atento/distraído) e `Assistant` (custo, pontos, cores exigidas — tabela `AJUDANTE`).
- [x] `Player`: mão, estabelecimentos, trabalhador, assistentes, carta "a construir".
- [x] `Market`: duas fileiras, contagem de recursos disponíveis.
- [x] Preparação completa (manual p. 2): Carvoaria aleatória por jogador, 7 carvões sobre ela, 5 cartas na mão, assistentes sorteados (4/6/8 conforme o nº de jogadores).
- [x] Conferir a base de dados contra o manual: as 110 cartas físicas incluem trabalhadores e assistentes; verificar se as guildas estão no banco e se faltam imagens (assistentes, trabalhador, verso da carta).
- [x] Testes unitários do setup.

**Conferência do banco:** 94 cartas no baralho (77 estabelecimentos + 17 guildas, cor `PRETO`) + 4 Carvoarias (`CAR_RECURSO = '0'`, separadas em `Database.getCharcoalBurners()`) + 16 assistentes na tabela `AJUDANTE` (o assistente 16 tem `AJU_COR_5 = ''`, ignorado). Há imagem para todas as 98 cartas; **não há** imagens de assistentes, trabalhador nem verso (o verso usa um desenho provisório). Os bens de cada estabelecimento ainda são exibidos numa pilha única (`Zone.GOODS`) — por enquanto só a Carvoaria tem bens.

**Validação:** ao abrir o jogo, a Carvoaria aparece na área do jogador com 7 carvões; testes do setup passam.

---

## Fase 2 — Planejamento (Fase II do manual)

**Objetivo:** o jogador decide onde trabalhar e o que construir, depois do Nascer do Sol.

- [x] Área de estabelecimentos do jogador na mesa, com uma pilha de bens por estabelecimento (`Zone.GOODS` agrupa as cartas pelo índice do estabelecimento) e a mão deslocando-se conforme os estabelecimentos crescem.
- [x] Clicar em um estabelecimento para alocar o trabalhador; clicar de novo alterna **atento** (todos os recursos → 2 bens) / **distraído** (1 recurso a menos → 1 bem). Etiqueta sobre a carta mostra o modo.
- [x] Escolher 1 carta da mão para construir (selecionar + tecla **C**; fica virada para baixo em `Zone.PLANNED`; clicar nela devolve para a mão).
- [x] Mover assistente pagando 2 moedas — feito na Fase 6.
- [x] Bloquear avanço inválido: não avança sem o trabalhador alocado; não aloca em guilda, em estabelecimento ocupado nem de outro jogador.

**Fluxo da rodada:** Nova Mão → Nascer do Sol → **Planejamento** (nova etapa) → Pôr do Sol → Produzir. Regras em `Player` (`placeWorker`, `planBuilding`, ...), testadas em `PlanningTest`.

**Validação:** é possível alocar, trocar o modo e escolher a construção; o estado aparece na tela e persiste até a Fase IV.

---

## Fase 3 — Motor de produção (Fase IV, parte 1)

**Objetivo:** validar recursos e produzir bens. Esta é a regra central do jogo — será feita com testes primeiro.

- [x] Calcular recursos disponíveis: mercado (compartilhado, não é consumido) + cartas da mão (descartadas, valem para um único estabelecimento).
- [x] Atento: exige 100% → 2 bens. Distraído: ignora 1 unidade à escolha → 1 bem. Assistente: exige 100% → 1 bem.
- [x] Recurso "QUALQUER" (Vidraçaria: 11 ou 12 recursos quaisquer).
- [x] Guildas de recurso: +1 recurso só para o dono, válido apenas para iniciar produção (`Card.getGuildResource()`).
- [x] Interface para escolher quais cartas da mão completar os recursos faltantes (selecionar na mão + **ESPAÇO**; **N** não produz). Só as cartas necessárias são descartadas.
- [x] Bens: cartas da pilha de compras colocadas viradas sobre o estabelecimento.
- [x] Trabalhador sai do estabelecimento ao fim da produção; assistente permanece (`Player.finishProduction`).

**Fluxo da rodada:** ... → Pôr do Sol → **Produzir** → Construir (provisório, Fase 5). A barra de status mostra o que falta. Regras em `Production` (cálculo puro) e `Player.produce`, testadas em `ProductionTest`.

**Validação:** testes cobrindo os exemplos do manual (p. 6–7, Carvoaria atenta/distraída) passam e a produção funciona na tela.

---

## Fase 4 — Cadeias de produção

**Objetivo:** gerar bens extras a partir da mão ou de bens já produzidos.

- [x] Só disponível se o estabelecimento produziu ≥ 1 bem no turno (`Building.hasProducedThisRound`, zerado em `Player.finishProduction`).
- [x] Cadeia com 1 item: recurso da mão (ex.: trigo → Moinho) ou bem de outro estabelecimento (ex.: couro do Curtume → Sapataria).
- [x] Cadeia com 2 itens: ambos juntos, gerando 2 bens por vez (ex.: Olaria: argila + carvão).
- [x] Mercado e guildas **não** valem para cadeias.
- [x] Repetir quantas vezes o jogador quiser.

**Fluxo da rodada:** ... → Produzir → **Cadeia de produção** (só se o estabelecimento produziu e tem cadeia) → Construir. Selecionar cartas na mão + **K** executa a cadeia uma vez; itens não cobertos pela mão saem automaticamente dos bens de outros estabelecimentos; **ESPAÇO** termina. As cartas usadas viram os bens (são movidas para cima do estabelecimento, sem comprar). Regras em `Production.chainItems` e `Player.runChain`, testadas em `ChainTest`.

**Validação:** testes reproduzindo os exemplos do manual (p. 8–9: Moinho, Sapataria, Olaria) passam.

---

## Fase 5 — Construção e economia

**Objetivo:** construir o estabelecimento planejado pagando com bens.

- [x] Pagar com bens cujo valor somado ≥ custo (sem troco). Valor do bem = moeda no rodapé do estabelecimento.
- [x] Interface para escolher quais bens usar no pagamento.
- [x] Se não puder/quiser construir, a carta volta para a mão.
- [x] Guilda de "+1 carta na Fase I" (se tiver ≤ 3 cartas no início da fase).
- [x] Regra de exaustão: compras e descarte vazios → cada jogador descarta metade da mão.

**Fluxo da rodada:** ... → Cadeia de produção → **Construir**: cada clique num estabelecimento põe mais 1 bem dele no pagamento (etiqueta "Pagar n (moedas)"; passando do total, volta a 0); **ESPAÇO** constrói e encerra a rodada; **N** não constrói (a carta volta para a mão). Os bens pagos vão para o descarte. Regras em `Player.canBuild`/`buildPlanned`, `Player.newHandBonus`, `Player.discardHalf` e `Deck.isExhausted`, testadas em `BuildTest`. As guildas de carta são as guildas sem produto no banco (cartas 44–46, `Card.isCardGuild`).

**Validação:** exemplo do manual (p. 9: custo 7 pago com 3 + 3 + 2) funciona; o estabelecimento passa a produzir na rodada seguinte.

---

## Fase 6 — Assistentes

**Objetivo:** contratar assistentes no lugar de construir.

- [x] Exibir os assistentes disponíveis na lateral da mesa (fichas abaixo do descarte: verde = tem as cores, cinza = não tem, azul = escolhido).
- [x] Contratar (máx. 1 por rodada, em vez de construir): pagar moedas + possuir as cores exigidas.
- [x] Alocar imediatamente em um estabelecimento livre; mover por 2 moedas na fase de planejamento.
- [x] Assistente produz 1 bem e pode iniciar cadeia de produção.

**Fluxo:** no **Planejamento**, clicar no estabelecimento de um assistente e depois num livre o move (2 moedas, bens escolhidos pelo jogo com `Player.cheapestPayment`). Na **Produção**, cada estabelecimento ocupado produz na vez dele (trabalhador primeiro, depois assistentes), cada um com sua cadeia; **N** pula o estabelecimento da vez. Na etapa **Construir ou contratar**, clicar numa ficha de assistente o escolhe; os cliques nos estabelecimentos montam o pagamento; **ESPAÇO** paga e vai para **Alocar assistente** (clicar num estabelecimento livre encerra a rodada; **N** volta). Regras em `Player.canHire`/`hireAssistant`/`moveAssistant`/`producingBuildings`, testadas em `AssistantTest`.

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

## Pontos de atenção

Registrados ao fim de cada fase; riscar quando resolvidos.

- ~~**(Fase 1) Pilha única de bens:** `Zone.GOODS` mostra os bens de todos os estabelecimentos numa só pilha. Resolver na Fase 2.~~ Resolvido na Fase 2.
- **(Fase 1) Preparação não conferida no manual:** o PDF não pôde ser lido no ambiente de desenvolvimento; Carvoaria + 7 carvões, 5 cartas e 2 assistentes por jogador seguem este planejamento. Conferir com o manual (p. 2).
- **(Fase 1) Imagens faltantes:** assistentes, trabalhador e verso da carta (o verso usa desenho provisório). Ver *Decisões em aberto*, item 2.
- **(Fase 2) Fim de rodada provisório:** como produção e construção ainda não existem, ao encerrar a rodada o trabalhador sai e a carta planejada volta para a mão (`Game.endPlanning`). Substituir nas Fases 3 e 5.
- **(Fase 2) Trabalhador obrigatório:** o avanço exige o trabalhador alocado; a construção é opcional. Conferir com o manual.
- **(Fase 2) Largura da mesa:** com muitos estabelecimentos (até 8 + carta a construir) a fileira de baixo ocupa a largura toda e a mão fica espremida. Rever o layout (Fase 9 ou antes, se atrapalhar).
- **(Fase 2) Validação visual:** a interface foi compilada e as regras testadas, mas a tela não foi conferida pelo agente; conferir rodando `mvnw exec:java`.
- **(Fase 3) Só o trabalhador produz:** a produção na tela cobre apenas o estabelecimento do trabalhador; assistentes (regra já em `Player.produce`) entram na Fase 6, com a escolha de cartas por estabelecimento.
- **(Fase 3) Recurso PEDRA:** não há estabelecimento com recurso pedra no banco (só as guildas oferecem pedra no mercado). Conferir com o manual.
- **(Fase 3) Validação visual:** regras testadas, mas a tela de produção não foi conferida pelo agente; conferir rodando `mvnw exec:java`.
- **(Fase 4) Bens escolhidos automaticamente:** quando a cadeia usa bens de outros estabelecimentos, o jogo pega do primeiro estabelecimento que tem o bem; não há como escolher a origem nem preferir a mão em vez dos bens (a mão só entra se a carta estiver selecionada). Rever se houver dois estabelecimentos com o mesmo produto.
- **(Fase 4) Recurso PEDRA (atualização):** muitos estabelecimentos *exigem* pedra para produzir, mas só guildas a oferecem no mercado/mão — confirma a importância de conferir com o manual.
- **(Fase 4) Validação visual:** a etapa de cadeia (tecla K) não foi conferida na tela pelo agente; conferir rodando `mvnw exec:java`.
- **(Fase 2) Fim de rodada provisório (atualização):** substituído por `Game.endRound` na Fase 5.
- **(Fase 5) Exaustão automática:** o jogo escolhe as cartas descartadas (a 1ª metade da mão); no jogo físico o jogador escolhe. Rever junto com a Fase 7.
- **(Fase 5) Guilda de carta:** cada guilda sem produto conta +1 carta (se houver mais de uma, somam). Conferir com o manual.
- **(Fase 5) Construção na rodada final / jogador sem carta planejada:** com nada planejado, ESPAÇO apenas encerra a rodada.
- **(Fase 5) Validação visual:** a escolha de bens para pagamento não foi conferida na tela pelo agente; conferir rodando `mvnw exec:java`.
- **(Fase 6) Pagamento automático ao mover:** o jogo escolhe os bens (menor valor acima de 2 moedas); no jogo físico o jogador escolhe.
- **(Fase 6) Espaço da lateral:** as fichas cabem até ~5 assistentes na janela padrão; com 3–4 jogadores (6–8 assistentes) vão invadir a área de baixo. Rever com a Fase 7/9.
- **(Fase 6) Sem imagem de assistente:** fichas desenhadas (número, custo, pontos, cores). Ver *Decisões em aberto*, item 2.
- **(Fase 6) Badges sobrepostos:** cada carta tem uma única etiqueta; na construção, o "Pagar" substitui a etiqueta do assistente.
- **(Fase 6) Validação visual:** contratação, alocação e movimento não foram conferidos na tela pelo agente; conferir rodando `mvnw exec:java`.
- **(Fase 1) `JAVA_HOME`:** não estava definido no terminal do agente; foi preciso apontar para o JDK manualmente para rodar `mvnw`. Verificar a variável de ambiente do sistema.

---

## Decisões em aberto

1. **Oponentes (Fase 7):** IA simples controlada pelo computador, ou vários jogadores humanos no mesmo computador (*hotseat*)? Rede/online fica fora do escopo inicial.
2. **Imagens faltantes:** existem imagens para assistentes, trabalhador e verso da carta?
3. **Documentação Técnica:** atualizar para Java/Swing?
