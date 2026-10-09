package org.cardGames;

import java.util.List;

/** Regras resumidas e teclas, mostradas na tecla H. */
public final class Help {

    private Help() { }

    public static final String TEXT = "<html><body style='width:520px'>"
            + "<h2>Oh My Goods! - regras resumidas</h2>"
            + "<p>Cada carta é ao mesmo tempo <b>recurso</b> (canto superior), <b>estabelecimento</b> e "
            + "<b>bem</b> (virada para baixo sobre o estabelecimento que a produziu). Os bens valem moedas, "
            + "usadas para construir e contratar.</p>"
            + "<h3>Rodada</h3><ol>"
            + "<li><b>Nova mão:</b> pode trocar a mão inteira (R) uma vez; depois cada um recebe 2 cartas.</li>"
            + "<li><b>Nascer do Sol:</b> o mercado abre até 2 meios sóis. Planeje: ponha o trabalhador num "
            + "estabelecimento (<b>atento</b> produz 2 bens e exige todos os recursos; <b>distraído</b> produz 1 e "
            + "aceita faltar 1) e escolha 1 carta da mão para construir (C).</li>"
            + "<li><b>Pôr do Sol:</b> a 2ª fileira do mercado abre até mais 2 meios sóis.</li>"
            + "<li><b>Produzir e construir:</b> a partir do jogador inicial, cada um produz (recursos do "
            + "mercado + cartas da mão), usa cadeias de produção (K) e então constrói a carta planejada "
            + "<i>ou</i> contrata um assistente, pagando com bens.</li></ol>"
            + "<h3>Assistentes</h3><p>Exigem estabelecimentos de certas cores. Ficam num estabelecimento livre e "
            + "produzem nele toda rodada (sempre 1 bem, exigindo todos os recursos). Mudar de lugar custa 2 moedas.</p>"
            + "<h3>Fim de partida</h3><p>Quando alguém chega a 8 estabelecimentos, joga-se mais uma rodada com "
            + "todas as cadeias liberadas. Pontos: estabelecimentos + assistentes + 1 a cada 5 moedas em bens; "
            + "desempate pela sobra.</p>"
            + "<h3>Teclas</h3><p><b>ESPAÇO</b> avançar &nbsp; <b>R</b> trocar a mão &nbsp; <b>C</b> construir a "
            + "selecionada &nbsp; <b>K</b> usar a cadeia &nbsp; <b>N</b> não produzir / não construir &nbsp; "
            + "<b>H</b> ajuda. Clique nas cartas da mão para selecioná-las.</p>"
            + "<p><b>Pagar:</b> clique nos estabelecimentos cujos bens quer usar (cada clique, +1 bem; botão "
            + "direito, -1). O que passar do custo se perde: não há troco.</p>"
            + "<p><b>Cadeia:</b> se o bem que falta está em mais de um estabelecimento, clique naquele de onde "
            + "ele deve sair. <b>Exaustão:</b> quando compras e descarte acabam, escolha na mão metade das "
            + "cartas para descartar e aperte ESPAÇO.</p>"
            + "<p>O título da janela mostra suas moedas em bens, pontos e cartas na mão. Passe o mouse sobre "
            + "um oponente ou um assistente para ver o detalhe. O quadro <b>Dicas</b>, à direita, explica as "
            + "opções da etapa em que você está.</p>"
            + "</body></html>";

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
