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

- [x] Definir o modelo de oponente: **IA simples** controlada pelo computador (`Bot`), usando as mesmas regras de `Player`.
- [x] Jogador inicial alterna a cada rodada; Fase IV na ordem do turno (`GameState.turnOrder`/`passStartingPlayer`).
- [x] Decisões "simultâneas" da Fase II: os oponentes planejam quando o humano termina, com o mesmo mercado e sem ver a escolha dele.
- [x] Prioridade na contratação do mesmo assistente: quem joga antes na ordem do turno contrata primeiro.
- [x] Área de cada oponente na mesa (estabelecimentos, bens, nº de cartas na mão).

**Fluxo:** na Fase I todos recebem cartas; ao terminar o **Planejamento**, cada oponente separa a carta a construir (a de mais pontos que os bens pagam, com folga de 2 moedas) e aloca o trabalhador onde rende mais (atento se nada falta, distraído se falta 1). No **Pôr do Sol**, os oponentes que vêm antes do humano na ordem do turno jogam a Fase IV inteira (produzir com a mão toda oferecida, cadeias enquanto der, construir ou, se não der, contratar o assistente de mais pontos); os que vêm depois jogam ao fim da vez do humano, antes do mercado ser descartado. As cartas dos oponentes ficam na zona invisível `Zone.OPPONENTS` (voam para a esquerda e somem); o painel à esquerda, abaixo da pilha de compras, mostra cada oponente (borda amarela = inicial): mão, assistentes, estabelecimentos com nº de bens (`*` trabalhador atento, `~` distraído, `+A` assistente) e o resumo da última vez. A barra de status indica quando o humano é o inicial. Regras em `Bot` e `GameState`, testadas em `RoundTest` (15 rodadas só com a IA, 2 a 4 jogadores, sem perder cartas).

**Validação:** uma rodada completa com 2 jogadores roda do início ao fim sem intervenção manual nos dados.

---

## Fase 8 — Fim de partida e pontuação

**Objetivo:** encerrar e declarar o vencedor.

- [x] Gatilho: algum jogador com 8 estabelecimentos (contando a Carvoaria) → termina a rodada atual + 1 rodada extra.
- [x] Na rodada final, cadeias de produção liberadas em todos os estabelecimentos.
- [x] Pontuação: pontos dos estabelecimentos + assistentes + (soma das moedas dos bens restantes ÷ 5).
- [x] Desempate: mais moedas restantes após a conversão.
- [x] Tela de resultado com o detalhamento e opção de nova partida.

**Fluxo:** ao fim de cada rodada, `GameState.endRound` confere o gatilho: se alguém tem 8 estabelecimentos (com a Carvoaria), a próxima rodada é a **final** (a barra de status mostra "RODADA FINAL") e as cadeias ficam liberadas (`Player.areChainsUnlocked`). Na Fase IV da rodada final, depois da produção, o humano passa pela cadeia de cada estabelecimento que tem uma (K/ESPAÇO); o `Bot` usa todas. Ao fim da rodada final o jogo entra em **Fim de partida**: ESPAÇO abre a tela de resultado (posição, pontos de estabelecimentos, assistentes, bens com as moedas, total e sobra) com *Nova partida* ou *Sair*. Regras em `Scoring` e `GameState`, testadas em `EndGameTest` (inclui partidas completas só com a IA, 2 a 4 jogadores).

**Validação:** testes de pontuação passam; uma partida completa chega à tela de resultado.

---

## Fase 9 — Polimento

- [x] Feedback visual: produção atenta/distraída, recursos faltantes destacados, contador de moedas.
- [x] Tela inicial (nº de jogadores, nomes).
- [x] Ajuda/regras resumidas dentro do jogo.
- [x] Empacotar como `.jar` executável.
- [ ] Atualizar a Documentação Técnica (stack Java). *Pendente: depende da Decisão em aberto 3; o README já foi atualizado.*

---

## Fase 10 — Escolhas do jogador e acabamento

**Objetivo:** devolver ao jogador as escolhas que o manual dá a ele e fechar os pontos de atenção de interface.

- [x] Validação visual: capturas da mesa numa partida com 2 e com 4 jogadores (planejamento, produção, cadeia, construção, contratação, resultado), antes e depois das mudanças. *Feitas com `Snapshots` (mesa desenhada fora da janela, jogando pela interface), não com o `.jar` aberto.*
- [x] Pagamento escolhido pelo jogador (construir, contratar, mover assistente): clicar nos bens sobre os estabelecimentos para marcar o que pagar e confirmar quando o total cobrir o custo (sem troco). Fecha o ponto da Fase 6 e completa o da Fase 5.
- [x] Origem dos bens na cadeia: perguntar só quando mais de um estabelecimento tiver o mesmo bem; nos outros casos, continua automático (Fase 4).
- [x] Exaustão: o humano escolhe as cartas a descartar (metade da mão, arredondada para baixo); o bot continua automático (Fases 5/7).
- [x] Ritmo dos oponentes: pausa curta entre os passos de cada bot na Fase IV, com a barra de status dizendo o que ele fez (ex.: "Jogador 2 · PADARIA: produziu 2 bens") (Fase 7).
- [x] Layout, conforme o que a validação mostrou: barra de status em 2 linhas; assistentes livres em fichas compactas (2 colunas, cores exigidas em quadradinhos); oponentes em caixas resumidas (pontos, nº de estabelecimentos, moedas, mão), com detalhe ao passar o mouse; estabelecimentos se sobrepõem quando não cabem (Fases 2, 6 e 7).
- [x] Tela de resultado desenhada na mesa (pontuação por categoria e botões "Jogar de novo" e "Sair"), no lugar do `JOptionPane` (Fase 8).

**O que as capturas de antes mostraram:** a barra de status passava da largura da janela; com 3 oponentes as caixas da esquerda invadiam os bens do jogador; 8 fichas de assistente cobriam a mão; com 7 estabelecimentos a mão ficava espremida na borda direita.

**Fluxo:**
- **Pagar** (construir, contratar e a nova etapa **Mover assistente**, na Fase II): cada clique num estabelecimento ou nos bens dele põe mais 1 bem no pagamento (passando do total, volta a 0); o botão direito tira 1. A barra mostra "pagamento X de Y" e, se passar, quanto se perde (não há troco). Para mover: clicar no assistente, depois no estabelecimento livre (etiqueta "Destino"), escolher os bens e **ESPAÇO**; **N** desiste.
- **Cadeia:** se um item não vem das cartas selecionadas e está em mais de um estabelecimento, **K** avisa e esses estabelecimentos ficam clicáveis; o escolhido ganha a etiqueta "Origem" e vale até trocar de estabelecimento na fila. Regras em `Production.itemsFromGoods`/`goodSources` e `Player.runChain(…, sources)`.
- **Exaustão:** os oponentes descartam na hora; para o humano, a barra pede metade da mão, as cartas são escolhidas na mesa e **ESPAÇO** confirma. A ação em andamento (comprar bens, abrir o mercado...) espera num `SecondaryLoop`, como uma janela modal, e continua depois. Regras em `Player.exhaustionDiscards`/`discardChosen`.
- **Oponentes:** `Bot.Table.step` avisa cada passo (produção, cadeia, construção/contratação); `Game` agenda a narração (`TablePanel.narrate`) no instante em que as cartas daquele passo começam a se mover e acrescenta 0,7 s de pausa. Enquanto há narração, a 1ª linha mostra "Fase IV - vez dos oponentes" e as caixas dos oponentes só se atualizam no fim.
- **Capturas:** `Snapshots` (teste) monta partidas de 2 e 4 jogadores, uma "mesa cheia" (humano com 7 estabelecimentos, jogada até o fim) e um cenário de escolhas (dois Curtumes, assistente, baralho esgotado), e salva os PNGs.

Testes novos em `ChainTest` (origem do couro com dois Curtumes), `BuildTest` (descarte escolhido) e `RoundTest` (passos do oponente). `EndGameTest` confere a tabela do resultado.

**Fora desta fase:** IA melhor (só se o jogo ficar fácil demais), imagens faltantes (Decisão em aberto 2), Documentação Técnica (Decisão em aberto 3) e `JAVA_HOME` (configuração do sistema).

**Validação:** testes passam (83); as escolhas novas funcionam na tela (capturas antes/depois); partida completa com 4 jogadores sem sobreposição na mesa (capturas `4j-cheia`).

---

## Fase 11 — Quadro de dicas

**Objetivo:** ajudar o jogador a entender as opções de cada etapa sem sair da mesa (sugestão do usuário: uma área de dicas no canto direito).

- [x] Quadro **Dicas** na coluna da direita, logo abaixo das fichas de assistente, até a fileira de baixo (contando a carta selecionada, que sobe). Sobe quando os assistentes são contratados; com menos de 50 px livres, não aparece.
- [x] Dicas fixas de cada etapa em `Help.tips` (opções e lembretes de regra), com "H: regras completas" no cabeçalho.
- [x] Dicas da situação, calculadas em `Game.tips` e mostradas primeiro: no planejamento, quanto valem os bens e que cartas da mão já cabem nesse valor; na produção, que cartas da mão têm o recurso que falta; na construção, se a carta planejada cabe e que assistentes estão ao alcance (ou por que nenhum está); na rodada final, que as cadeias estão liberadas; na exaustão, só a dica do descarte.
- [x] A ajuda (tecla H) menciona o quadro.

**Validação:** testes passam (83); capturas com 2 e 4 jogadores (8 fichas de assistente) mostram o quadro sem invadir a mão nem o mercado.

---

## Fase 12 — Pendentes resolvidos com código

**Objetivo:** fechar os pontos de atenção que não dependiam de decisão do usuário.

- [x] Baralho com semente: o `Deck` recebe o `Random` do `Setup` (embaralhamento inicial e reembaralhamentos); a mesma semente repete a partida.
- [x] Exaustão dos oponentes: `Bot.discardForExhaustion` fica com as cartas mais úteis (recursos que os estabelecimentos dele usam na produção ou na cadeia, cartas que ele já pode construir) e descarta o resto.
- [x] Contador na mesa: moedas em bens, pontos e cartas na mão aparecem à direita da 1ª linha da barra de status (antes, no título da janela); a ajuda foi atualizada.
- [x] Dicas de orçamento: no planejamento, "já cabem" soma as moedas que a produção já garante (estabelecimentos ocupados que produzem só com o mercado e as guildas).

**Validação:** testes passam (85; novos em `SetupTest` — mesma semente — e `BuildTest` — descarte do oponente); capturas com 2 e 4 jogadores mostram o contador sem cobrir o título da etapa.

---

## Pontos de atenção

Registrados ao fim de cada fase; quando resolvidos, passam de *Pendentes* para *Concluídos* com a resolução.

### Pendentes

- **(Fase 1) Imagens faltantes:** assistentes, trabalhador e verso da carta (o verso usa desenho provisório). Ver *Decisões em aberto*, item 2.
- **(Fase 6) Sem imagem de assistente:** fichas desenhadas (número, custo, pontos, cores). Ver *Decisões em aberto*, item 2.
- **(Fase 6) Badges sobrepostos:** cada carta tem uma única etiqueta; na construção, o "Pagar" substitui a etiqueta do assistente.
- **(Fase 7) IA simples:** o oponente nunca troca a mão, nunca move assistentes, oferece a mão inteira na produção e usa as cadeias até acabar (pode gastar cartas/bens que valeriam mais guardados). Suficiente para testar o fluxo; melhorar se ficar fácil demais.
- **(Fase 9) Badge de produção:** o estabelecimento da vez mostra "Atento: falta N" (vermelho se não dá para produzir), contando o mercado e as cartas selecionadas; substitui a etiqueta do trabalhador/assistente durante a produção.
- **(Fase 9) Validação:** testes e `.jar` conferidos (o jar lê banco e imagens de dentro dele), mas a tela inicial, a ajuda e os badges não foram conferidos na tela pelo agente; conferir com `java -jar target/OhMyGoods.jar`.
- **(Fase 10) Validação visual fora da janela:** as capturas (`Snapshots`) desenham a mesa sem abrir a janela; cobrem as etapas das Fases 2 a 9, mas não a tela inicial nem a ajuda (`JOptionPane`), nem o tempo real das animações e da narração. Conferir com `java -jar target/OhMyGoods.jar`.
- **(Fase 10) Exaustão segura a ação:** enquanto o humano escolhe o descarte, as outras teclas e cliques ficam bloqueados (só a seleção da mão e ESPAÇO respondem). Fechar a janela nesse momento encerra o jogo normalmente.
- **(Fase 10) Estabelecimentos sobrepostos:** com muitos estabelecimentos e mão grande, cada um mostra só a parte esquerda (custo, recursos, produção); o selo de bens foi para o canto esquerdo e as etiquetas diminuem a fonte para caber. A carta inteira só aparece no último.
- **(Fase 10) Resumo dos oponentes:** o modelo do oponente é calculado de uma vez; a tela é que mostra os passos aos poucos. Durante a narração, as caixas da esquerda guardam o resumo antigo e só mudam no fim.
- **(Fase 11) Dicas cortadas:** com a janela baixa ou muitas fichas de assistente, as últimas dicas são cortadas com "..."; as da situação vêm primeiro para não serem as cortadas.
- **(Fase 12) Produção garantida:** as dicas do planejamento só contam a produção que o mercado e as guildas já cobrem; a que depende das cartas da mão ou da 2ª fileira do mercado não entra.
- **(Fase 12) Descarte do oponente:** a utilidade da carta é uma heurística simples (recurso usado pelos estabelecimentos, carta que já dá para construir); não olha o mercado nem o que falta para produzir.

### Concluídos

- **(Fase 1) Pilha única de bens:** `Zone.GOODS` mostrava os bens de todos os estabelecimentos numa só pilha. → Resolvido na Fase 2.
- **(Fase 1) Preparação não conferida no manual:** Carvoaria + 7 carvões, 5 cartas e 2 assistentes por jogador seguiam este planejamento. → Conferido em `docs/oh_my_goods_manual.md`: Carvoaria + 7 carvões, 5 cartas e assistentes 4/6/8 (2 por jogador) batem com o manual.
- **(Fase 1) `JAVA_HOME`:** não estava definido no terminal do agente; foi preciso apontar para o JDK manualmente para rodar `mvnw`. → A variável já estava configurada no usuário; o VS Code tinha sido aberto antes dela. Depois de reabri-lo, o terminal do agente encontra o JDK 25 e o `mvnw` roda direto.
- **(Fase 2) Fim de rodada provisório:** ao encerrar a rodada, o trabalhador saía e a carta planejada voltava para a mão (`Game.endPlanning`). → Substituído por `Game.endRound` na Fase 5.
- **(Fase 2) Trabalhador obrigatório:** o avanço exige o trabalhador alocado; a construção é opcional. → Conferido: o manual manda colocar o trabalhador num estabelecimento; construir é opcional.
- **(Fase 2) Largura da mesa:** com muitos estabelecimentos (até 8 + carta a construir), a fileira de baixo ocupava a largura toda e a mão ficava espremida. → Resolvido na Fase 10: os estabelecimentos se sobrepõem (até 84 px cada) para a mão manter um espaço mínimo.
- **(Fases 3 e 4) Recurso PEDRA:** não há estabelecimento com recurso pedra no banco (só as guildas oferecem pedra no mercado), mas muitos estabelecimentos *exigem* pedra para produzir. → Conferido: pedra é o recurso das cartas pretas (guildas), 17 cartas — mesma quantidade das outras cores (verde é a maior, 26), como diz o manual.
- **(Fase 3) Só o trabalhador produz:** a produção na tela cobria apenas o estabelecimento do trabalhador. → Resolvido na Fase 6: `Player.producingBuildings` põe na fila o estabelecimento do trabalhador e os que têm assistente.
- **(Fase 4) Bens escolhidos automaticamente:** na cadeia, o jogo pegava o bem do primeiro estabelecimento que o tinha, sem deixar escolher a origem. → Resolvido na Fase 10: com o bem em mais de um estabelecimento, o jogador escolhe a origem.
- **(Fase 5) Exaustão automática:** o jogo escolhia as cartas descartadas (a 1ª metade da mão). → Resolvido na Fase 10 para o humano; os oponentes seguem automáticos (ver *Pendentes*, Fase 7).
- **(Fase 5) Guilda de carta:** cada guilda sem produto conta +1 carta (se houver mais de uma, somam). → Conferido: +1 carta com até 3 cartas na mão. O manual descreve o efeito por guilda, então duas guildas somam +2 (interpretação mantida).
- **(Fase 5) Construção na rodada final / jogador sem carta planejada:** com nada planejado, ESPAÇO apenas encerra a rodada. → Conferido: a rodada final é uma rodada completa (pode construir) e construir é opcional.
- **(Fase 6) Pagamento automático ao mover:** o jogo escolhia os bens (menor valor acima de 2 moedas). → Resolvido na Fase 10 (etapa Mover assistente).
- **(Fase 6) Espaço da lateral:** com 3–4 jogadores (6–8 assistentes), as fichas invadiam a área de baixo. → Resolvido na Fase 10: fichas compactas em 2 colunas (8 assistentes ocupam 4 linhas).
- **(Fase 7) Oponentes jogam de uma vez:** a Fase IV de cada oponente acontecia numa só ação, sem pausa para acompanhar. → Resolvido na Fase 10: cada passo aparece na barra de status, com pausa.
- **(Fase 7) Espaço do painel:** com 3 oponentes e muitos estabelecimentos, as caixas podiam invadir a fileira de baixo. → Resolvido na Fase 10: caixas resumidas de altura fixa, detalhe ao passar o mouse.
- **(Fase 7) Exaustão dos oponentes:** os oponentes descartavam a 1ª metade da mão. → Resolvido na Fase 12: ficam com as cartas mais úteis (`Bot.discardForExhaustion`).
- **(Fase 8) Tela de resultado simples:** era um `JOptionPane` com tabela HTML. → Resolvido na Fase 10: resultado desenhado na mesa.
- **(Fase 8) Empate total:** se total e sobra empatam, o resultado mostra "Empate!" (vitória compartilhada). → Conferido: o manual desempata pelas moedas restantes e não fala de empate nelas; a vitória compartilhada fica.
- **(Fase 8) Cadeias na rodada final:** o humano passa por todos os estabelecimentos com cadeia, um de cada vez (inclusive os que já usaram a cadeia ao produzir). → Conferido: o manual libera as cadeias de **todos** os estabelecimentos na rodada final. O uso das cadeias pelo bot segue em *Pendentes* (Fase 7, IA simples).
- **(Fases 2 a 8) Validação visual:** planejamento, produção, cadeia (tecla K), pagamento da construção, assistentes, rodada com oponentes e tela de resultado não tinham sido conferidos na tela pelo agente. → Conferido nas capturas da Fase 10 (mesa desenhada fora da janela).
- **(Fase 9) Janela maior e itens não feitos:** a janela passou para 1280×760 sem refazer o layout; os oponentes ainda jogavam de uma vez e o resultado era um `JOptionPane`. → Feitos na Fase 10.
- **(Fase 9) Contador no título:** moedas/pontos/cartas apareciam no título da janela, não na mesa. → Resolvido na Fase 12: à direita da 1ª linha da barra de status.
- **(Fase 10) Sem troco:** pagar acima do custo é permitido (a barra avisa quanto se perde). → Conferido: é a regra do manual.
- **(Fase 10) Baralho sem semente:** o `Deck` embaralhava sem `Random`, então a mesma semente não repetia a partida. → Resolvido na Fase 12: o `Deck` usa o `Random` do `Setup`.
- **(Fase 11) Dicas de orçamento:** "já cabem" comparava o custo só com os bens de agora. → Resolvido na Fase 12: soma a produção já garantida (ver *Pendentes*, Fase 12).
- **(Pós-Fase 9) Manual conferido:** com o manual em Markdown, também batem com o código: modo distraído, recursos da mão valendo para um só estabelecimento, mercado e guildas sem valer na cadeia, cadeias de todos os estabelecimentos na rodada final, Vidraçaria (11/12 recursos quaisquer) e troca de inicial a cada rodada. → As escolhas automáticas que restavam (exaustão, bens na cadeia e no pagamento) foram feitas na Fase 10.

---

## Decisões em aberto

1. ~~**Oponentes (Fase 7):** IA simples ou *hotseat*?~~ Decidido: IA simples.
2. **Imagens faltantes:** existem imagens para assistentes, trabalhador e verso da carta?
3. **Documentação Técnica:** atualizar para Java/Swing?
