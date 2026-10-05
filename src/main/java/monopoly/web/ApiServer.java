package monopoly.web;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import monopoly.Board;
import monopoly.Game;
import monopoly.Player;
import monopoly.Rules;
import monopoly.Square;
import monopoly.Strategy;
import monopoly.util.Json;

/** JSON API and the browser UI. Games live in memory, one per id. */
public final class ApiServer {
    private static final int MAX_GAMES = 200;

    private final HttpServer server;
    private final Map<String, Game> games = new ConcurrentHashMap<>();
    private final SecureRandom ids = new SecureRandom();

    public ApiServer(String host, int port) throws IOException {
        server = HttpServer.create(new InetSocketAddress(host, port), 0);
        server.createContext("/", this::handle);
        server.setExecutor(Executors.newFixedThreadPool(4));
    }

    public void start() { server.start(); }

    public void stop() { server.stop(0); }

    public int port() { return server.getAddress().getPort(); }

    // ---------------------------------------------------------------- routing

    private static final class HttpError extends RuntimeException {
        private static final long serialVersionUID = 1L;
        final int status;

        HttpError(int status, String message) {
            super(message);
            this.status = status;
        }
    }

    private void handle(HttpExchange ex) throws IOException {
        try {
            String method = ex.getRequestMethod();
            String path = ex.getRequestURI().getPath();
            if (method.equals("GET") && (path.equals("/") || path.equals("/index.html"))) {
                page(ex);
                return;
            }
            String[] seg = path.split("/");   // "", "api", "games", id, action
            if (seg.length < 3 || !seg[1].equals("api") || !seg[2].equals("games")) throw new HttpError(404, "not found");

            if (seg.length == 3 && method.equals("POST")) {
                send(ex, 201, state(createGame(body(ex))));
            } else if (seg.length == 4 && method.equals("GET")) {
                send(ex, 200, state(Map.entry(seg[3], find(seg[3]))));
            } else if (seg.length == 5 && method.equals("POST")) {
                Game g = find(seg[3]);
                synchronized (g) {
                    switch (seg[4]) {
                        case "roll" -> {
                            requireHumanTurn(g, Game.Phase.ROLL);
                            g.roll();
                            g.runBots();
                        }
                        case "buy" -> {
                            requireHumanTurn(g, Game.Phase.BUY);
                            Object v = body(ex).get("buy");
                            if (!(v instanceof Boolean)) throw new HttpError(400, "\"buy\" must be true or false");
                            g.decideBuy((Boolean) v);
                            g.runBots();
                        }
                        default -> throw new HttpError(404, "no such action: " + seg[4]);
                    }
                }
                send(ex, 200, state(Map.entry(seg[3], g)));
            } else {
                throw new HttpError(404, "not found");
            }
        } catch (HttpError e) {
            send(ex, e.status, Map.of("error", e.getMessage()));
        } catch (Json.JsonException | ClassCastException | NullPointerException | IllegalArgumentException e) {
            send(ex, 400, Map.of("error", "invalid request: " + e.getMessage()));
        } catch (RuntimeException e) {
            send(ex, 500, Map.of("error", "internal error"));
        }
    }

    private static void requireHumanTurn(Game g, Game.Phase expected) {
        if (g.isOver()) throw new HttpError(409, "the game is over");
        if (g.phase() != expected) throw new HttpError(409, "not allowed now: the game is waiting for " + g.phase());
        if (!g.currentPlayer().isHuman()) throw new HttpError(409, "it is a bot's turn");
    }

    private Game find(String id) {
        Game g = games.get(id);
        if (g == null) throw new HttpError(404, "unknown game: " + id);
        return g;
    }

    @SuppressWarnings("unchecked")
    private Map.Entry<String, Game> createGame(Map<String, Object> body) {
        List<Object> raw = (List<Object>) body.get("players");
        if (raw == null || raw.size() < 2 || raw.size() > 4) throw new HttpError(400, "2 to 4 players are required");
        List<Game.Spec> specs = new ArrayList<>();
        int n = 1;
        for (Object o : raw) {
            Map<String, Object> m = (Map<String, Object>) o;
            String name = m.get("name") == null ? "Player " + n : String.valueOf(m.get("name")).trim();
            if (name.isEmpty() || name.length() > 20) throw new HttpError(400, "player names must be 1-20 characters");
            Strategy s = Strategy.valueOf(String.valueOf(m.getOrDefault("strategy", "HUMAN")).toUpperCase());
            specs.add(new Game.Spec(name, s));
            n++;
        }
        long seed = body.get("seed") instanceof Number num ? num.longValue() : System.nanoTime();
        if (games.size() >= MAX_GAMES) games.clear();   // simple memory cap for a local demo server
        Game g = new Game(specs, Rules.standard(), seed);
        String id = Long.toString(ids.nextLong() >>> 1, 36);
        games.put(id, g);
        synchronized (g) {
            g.runBots();
        }
        return Map.entry(id, g);
    }

    // ---------------------------------------------------------------- JSON view

    private static Map<String, Object> state(Map.Entry<String, Game> e) {
        Map<String, Object> m = state(e.getValue());
        m.put("id", e.getKey());
        return m;
    }

    private static Map<String, Object> state(Game g) {
        synchronized (g) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("phase", g.phase().name());
            m.put("round", g.round());
            m.put("maxRounds", g.rules().maxRounds());
            m.put("pot", g.pot());
            m.put("current", g.currentIndex());
            int[] dice = g.lastDice();
            m.put("dice", List.of(dice[0], dice[1]));
            m.put("winner", g.winner() == null ? null : g.players().indexOf(g.winner()));
            m.put("pending", g.pendingSquare() < 0 ? null : g.pendingSquare());

            List<Object> players = new ArrayList<>();
            for (Player p : g.players()) {
                Map<String, Object> pm = new LinkedHashMap<>();
                pm.put("name", p.name());
                pm.put("strategy", p.strategy().name());
                pm.put("cash", p.cash());
                pm.put("position", p.position());
                pm.put("alive", p.alive());
                pm.put("prisonTurns", p.prisonTurns());
                pm.put("netWorth", g.netWorth(p));
                pm.put("properties", p.properties());
                players.add(pm);
            }
            m.put("players", players);

            List<Object> squares = new ArrayList<>();
            for (Square s : Board.squares()) {
                Map<String, Object> sm = new LinkedHashMap<>();
                sm.put("index", s.index());
                sm.put("kind", s.kind().name());
                sm.put("name", s.name());
                sm.put("color", s.color().name());
                sm.put("price", s.price());
                sm.put("owner", g.ownerOf(s.index()));
                sm.put("rent", g.rentOf(s.index()));
                squares.add(sm);
            }
            m.put("squares", squares);

            List<String> log = g.log();
            m.put("log", new ArrayList<>(log.subList(Math.max(0, log.size() - 40), log.size())));
            return m;
        }
    }

    // ---------------------------------------------------------------- http helpers

    private static Map<String, Object> body(HttpExchange ex) throws IOException {
        try (InputStream in = ex.getRequestBody()) {
            byte[] bytes = in.readNBytes(100_000);
            return bytes.length == 0 ? new LinkedHashMap<>() : Json.parseObject(new String(bytes, StandardCharsets.UTF_8));
        }
    }

    private static void page(HttpExchange ex) throws IOException {
        try (InputStream in = ApiServer.class.getResourceAsStream("/web/index.html")) {
            if (in == null) throw new HttpError(500, "UI resource missing from classpath");
            byte[] bytes = in.readAllBytes();
            ex.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
            ex.sendResponseHeaders(200, bytes.length);
            try (OutputStream out = ex.getResponseBody()) { out.write(bytes); }
        }
    }

    private static void send(HttpExchange ex, int status, Object payload) throws IOException {
        byte[] bytes = Json.stringify(payload).getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = ex.getResponseBody()) { out.write(bytes); }
    }
}
