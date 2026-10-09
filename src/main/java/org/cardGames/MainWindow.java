package org.cardGames;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class MainWindow extends JFrame {

    private final Function<List<String>, GameState> newGame;
    private final Dimension size;
    private final HelpOverlay help = new HelpOverlay();
    private TablePanel table;
    private List<String> lastNames;

    public MainWindow(Function<List<String>, GameState> newGame) {
        super("Oh My Goods!");
        this.newGame = newGame;
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        // Área de jogo, sem contar a barra de título: maior com mais jogadores, limitada à tela
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        size = new Dimension(Math.min(1280, screen.width - 40), Math.min(760, screen.height - 100));

        setGlassPane(help);
        showStart();
        pack();
        setLocationRelativeTo(null);
        setVisible(true);
    }

    /** Tela inicial na própria janela: jogadores e nomes (os da última partida, se houver). */
    private void showStart() {
        help.close();
        StartScreen start = new StartScreen(lastNames, this::startGame, () -> System.exit(0));
        start.setPreferredSize(size);
        setContentPane(start);
        revalidate();
        repaint();
    }

    /** Troca a tela inicial pela mesa e começa a partida. */
    private void startGame(List<String> names) {
        lastNames = names;
        table = new TablePanel();
        table.setPreferredSize(size);
        Game game = new Game(newGame.apply(names), table);
        table.onKey(KeyEvent.VK_SPACE, unlessHelp(game::advance));
        table.onKey(KeyEvent.VK_R, unlessHelp(game::replaceHand));
        table.onKey(KeyEvent.VK_C, unlessHelp(game::planSelected));
        table.onKey(KeyEvent.VK_N, unlessHelp(game::decline));
        table.onKey(KeyEvent.VK_K, unlessHelp(game::runChain));
        table.onKey(KeyEvent.VK_H, help::toggle);
        table.onKey(KeyEvent.VK_ESCAPE, help::close);
        game.onGameOver(this::showResult);

        setContentPane(table);
        validate(); // a mesa precisa do tamanho antes da distribuição
        repaint();

        // Depois do primeiro layout da mesa, para a distribuição inicial aparecer animada
        SwingUtilities.invokeLater(game::start);
    }

    /** Com a ajuda aberta, as teclas do jogo não fazem nada (só H e Esc, que a fecham). */
    private Runnable unlessHelp(Runnable action) {
        return () -> {
            if (!help.isVisible()) action.run();
        };
    }

    /** Tela de resultado, desenhada sobre a mesa: detalhamento da pontuação e opção de nova partida. */
    private void showResult(List<Scoring.Score> ranking) {
        table.showResult(resultView(ranking), this::showStart, () -> {
            dispose();
            System.exit(0);
        });
    }

    /** Tabela do resultado: posição, jogador, pontos por categoria, total e moedas que sobraram (desempate). */
    static TablePanel.ResultView resultView(List<Scoring.Score> ranking) {
        List<List<String>> rows = new ArrayList<>();
        for (int i = 0; i < ranking.size(); i++) {
            Scoring.Score s = ranking.get(i);
            rows.add(List.of((i + 1) + "º", s.player().getName(), String.valueOf(s.buildingPoints()),
                    String.valueOf(s.assistantPoints()), s.goodsPoints() + " (" + s.coins() + " moedas)",
                    String.valueOf(s.total()), String.valueOf(s.leftoverCoins())));
        }
        String winner = Scoring.isTie(ranking) ? "Empate!" : ranking.getFirst().player().getName() + " venceu!";
        return new TablePanel.ResultView(List.of("", "Jogador", "Estabel.", "Assist.", "Bens", "Total", "Sobra"),
                rows, winner, "Bens: 1 ponto a cada " + Scoring.COINS_PER_POINT + " moedas; desempate pela sobra.");
    }
}
