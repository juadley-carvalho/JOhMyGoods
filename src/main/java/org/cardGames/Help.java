package org.cardGames;

import java.util.List;

/** Regras resumidas e teclas, mostradas na tecla H. */
public final class Help {

    private Help() { }

    /**
     * Regras e controles em duas colunas, para o {@link HelpOverlay} (o estilo das classes fica lá).
     * À esquerda, o jogo; à direita, como jogá-lo nesta mesa.
     */
    public static final String TEXT = "<html><body><table width='100%' cellspacing='0' cellpadding='0'><tr>"
            + "<td valign='top' width='50%'>"
            + "<h2>A carta</h2>"
            + "<p>Cada carta serve para três coisas:</p>"
            + "<table cellspacing='0' cellpadding='0'>"
            + use("Recurso", "o símbolo no canto de cima; vem do mercado ou da sua mão.")
            + use("Estabelecimento", "construída à sua frente, transforma recursos em bens.")
            + use("Bem", "virada para baixo sobre quem a produziu; vale moedas para construir e contratar.")
            + "</table>"
            + "<h2>A rodada</h2>"
            + "<table cellspacing='0' cellpadding='0'>"
            + step("I", "Nova mão", "pode trocar a mão inteira (R) uma vez; depois cada um recebe 2 cartas.")
            + step("II", "Nascer do Sol", "o mercado abre até 2 meios sóis. Ponha o trabalhador num estabelecimento "
                    + "(<b>atento</b>: 2 bens, exige todos os recursos; <b>distraído</b>: 1 bem, pode faltar 1) "
                    + "e escolha 1 carta da mão para construir (C).")
            + step("III", "Pôr do Sol", "a 2ª fileira do mercado abre até mais 2 meios sóis.")
            + step("IV", "Produzir e construir", "a partir do jogador inicial, cada um produz (mercado + cartas "
                    + "da mão), usa cadeias (K) e constrói a carta planejada <i>ou</i> contrata um assistente, "
                    + "pagando com bens.")
            + "</table>"
            + "<h2>Assistentes</h2>"
            + "<p>Exigem estabelecimentos de certas cores. Ficam num estabelecimento livre e produzem nele toda "
            + "rodada: sempre 1 bem, exigindo todos os recursos. Mudar de lugar custa 2 moedas.</p>"
            + "<h2>Fim de partida</h2>"
            + "<p>Quando alguém chega a <b>8 estabelecimentos</b>, joga-se mais uma rodada com todas as cadeias "
            + "liberadas.</p>"
            + "<p class='score'>Pontos = estabelecimentos + assistentes + 1 a cada 5 moedas em bens. "
            + "Desempate pela sobra.</p>"
            + "</td>"
            + "<td width='28'></td>"
            + "<td valign='top' width='50%'>"
            + "<h2>Teclado</h2>"
            + "<table cellspacing='0' cellpadding='0'>"
            + key("ESPAÇO", "avança para a próxima etapa")
            + key("R", "troca a mão inteira")
            + key("C", "separa a carta selecionada para construir")
            + key("K", "usa a cadeia de produção")
            + key("N", "não produzir / não construir / voltar")
            + key("H", "abre e fecha esta ajuda (Esc também fecha)")
            + "</table>"
            + "<h2>Mouse</h2>"
            + "<table cellspacing='0' cellpadding='0'>"
            + use("Selecionar", "clique nas cartas da mão.")
            + use("Pagar", "clique nos estabelecimentos cujos bens quer usar: cada clique, +1 bem; "
                    + "botão direito, -1. O que passar do custo se perde: <b>não há troco</b>.")
            + use("Cadeia", "se o bem que falta está em mais de um estabelecimento, clique naquele de onde ele "
                    + "deve sair.")
            + use("Detalhes", "passe o mouse sobre um oponente ou um assistente.")
            + "</table>"
            + "<h2>Na mesa</h2>"
            + "<p>A barra de cima diz a etapa e o que fazer; no canto direito, suas moedas em bens, pontos e "
            + "cartas na mão. O quadro <b>Dicas</b>, à direita, explica as opções da etapa.</p>"
            + "<p class='note'><b>Exaustão:</b> quando compras e descarte acabam, escolha na mão metade das cartas "
            + "para descartar e aperte ESPAÇO.</p>"
            + "</td></tr></table></body></html>";

    /** Linha de definição: termo em destaque e explicação. */
    private static String use(String term, String text) {
        return "<tr><td valign='top' class='term' width='112' nowrap>" + term + "</td>"
                + "<td valign='top' class='def'>" + text + "</td></tr>";
    }

    /** Etapa da rodada: número romano num selo, nome e explicação. */
    private static String step(String number, String name, String text) {
        return "<tr><td valign='top' class='step' width='30' nowrap>" + number + "</td>"
                + "<td valign='top' class='def'><b>" + name + ":</b> " + text + "</td></tr>";
    }

    /** Tecla desenhada como um botão, seguida do que ela faz. */
    private static String key(String key, String text) {
        return "<tr><td valign='top' class='keycell' width='76' nowrap><table cellspacing='0' cellpadding='0'>"
                + "<tr><td class='key' align='center' width='64'>" + key + "</td></tr></table></td>"
                + "<td valign='top' class='def'>" + text + "</td></tr>";
    }

    /** Dicas fixas de cada etapa, mostradas no quadro da direita (o {@link Game} acrescenta as da situação). */
    static List<String> tips(Game.Phase phase) {
        return switch (phase) {
            case NEW_HAND -> List.of(
                    "R troca a mão inteira (descarta todas e compra o mesmo número); só uma vez por rodada.",
                    "Vale a pena quando a mão não tem os recursos que você usa nem cartas que dê para construir.",
                    "Depois, cada jogador recebe 2 cartas (guildas de carta: +1 com até 3 cartas na mão).");
            case SUNRISE -> List.of(
                    "O mercado abre cartas até aparecerem 2 meios sóis. Os recursos dele servem a todos, sem gastar nada.",
                    "Você planeja vendo esta fileira; a 2ª (Pôr do Sol) só abre depois.");
            case PLAN -> List.of(
                    "Atento: 2 bens, exige todos os recursos. Distraído: 1 bem, pode faltar 1. Clique de novo para alternar.",
                    "Conte o mercado e as cartas da mão; a 2ª fileira ainda pode trazer o que falta.",
                    "C separa 1 carta para construir; ela só é paga (com bens) na Fase IV.");
            case MOVE -> List.of(
                    "Mover custa " + Player.MOVE_ASSISTANT_COST + " moedas em bens; o que passar se perde (não há troco).",
                    "Prefira bens de valor baixo. N desiste e volta ao planejamento.");
            case SUNSET -> List.of(
                    "A 2ª fileira abre mais cartas até 2 meios sóis.",
                    "Depois cada jogador produz e constrói, a partir do inicial.");
            case PRODUCE -> List.of(
                    "O mercado não se gasta; as cartas da mão usadas como recurso vão para o descarte.",
                    "Cada carta da mão vale para um só estabelecimento.",
                    "Sem recursos suficientes, N: não produzir.");
            case CHAIN -> List.of(
                    "A cadeia troca os itens indicados (cartas da mão ou bens de outros estabelecimentos) por mais bens aqui; pode repetir.",
                    "Mercado e guildas não valem na cadeia.",
                    "Gastar bens de outro estabelecimento só compensa se o novo bem valer mais. ESPAÇO termina.");
            case BUILD -> List.of(
                    "Construa a carta planejada ou contrate 1 assistente (clique na ficha), pagando com bens; não há troco.",
                    "No fim, bens guardados também pontuam: 1 ponto a cada 5 moedas.");
            case PLACE_ASSISTANT -> List.of(
                    "O assistente fica num estabelecimento livre e produz nele 1 bem toda rodada, exigindo todos os recursos.",
                    "N volta para a escolha do pagamento.");
            case GAME_OVER -> List.of(
                    "Pontos: estabelecimentos + assistentes + 1 a cada 5 moedas em bens; desempate pela sobra.");
        };
    }

    /** Dica da exaustão (no lugar das da etapa enquanto o jogador escolhe o descarte). */
    static final String EXHAUSTION_TIP =
            "Compras e descarte acabaram: todos descartam metade da mão. Guarde as cartas com os recursos que você usa.";
}
