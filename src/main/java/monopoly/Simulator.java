package monopoly;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Plays many all-bot games to measure how balanced the rules are: does going first help, and which
 * buying strategy wins more often?
 */
public final class Simulator {
    private Simulator() {}

    public record Summary(int games, int[] winsBySeat, Map<Strategy, Integer> winsByStrategy, double averageRounds,
                          int endedByRoundLimit, int bankruptcies) {}

    /**
     * @param seats        strategies at the table, in turn order (bots only)
     * @param rotateSeats  when true each game rotates who sits where, so a strategy is not tied to a seat
     */
    public static Summary run(List<Strategy> seats, int games, long seed, Rules rules, boolean rotateSeats) {
        int n = seats.size();
        int[] winsBySeat = new int[n];
        Map<Strategy, Integer> winsByStrategy = new EnumMap<>(Strategy.class);
        long totalRounds = 0;
        int limit = 0;
        int bankruptcies = 0;

        for (int g = 0; g < games; g++) {
            int shift = rotateSeats ? g % n : 0;
            List<Game.Spec> specs = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                Strategy s = seats.get((i + shift) % n);
                specs.add(new Game.Spec("P" + (i + 1) + "-" + s, s));
            }
            Game game = new Game(specs, rules, seed + g);
            game.setLogging(false);
            game.runBots();

            int seat = game.players().indexOf(game.winner());
            if (seat >= 0) {
                winsBySeat[seat]++;
                winsByStrategy.merge(game.winner().strategy(), 1, Integer::sum);
            }
            totalRounds += game.round();
            int alive = 0;
            for (Player p : game.players()) if (p.alive()) alive++; else bankruptcies++;
            if (alive > 1) limit++;
        }
        return new Summary(games, winsBySeat, winsByStrategy, (double) totalRounds / games, limit, bankruptcies);
    }
}
