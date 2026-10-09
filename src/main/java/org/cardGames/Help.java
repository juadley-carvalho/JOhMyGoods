package org.cardGames;

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
            + "um oponente ou um assistente para ver o detalhe.</p>"
            + "</body></html>";
}
