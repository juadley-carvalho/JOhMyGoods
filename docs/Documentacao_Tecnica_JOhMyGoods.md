# Documentação Técnica Oficial do Projeto JOhMyGoods

## 1. Visão Geral do Projeto

- **Nome do Projeto:** JOhMyGoods
- **Repositório:** GitHub (`juadley-carvalho/JOhMyGoods`)
- **Branch Principal:** `master`
- **Objetivo:** Implementação/digitalização da lógica e interface do jogo de cartas e estratégia econômica *Oh My Goods!* (concebido originalmente por Alexander Pfister). O projeto foca no gerenciamento de cadeias de produção, alocação de trabalhadores, mecânica de mercado de matérias-primas e gestão de estoque/pontuação.

## 2. Arquitetura do Sistema & Stack Técnica

### Tecnologias & Ferramentas

- **Linguagem / Framework:** C++ / Qt Framework (ou ecossistema C/C++ equivalente orientado a GUI e orientação a objetos).
- **Controle de Versão:** Git (repositório configurado e sincronizado via GitHub).
- **Ativos Gráficos (Assets):** Recursos visuais das cartas, edifícios e ícones de matérias-primas em formato `.png` empacotados nos assets da aplicação.

### Arquitetura Modular

1. **Módulo de Domínio / Lógica do Jogo (Core Game Engine):**
   - **Baralho & Cartas:** Cada carta desempenha triplo papel (Edifício, Matéria-Prima de Mercado ou Mercadoria/Bem Produzido).
   - **Gerenciador do Mercado (Market Engine):** Controle da revelação de recursos para as fases de Manhã (Sunrise) e Tarde (Sunset).
   - **Cadeias de Produção (Production Chain Processor):** Validação de insumos necessários e processamento de produção em cadeia a partir da mão do jogador.
   - **Gerenciador de Estado do Jogador (Player State):** Controle de ouro, pontos de vitória, mão de cartas, trabalhadores, assistentes e infraestrutura construída.
2. **Módulo de Interface de Usuário (UI / UX):**
   - Renderização visual da mesa do jogo, exibição dos edifícios ativos, slots de trabalhadores e área do mercado comum.
   - Feedback visual para ações de produção (modo eficiente vs. modo desleixado/*sloppy*).

## 3. Regras & Mecânicas Implementadas (Domain Rules)

### Fluxo das Rodadas (Turn Pipeline)

1. **Fase 1: Troca de Cartas & Compra:** Possibilidade de descartar a mão inteira e comprar o mesmo número de cartas do baralho.
2. **Fase 2: Seleção & Planejamento (Manhã):**
   - Revelação parcial do mercado (fase Manhã).
   - Alocação do Trabalhador em um edifício específico:
     - *Modo Eficiente:* Requer 100% dos insumos do edifício para gerar 2 mercadorias.
     - *Modo Desleixado (Sloppy):* Requer 1 insumo a menos, gerando apenas 1 mercadoria.
   - Escolha do edifício a ser construído no turno.
3. **Fase 3: Complemento do Mercado (Tarde):** Revelação da segunda metade do mercado de matérias-primas.
4. **Fase 4: Produção & Construção:**
   - Verificação da disponibilidade dos insumos no mercado + insumos descartados da mão.
   - **Ativação da Cadeia de Produção:** Se o edifício produzir, o jogador pode baixar cartas da mão do tipo correspondente para produzir mercadorias extras.
   - Pagamento do custo e construção do edifício planejado na Fase 2.
   - Contratação de assistentes (se os requisitos de edifícios forem preenchidos).

## 4. Estado Atual do Repositório & Arquivos

- **Estrutura Git:** Repositório inicializado e versionado em `master`. Inclusão dos objetos Git, commits de acompanhamento e inclusão dos assets de UI (`.png`).
- **Sincronização:** Código-fonte e recursos vinculados ao repositório remoto no GitHub (`juadley-carvalho/JOhMyGoods`).

## 5. Instruções para Continuidade (Prompt de Handoff para o Gemini)

```text
Olá, Gemini! Estou dando continuidade ao desenvolvimento do projeto "JOhMyGoods" (repositório: juadley-carvalho/JOhMyGoods).

O projeto é uma implementação digital em C++/Qt do jogo de cartas "Oh My Goods!".

Pontos de partida para esta nova etapa:
1. Revisar/refinar a lógica das Cadeias de Produção (Production Chains) e validação dos recursos de Mercado (Manhã/Tarde).
2. Refinar a Interface Gráfica (UI) para renderização correta das cartas, alocação de trabalhadores e indicadores de produção.
3. Implementar/Ajustar os testes unitários e a lógica de final da partida (condição de vitória ao construir 8 edifícios ou contratar assistentes).

Por favor, confirme o entendimento desta estrutura para começarmos as tarefas.
```
