package monopoly;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Random;
import java.util.function.IntSupplier;

import monopoly.web.ApiServer;

/** Dependency-free test runner: {@code java -cp out monopoly.GameTests} exits non-zero on failure. */
public final class GameTests {
    private static int checks = 0;
    private static int failures = 0;

    static void check(boolean ok, String what) {
        checks++;
        if (!ok) {
            failures++;
            System.err.println("FAIL: " + what);
        }
    }

    // ---------------------------------------------------------------- helpers

    /** Dice that return the given values in order (two per roll). */
    static IntSupplier dice(int... values) {
        Deque<Integer> q = new ArrayDeque<>();
        for (int v : values) q.add(v);
        return () -> {
            Integer v = q.poll();
            if (v == null) throw new IllegalStateException("test ran out of dice");
            return v;
        };
    }

    static List<Game.Spec> two(Strategy a, Strategy b) {
        return List.of(new Game.Spec("Ann", a), new Game.Spec("Bob", b));
    }

    static Game game(List<Game.Spec> specs, IntSupplier dice, Card... deck) {
        return new Game(specs, Rules.standard(), new Random(1), dice, deck.length == 0 ? List.of() : List.of(deck));
    }

    // ---------------------------------------------------------------- board

    static void testBoard() {
        check(Board.squares().size() == 24, "24 squares");
        for (Color c : new Color[] {Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW})
            check(Board.group(c).size() == 3, c + " group has three properties");
        check(Board.nextAirport(0) == 2 && Board.nextAirport(2) == 10 && Board.nextAirport(19) == 2, "airport order wraps");
        check(Board.square(0).kind() == Square.Kind.PARKING && Board.square(12).kind() == Square.Kind.PRISON, "layout");
        check(Board.square(1).price() == 100 && Board.square(3).price() == 180, "land and cinema prices");
    }

    // ---------------------------------------------------------------- movement and buying

    static void testMovementAndSalary() {
        Game g = game(two(Strategy.BALANCED, Strategy.BALANCED), dice(3, 4));
        Player ann = g.players().get(0);
        g.roll();
        check(ann.position() == 7, "moved 3+4 squares");
        check(g.currentIndex() == 1, "turn passes to the next player");

        Game w = game(two(Strategy.BALANCED, Strategy.BALANCED), dice(4, 5));
        Player a = w.players().get(0);
        w.teleport(a, 20);
        w.roll();   // 20 + 9 = 29 -> square 5 (Lottery Hall), passing Start
        check(a.position() == 5, "wrapped around the board");
        check(a.cash() == 600 + w.rules().salary() + w.rules().awardPrize(), "salary for passing Start plus the prize (cash " + a.cash() + ")");
    }

    static void testBuyingAndRent() {
        Game k = game(two(Strategy.AGGRESSIVE, Strategy.AGGRESSIVE), dice(3, 3, 3, 3));
        Player ann = k.players().get(0), bob = k.players().get(1);
        k.teleport(ann, 2);   // + 6 -> square 8 "Rose Avenue" (red land, 140)
        k.roll();
        check(k.ownerOf(8) == 0 && ann.properties().contains(8), "aggressive bot buys an unowned property");
        check(ann.cash() == 600 - 140, "price deducted");
        k.teleport(bob, 2);   // + 6 -> square 8, now owned by Ann
        k.roll();
        check(k.rentOf(8) == 140 * 80 / 100, "rent is a percentage of the price");
        check(bob.cash() == 600 - 112 && ann.cash() == 460 + 112, "rent moves from tenant to owner");
    }

    static void testMonopolyDoublesRent() {
        Game g = game(two(Strategy.BALANCED, Strategy.BALANCED), dice(1, 1));
        Player ann = g.players().get(0);
        int base = g.rentOf(8);
        check(base == 0, "no rent on an unowned square");
        g.grant(ann, 3);
        g.grant(ann, 8);
        check(g.rentOf(8) == 140 * 80 / 100, "two of three: normal rent");
        check(!g.ownsWholeGroup(0, Color.RED), "group is incomplete");
        g.grant(ann, 17);
        check(g.ownsWholeGroup(0, Color.RED), "owning the full colour group");
        check(g.rentOf(8) == 2 * (140 * 80 / 100), "monopoly doubles the rent");
    }

    static void testHumanBuyFlow() {
        Game g = game(two(Strategy.HUMAN, Strategy.BALANCED), dice(3, 3, 1, 1));
        Player ann = g.players().get(0);
        g.teleport(ann, 2);
        g.roll();
        check(g.phase() == Game.Phase.BUY && g.pendingSquare() == 8, "human is asked before buying");
        boolean threw = false;
        try { g.roll(); } catch (IllegalStateException e) { threw = true; }
        check(threw, "cannot roll while a purchase is pending");
        g.decideBuy(true);
        check(g.ownerOf(8) == 0 && ann.cash() == 600 - 140, "purchase completes");
        check(g.phase() == Game.Phase.ROLL && g.currentIndex() == 1, "turn passes on after the decision");

        Game h = game(two(Strategy.HUMAN, Strategy.BALANCED), dice(3, 3));
        h.teleport(h.players().get(0), 2);
        h.roll();
        h.decideBuy(false);
        check(h.ownerOf(8) == -1 && h.players().get(0).cash() == 600, "declining leaves the square with the bank");
    }

    static void testBotStrategies() {
        check(Strategy.AGGRESSIVE.wantsToBuy(150, 140), "aggressive buys whenever affordable");
        check(!Strategy.CAUTIOUS.wantsToBuy(500, 140), "cautious keeps a 400 reserve");
        check(Strategy.CAUTIOUS.wantsToBuy(600, 140), "cautious buys when rich enough");
        check(Strategy.BALANCED.wantsToBuy(400, 200) && !Strategy.BALANCED.wantsToBuy(399, 200), "balanced keeps 200");
        check(!Strategy.HUMAN.wantsToBuy(10_000, 1), "a human never auto-buys");
    }

    // ---------------------------------------------------------------- special squares

    static void testAirportAndRoad() {
        Game g = game(two(Strategy.BALANCED, Strategy.BALANCED), dice(1, 1));
        Player ann = g.players().get(0);
        g.roll();   // square 2: North Airport -> pay 50, fly to East Airport (10)
        check(ann.position() == 10, "flew to the next airport");
        check(ann.cash() == 600 - 50 && g.pot() == 50, "fee paid into the pot");

        Game r = game(two(Strategy.BALANCED, Strategy.BALANCED), dice(2, 2, 1, 2));
        Player a = r.players().get(0);
        r.roll();   // square 4: Highway One -> another roll
        check(r.currentIndex() == 0, "road gives the same player another roll");
        r.roll();   // 4 + 3 = 7
        check(a.position() == 7 && r.currentIndex() == 1, "extra turn is only granted once");
    }

    static void testAwardBankTaxPrisonParking() {
        Game g = game(two(Strategy.BALANCED, Strategy.BALANCED), dice(2, 3, 3, 3));
        Player ann = g.players().get(0), bob = g.players().get(1);
        g.roll();   // Ann: square 5 Lottery Hall
        check(ann.cash() == 700, "award prize collected");
        g.teleport(bob, 14);
        g.roll();   // Bob: 14 + 6 = 20 Central Bank, interest = min(100, 10% of 600) = 60
        check(bob.cash() == 660, "bank pays interest on cash");

        Game t = game(two(Strategy.BALANCED, Strategy.BALANCED), dice(4, 4));
        Player p = t.players().get(0);
        t.teleport(p, 8);
        t.roll();   // 8 + 8 = 16 Tax Office: 10% of 600 = 60
        check(p.cash() == 540 && t.pot() == 60, "tax is 10% of cash and goes to the pot");

        Game pr = game(two(Strategy.BALANCED, Strategy.BALANCED), dice(6, 6, 1, 1));
        Player q = pr.players().get(0);
        pr.roll();   // Ann: 12 = Prison
        check(q.prisonTurns() == 1, "landing on Prison jails the player");
        pr.roll();   // Bob rolls 1+1
        pr.roll();   // Ann is jailed: skips without rolling
        check(q.prisonTurns() == 0 && q.position() == 12, "a jailed player misses one turn and stays put");

        Game pk = game(two(Strategy.BALANCED, Strategy.BALANCED), dice(6, 6));
        Player z = pk.players().get(0);
        pk.teleport(z, 12);
        pk.addToPot(300);
        pk.roll();   // 12 + 12 = 24 -> Parking, passing Start
        check(z.position() == 0 && z.cash() == 600 + 300 + pk.rules().salary(), "parking pays out the pot plus salary");
        check(pk.pot() == 0, "pot is emptied");
    }

    // ---------------------------------------------------------------- chance

    static void testChanceCards() {
        // Ann at 17 rolls 3+3 -> 23 (Chance).
        Game g = game(two(Strategy.BALANCED, Strategy.BALANCED), dice(3, 3), Card.BANK_ERROR);
        Player ann = g.players().get(0);
        g.teleport(ann, 17);
        g.roll();
        check(ann.cash() == 750, "bank error pays 150");

        Game p = game(two(Strategy.BALANCED, Strategy.BALANCED), dice(3, 3), Card.TO_PRISON);
        p.teleport(p.players().get(0), 17);
        p.roll();
        check(p.players().get(0).position() == 12 && p.players().get(0).prisonTurns() == 1, "card sends to prison");

        Game b = game(two(Strategy.BALANCED, Strategy.BALANCED), dice(3, 3), Card.BIRTHDAY);
        b.teleport(b.players().get(0), 17);
        b.roll();
        check(b.players().get(0).cash() == 630 && b.players().get(1).cash() == 570, "birthday: 30 from each other player");

        Game e = game(two(Strategy.BALANCED, Strategy.BALANCED), dice(3, 3), Card.GENEROUS);
        e.teleport(e.players().get(0), 17);
        e.roll();
        check(e.players().get(0).cash() == 575 && e.players().get(1).cash() == 625, "generous: pay 25 to each other player");

        Game r = game(two(Strategy.BALANCED, Strategy.BALANCED), dice(3, 3), Card.REPAIRS);
        r.teleport(r.players().get(0), 17);
        r.grant(r.players().get(0), 1);
        r.grant(r.players().get(0), 11);
        r.roll();
        check(r.players().get(0).cash() == 600 - 40 && r.pot() == 40, "repairs cost 20 per property");

        Game t = game(two(Strategy.BALANCED, Strategy.BALANCED), dice(3, 3), Card.TO_START);
        t.teleport(t.players().get(0), 17);
        t.addToPot(100);
        t.roll();
        check(t.players().get(0).position() == 0 && t.players().get(0).cash() == 600 + 20 + 100, "advance to Start: salary and pot");

        Game f = game(two(Strategy.BALANCED, Strategy.BALANCED), dice(3, 3), Card.TO_AIRPORT);
        f.teleport(f.players().get(0), 17);
        f.roll();
        check(f.players().get(0).position() == 2 && f.players().get(0).cash() == 620, "free flight wraps past Start but charges no fee");

        Game d = game(two(Strategy.BALANCED, Strategy.BALANCED), dice(3, 3), Card.BACK_THREE);
        d.teleport(d.players().get(0), 17);
        d.roll();
        check(d.players().get(0).position() == 20 && d.players().get(0).cash() == 600 + 60, "move back three lands on the bank");
    }

    // ---------------------------------------------------------------- bankruptcy and game end

    static void testBankruptcyAndSelling() {
        // Ann owns a property, is nearly broke, and lands on Bob's land.
        Game g = game(two(Strategy.BALANCED, Strategy.BALANCED), dice(3, 3));
        Player ann = g.players().get(0), bob = g.players().get(1);
        g.grant(bob, 8);            // Bob owns Rose Avenue (rent 112)
        g.grant(ann, 1);            // Ann owns Garden Street (price 100, sells for 50)
        ann.cash = 70;
        g.teleport(ann, 2);
        g.roll();                   // lands on 8, owes 112; sells Garden Street for 50 -> 120 -> pays
        check(ann.alive() && ann.cash() == 8, "short on cash: sells a property to the bank and survives (cash " + ann.cash() + ")");
        check(g.ownerOf(1) == -1 && ann.properties().isEmpty(), "sold property returns to the bank");

        Game h = game(two(Strategy.BALANCED, Strategy.BALANCED), dice(3, 3));
        Player a = h.players().get(0), b = h.players().get(1);
        h.grant(b, 8);
        a.cash = 30;
        h.teleport(a, 2);
        h.roll();
        check(!a.alive() && b.cash() == 600 + 30, "bankrupt player pays what they have to the creditor");
        check(h.isOver() && h.winner() == b, "the last player standing wins");
    }

    static void testRoundLimit() {
        Rules rules = Rules.standard().withMaxRounds(2);
        Game g = new Game(two(Strategy.BALANCED, Strategy.BALANCED), rules, new Random(1), dice(6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6), List.of());
        g.grant(g.players().get(1), 1);   // Bob is richer in assets
        for (int i = 0; i < 12 && !g.isOver(); i++) g.roll();
        check(g.isOver(), "game ends at the round limit");
        check(g.winner() != null, "round limit still produces a winner");
    }

    // ---------------------------------------------------------------- determinism and invariants

    static String fingerprint(long seed) {
        Game g = new Game(List.of(new Game.Spec("A", Strategy.BALANCED), new Game.Spec("B", Strategy.AGGRESSIVE),
                new Game.Spec("C", Strategy.CAUTIOUS)), Rules.standard(), seed);
        g.runBots();
        return String.join("|", g.log()) + g.winner().name();
    }

    static void testDeterminism() {
        check(fingerprint(42).equals(fingerprint(42)), "same seed gives an identical game");
        check(!fingerprint(42).equals(fingerprint(43)), "different seeds give different games");
    }

    /** Plays many random games and checks rules that must always hold. */
    static void testInvariants() {
        boolean ok = true;
        String why = "";
        Strategy[] all = {Strategy.CAUTIOUS, Strategy.BALANCED, Strategy.AGGRESSIVE};
        for (int seed = 0; seed < 600 && ok; seed++) {
            int n = 2 + seed % 3;
            List<Game.Spec> specs = new ArrayList<>();
            for (int i = 0; i < n; i++) specs.add(new Game.Spec("P" + i, all[(seed + i) % 3]));
            Game g = new Game(specs, Rules.standard(), seed);
            g.setLogging(false);
            int guard = 0;
            while (!g.isOver() && guard++ < 100_000) {
                g.roll();
                for (Player p : g.players()) {
                    if (p.alive() && p.cash() < 0) { ok = false; why = "negative cash, seed " + seed; }
                    if (!p.alive() && !p.properties().isEmpty()) { ok = false; why = "bankrupt player keeps properties, seed " + seed; }
                    for (int sq : p.properties())
                        if (g.ownerOf(sq) != g.players().indexOf(p)) { ok = false; why = "owner mismatch, seed " + seed; }
                }
                int owned = 0;
                for (int i = 0; i < Board.SIZE; i++) if (g.ownerOf(i) >= 0) owned++;
                int listed = 0;
                for (Player p : g.players()) listed += p.properties().size();
                if (owned != listed) { ok = false; why = "ownership out of sync, seed " + seed; }
            }
            if (!g.isOver()) { ok = false; why = "game did not terminate, seed " + seed; }
            if (g.winner() == null) { ok = false; why = "no winner, seed " + seed; }
        }
        check(ok, "600 random games keep every invariant" + (ok ? "" : ": " + why));
    }

    static void testSimulator() {
        Simulator.Summary s = Simulator.run(List.of(Strategy.BALANCED, Strategy.BALANCED, Strategy.BALANCED), 300, 7,
                Rules.standard(), false);
        int wins = 0;
        for (int w : s.winsBySeat()) wins += w;
        check(wins == 300, "every game has exactly one winner");
        check(s.averageRounds() > 5, "games take more than a few rounds");
    }

    // ---------------------------------------------------------------- HTTP API

    static HttpResponse<String> send(HttpClient http, String method, String url, String body) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url));
        b.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    static void testApi() throws Exception {
        ApiServer server = new ApiServer("127.0.0.1", 0);
        server.start();
        try {
            HttpClient http = HttpClient.newHttpClient();
            String base = "http://127.0.0.1:" + server.port();
            check(send(http, "GET", base + "/", null).body().contains("City"), "serves the UI");
            check(send(http, "POST", base + "/api/games", "{\"players\":[{\"name\":\"Solo\"}]}").statusCode() == 400,
                    "one player is rejected");
            HttpResponse<String> r = send(http, "POST", base + "/api/games",
                    "{\"seed\":5,\"players\":[{\"name\":\"Ann\",\"strategy\":\"HUMAN\"},{\"name\":\"Bot\",\"strategy\":\"BALANCED\"}]}");
            check(r.statusCode() == 201 && r.body().contains("\"phase\":\"ROLL\""), "game created and waiting for the human");
            String id = r.body().replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");
            check(send(http, "POST", base + "/api/games/" + id + "/buy", "{\"buy\":true}").statusCode() == 409,
                    "buying without a pending offer is a conflict");
            r = send(http, "POST", base + "/api/games/" + id + "/roll", "{}");
            check(r.statusCode() == 200 && r.body().contains("\"squares\""), "roll returns the new state");
            check(r.body().matches(".*\"dice\":\\[[1-6],[1-6]\\].*"), "dice are serialized as a JSON array");
            check(send(http, "GET", base + "/api/games/nope", null).statusCode() == 404, "unknown game -> 404");
            check(send(http, "POST", base + "/api/games", "{bad").statusCode() == 400, "bad JSON -> 400");

            // Play a whole game through the API with a human who always buys.
            r = send(http, "POST", base + "/api/games",
                    "{\"seed\":9,\"players\":[{\"name\":\"H\",\"strategy\":\"HUMAN\"},{\"name\":\"B\",\"strategy\":\"AGGRESSIVE\"}]}");
            String gid = r.body().replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");
            int steps = 0;
            while (!r.body().contains("\"phase\":\"OVER\"") && steps++ < 5000) {
                String path = r.body().contains("\"phase\":\"BUY\"") ? "/buy" : "/roll";
                r = send(http, "POST", base + "/api/games/" + gid + path, path.equals("/buy") ? "{\"buy\":true}" : "{}");
                if (r.statusCode() != 200) break;
            }
            check(r.body().contains("\"phase\":\"OVER\""), "a full game can be played through the API");
        } finally {
            server.stop();
        }
    }

    public static void main(String[] args) throws Exception {
        testBoard();
        testMovementAndSalary();
        testBuyingAndRent();
        testMonopolyDoublesRent();
        testHumanBuyFlow();
        testBotStrategies();
        testAirportAndRoad();
        testAwardBankTaxPrisonParking();
        testChanceCards();
        testBankruptcyAndSelling();
        testRoundLimit();
        testDeterminism();
        testInvariants();
        testSimulator();
        testApi();
        System.out.println((checks - failures) + "/" + checks + " checks passed");
        System.exit(failures == 0 ? 0 : 1);
    }
}
