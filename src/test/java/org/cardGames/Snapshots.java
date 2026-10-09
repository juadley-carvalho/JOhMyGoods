package org.cardGames;

import org.cardGames.database.Database;

import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;
import java.util.Random;

/**
 * Capturas de tela da mesa sem abrir janela (validação visual da Fase 10): monta a partida,
 * joga pela interface (as mesmas ações das teclas e cliques) e salva PNGs na pasta indicada.
 * Não é um teste do JUnit; roda com {@code java -cp target/classes;target/test-classes;<sqlite.jar>
 * -Djava.awt.headless=true org.cardGames.Snapshots <pasta>}.
 */
public final class Snapshots {

    private static final int WIDTH = 1280;
    private static final int HEIGHT = 760;
    private static final int BOT_STEP_SAMPLE_MS = 900; // um pouco depois do primeiro passo do oponente

    private final File dir;
    private final String prefix;
    private final TablePanel table = new TablePanel();
    private final Game game;
    private final Player human;

    private Snapshots(File dir, String prefix, GameState state) {
        this(dir, prefix, state, true);
    }

    private Snapshots(File dir, String prefix, GameState state, boolean autoExhaustion) {
        this.dir = dir;
        this.prefix = prefix;
        this.human = state.human();
        table.setSize(WIDTH, HEIGHT);
        game = new Game(state, table);
        game.setAutoExhaustion(autoExhaustion);
        game.onGameOver(ranking -> table.showResult(MainWindow.resultView(ranking), () -> { }, () -> { }));
        game.start();
        settle();
    }

    public static void main(String[] args) throws Exception {
        File dir = new File(args.length > 0 ? args[0] : "target/snapshots");
        dir.mkdirs();
        SwingUtilities.invokeAndWait(() -> {
            Database database = new Database();
            List<Card> cards = database.getCards();
            List<Card> burners = database.getCharcoalBurners();
            List<Assistant> assistants = database.getAssistants();
            database.closeConnection();
            for (int players : new int[]{2, 4}) {
                GameState state = Setup.newGame(cards, burners, assistants, players, new Random(7));
                new Snapshots(dir, players + "j", state).firstRound();
                GameState crowded = Setup.newGame(cards, burners, assistants, players, new Random(11));
                crowd(crowded.human(), crowded.deck());
                new Snapshots(dir, players + "j-cheia", crowded).untilGameOver();
            }
            // Semente em que 2 Curtumes e 1 Sapataria ainda estão na pilha (não foram para mãos ou carvões)
            for (long seed = 1; ; seed++) {
                GameState choices = Setup.newGame(cards, burners, assistants, 2, new Random(seed));
                Building[] tanneries;
                try {
                    tanneries = prepareChoices(choices);
                } catch (IllegalStateException missing) {
                    continue;
                }
                new Snapshots(dir, "escolhas", choices, false).choices(tanneries);
                break;
            }
        });
        System.exit(0);
    }

    /** Mesa cheia: o humano começa com 6 estabelecimentos a mais, cada um com alguns bens. */
    private static void crowd(Player player, Deck deck) {
        int added = 0;
        while (added < 6) {
            Card card = deck.draw();
            if (!card.isProducer()) { deck.discard(card); continue; }
            Building building = player.build(card);
            for (int i = 0; i < added % 3 + 1; i++) building.addGood(deck.draw());
            added++;
        }
    }

    /**
     * Escolhas do jogador: dois Curtumes com couro (o 1º com um assistente) e uma Sapataria, cadeias
     * liberadas (como na rodada final) e o baralho inteiro guardado como bens do oponente, para a
     * exaustão acontecer logo na 1ª compra. Devolve os dois Curtumes.
     */
    private static Building[] prepareChoices(GameState state) {
        Player human = state.human();
        Deck deck = state.deck();
        Building first = human.build(drawNamed(deck, "CURTUME"));
        Building second = human.build(drawNamed(deck, "CURTUME"));
        human.build(drawNamed(deck, "SAPATARIA"));
        for (int i = 0; i < 2; i++) {
            first.addGood(drawAny(deck));
            second.addGood(drawAny(deck));
        }
        Assistant assistant = state.availableAssistants().removeFirst();
        human.hire(assistant);
        first.setPerson(assistant);
        human.setChainsUnlocked(true);
        Building hoard = state.opponents().getFirst().getCharcoalBurner();
        while (!deck.isExhausted()) {
            if (deck.needsReshuffle()) deck.reshuffle();
            hoard.addGood(deck.draw());
        }
        return new Building[]{first, second};
    }

    /** Compra uma carta, reembaralhando o descarte se a pilha acabou. */
    private static Card drawAny(Deck deck) {
        if (deck.needsReshuffle()) deck.reshuffle();
        Card card = deck.draw();
        if (card == null) throw new IllegalStateException("baralho vazio");
        return card;
    }

    /** Compra até achar a carta com o nome; as outras vão para o descarte. */
    private static Card drawNamed(Deck deck, String name) {
        for (Card card = deck.draw(); card != null; card = deck.draw()) {
            if (card.getName().equals(name)) return card;
            deck.discard(card);
        }
        throw new IllegalStateException("sem " + name);
    }

    /** Exaustão (escolher o descarte), mover assistente pagando e escolher a origem do couro na cadeia. */
    private void choices(Building[] tanneries) {
        // A compra da Fase I esgota o baralho: o jogo espera o humano escolher. Esta ação roda dentro da espera.
        SwingUtilities.invokeLater(() -> {
            settle();
            shot("1-exaustao");
            int half = human.exhaustionDiscards();
            for (int i = 0; i < half; i++) {
                Card card = human.getHand().get(i);
                act(() -> table.press(card));
            }
            shot("2-exaustao-escolhidas");
            game.advance();           // confirma o descarte e libera a compra
            game.setAutoExhaustion(true); // as próximas exaustões (o baralho continua curto) sem esperar
        });
        act(game::advance);           // Fase I
        shot("3-exaustao-depois");
        act(game::advance);           // Nascer do Sol
        Building sapataria = human.getBuildings().getLast();
        act(() -> table.press(tanneries[0].getCard()));   // escolhe o assistente
        act(() -> table.press(sapataria.getCard()));      // destino
        shot("4-mover-destino");
        act(() -> table.press(human.getCharcoalBurner().getCard()));
        shot("5-mover-pagando");
        act(() -> table.press(tanneries[0].getCard()));       // etiquetas empilhadas: assistente e pagamento
        shot("5a-pagar-com-assistente");
        act(() -> table.pressRight(tanneries[0].getCard()));
        act(() -> table.press(human.getCharcoalBurner().getCard()));
        act(game::advance);           // paga e move
        shot("6-movido");
        act(() -> table.press(human.getCharcoalBurner().getCard()));  // trabalhador
        act(game::advance);
        act(game::advance);           // Pôr do Sol
        for (int guard = 0; guard < 30 && game.phase() != Game.Phase.BUILD; guard++) {
            if (game.phase() == Game.Phase.PRODUCE) {
                act(game::advance);
                if (game.phase() == Game.Phase.PRODUCE) act(game::decline);
                continue;
            }
            act(game::runChain);
            if (table.statusHint().contains("mais de um")) {
                shot("7-cadeia-origem-pedida");
                act(() -> table.press(tanneries[1].getCard()));
                shot("8-cadeia-origem-escolhida");
                act(game::runChain);
                shot("9-cadeia-feita");
            }
            act(game::advance);
        }
    }

    /** Primeira rodada, parando nas etapas que interessam. */
    private void firstRound() {
        shot("1-inicio");
        act(game::advance);           // Fase I
        act(game::advance);           // Nascer do Sol
        shot("2-planejamento");
        helpShot("2c-ajuda");
        hover(table.opponentArea(0), "2a-detalhe-oponente");
        hover(table.tileArea(1), "2b-detalhe-assistente");
        act(() -> table.press(human.getCharcoalBurner().getCard()));
        Card planned = human.getHand().getFirst();
        act(() -> table.press(planned));
        shot("2d-carta-selecionada");  // a 1ª da mão, a mais coberta: selecionada, aparece inteira
        act(game::planSelected);
        shot("3-planejado");
        act(game::advance);           // fim do planejamento
        act(game::advance);           // Pôr do Sol: oponentes antes do humano jogam
        shot("4-producao");
        produceAndChain();
        shot("5-construcao");
        act(() -> table.press(human.getCharcoalBurner().getCard()));
        act(() -> table.press(human.getCharcoalBurner().getCard()));
        shot("6-pagamento");
        Rectangle tile = table.tileArea(0);
        act(() -> table.pressAt(tile.x + 10, tile.y + 10));
        shot("6a-contratar");
        game.decline();               // oponentes que jogam depois do humano: narração no meio da animação
        table.advanceTime(BOT_STEP_SAMPLE_MS);
        shot("7-oponentes-jogando");
        settle();
        shot("8-fim-rodada");
    }

    /** Joga rodadas até o fim da partida (o humano só produz com o trabalhador na Carvoaria). */
    private void untilGameOver() {
        int round = 0;
        while (game.phase() != Game.Phase.GAME_OVER && round < 40) {
            act(game::advance);       // Fase I
            act(game::advance);       // Nascer do Sol
            Building target = human.getBuildings().stream().filter(human::canPlaceWorker)
                    .reduce((a, b) -> b).orElseThrow();
            act(() -> table.press(target.getCard()));
            if (round == 1) {
                shot("1-planejamento");
                hover(table.opponentArea(game.getState().opponents().size() - 1), "1a-detalhe-oponente");
                Rectangle covered = table.cardArea(human.getBuildings().get(2).getCard());
                table.hoverAt(covered.x + 20, covered.y + covered.height - 20);
                shot("1b-estabelecimento-inteiro");
                table.hoverAt(covered.x + covered.width - 5, covered.y + covered.height - 20); // parte que estava coberta
                shot("1c-estabelecimento-segue");
                table.hoverAt(-1, -1);
            }
            act(game::advance);
            act(game::advance);       // Pôr do Sol
            if (round == 1) shot("2-producao");
            produceAndChain();
            if (round == 1) shot("3-construcao");
            act(game::decline);
            round++;
        }
        shot("4-fim");
        act(game::advance);           // resultado
        shot("5-resultado");
        Rectangle again = table.playAgainArea();
        hover(again, "5a-resultado-botao");
    }

    private void produceAndChain() {
        for (int guard = 0; guard < 30; guard++) {
            Game.Phase phase = game.phase();
            if (phase == Game.Phase.PRODUCE) {
                act(game::advance);
                if (game.phase() == Game.Phase.PRODUCE) act(game::decline);
            } else if (phase == Game.Phase.CHAIN) {
                act(game::advance);
            } else {
                return;
            }
        }
    }

    /** Captura com o mouse parado no meio da área. */
    private void hover(Rectangle area, String name) {
        table.hoverAt(area.x + area.width / 2, area.y + area.height / 2);
        shot(name);
        table.hoverAt(-1, -1);
    }

    private void act(Runnable action) {
        action.run();
        settle();
    }

    private void settle() {
        table.finishAnimations();
    }

    /** A ajuda aberta sobre a mesa, como no glass pane da janela. */
    private void helpShot(String name) {
        HelpOverlay help = new HelpOverlay();
        help.openFor(WIDTH, HEIGHT);
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        table.paint(g);
        help.paint(g);
        g.dispose();
        write(image, name);
    }

    private void shot(String name) {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        table.paint(g);
        g.dispose();
        write(image, name);
    }

    private void write(BufferedImage image, String name) {
        try {
            ImageIO.write(image, "png", new File(dir, prefix + "-" + name + ".png"));
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }
    }
}
