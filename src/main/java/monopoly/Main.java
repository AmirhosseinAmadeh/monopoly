package monopoly;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import monopoly.web.ApiServer;

/**
 * <pre>
 * java monopoly.Main web [--port 8080] [--host 127.0.0.1]
 * java monopoly.Main cli  Ali Sara bot:balanced        hot-seat game in the terminal
 * java monopoly.Main simulate [--games 4000] [--seed 1] balance report from all-bot games
 * </pre>
 */
public final class Main {
    private Main() {}

    public static void main(String[] args) throws IOException {
        if (args.length == 0) usage();
        switch (args[0]) {
            case "web" -> web(args);
            case "cli" -> cli(Arrays.copyOfRange(args, 1, args.length));
            case "simulate" -> simulate(args);
            default -> usage();
        }
    }

    private static void usage() {
        System.err.println("usage: java monopoly.Main web [--port N] [--host H]\n"
                + "       java monopoly.Main cli <name | bot:cautious|balanced|aggressive>...   (2-4 players)\n"
                + "       java monopoly.Main simulate [--games N] [--seed S]");
        System.exit(2);
    }

    // ---------------------------------------------------------------- web

    private static void web(String[] args) throws IOException {
        int port = 8080;
        String host = "127.0.0.1";
        for (int i = 1; i < args.length; i++) {
            switch (args[i]) {
                case "--port" -> port = Integer.parseInt(args[++i]);
                case "--host" -> host = args[++i];
                default -> usage();
            }
        }
        ApiServer server = new ApiServer(host, port);
        server.start();
        System.out.println("City Monopoly running at http://" + host + ":" + server.port() + "/");
    }

    // ---------------------------------------------------------------- simulate

    private static void simulate(String[] args) {
        int games = 4000;
        long seed = 1;
        for (int i = 1; i < args.length; i++) {
            switch (args[i]) {
                case "--games" -> games = Integer.parseInt(args[++i]);
                case "--seed" -> seed = Long.parseLong(args[++i]);
                default -> usage();
            }
        }
        Rules rules = Rules.standard();

        Simulator.Summary seats = Simulator.run(List.of(Strategy.BALANCED, Strategy.BALANCED, Strategy.BALANCED,
                Strategy.BALANCED), games, seed, rules, false);
        System.out.printf("## Does turn order matter? (%,d games, 4 balanced bots)%n%n", games);
        System.out.println("| seat | wins | win rate |");
        System.out.println("|---|---:|---:|");
        for (int i = 0; i < seats.winsBySeat().length; i++)
            System.out.printf("| %d%s | %d | %.1f%% |%n", i + 1, i == 0 ? " (first)" : "", seats.winsBySeat()[i],
                    100.0 * seats.winsBySeat()[i] / games);
        System.out.printf("%naverage length: %.1f rounds, %.1f%% of games hit the %d-round limit%n%n",
                seats.averageRounds(), 100.0 * seats.endedByRoundLimit() / games, rules.maxRounds());

        List<Strategy> mix = List.of(Strategy.CAUTIOUS, Strategy.BALANCED, Strategy.AGGRESSIVE);
        Simulator.Summary strat = Simulator.run(mix, games, seed + 1_000_000, rules, true);
        System.out.printf("## Which strategy wins? (%,d games, one of each, seats rotated)%n%n", games);
        System.out.println("| strategy | wins | win rate |");
        System.out.println("|---|---:|---:|");
        for (Strategy s : mix) {
            int w = strat.winsByStrategy().getOrDefault(s, 0);
            System.out.printf("| %s | %d | %.1f%% |%n", s, w, 100.0 * w / games);
        }
        System.out.printf("%naverage length: %.1f rounds, %.1f%% of games hit the round limit%n",
                strat.averageRounds(), 100.0 * strat.endedByRoundLimit() / games);
    }

    // ---------------------------------------------------------------- cli

    private static void cli(String[] names) throws IOException {
        if (names.length < 2 || names.length > 4) usage();
        List<Game.Spec> specs = new ArrayList<>();
        for (String n : names) {
            if (n.startsWith("bot:")) {
                Strategy s = Strategy.valueOf(n.substring(4).toUpperCase());
                specs.add(new Game.Spec("Bot-" + s.name().toLowerCase(), s));
            } else {
                specs.add(new Game.Spec(n, Strategy.HUMAN));
            }
        }
        Game game = new Game(specs, Rules.standard(), System.nanoTime());
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
        int printed = 0;
        while (!game.isOver()) {
            game.runBots();
            printed = flush(game, printed);
            if (game.isOver()) break;
            Player p = game.currentPlayer();
            if (game.phase() == Game.Phase.BUY) {
                Square s = Board.square(game.pendingSquare());
                System.out.printf("%s, buy %s for %d? You have %d. (y/n) ", p.name(), s.name(), s.price(), p.cash());
                String line = in.readLine();
                if (line == null) return;
                game.decideBuy(line.trim().toLowerCase().startsWith("y"));
            } else {
                System.out.printf("%s (cash %d, at %s) - press Enter to roll ", p.name(), p.cash(),
                        Board.square(p.position()).name());
                if (in.readLine() == null) return;
                game.roll();
            }
            printed = flush(game, printed);
        }
        System.out.println("\nGame over. Winner: " + (game.winner() == null ? "nobody" : game.winner().name()));
    }

    private static int flush(Game game, int printed) {
        List<String> log = game.log();
        for (int i = printed; i < log.size(); i++) System.out.println("  " + log.get(i));
        return log.size();
    }
}
