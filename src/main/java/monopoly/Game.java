package monopoly;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Random;
import java.util.function.IntSupplier;

/**
 * The rules engine. It is a small state machine: {@link #roll()} plays the current player's turn and
 * either finishes it or stops in {@link Phase#BUY} to ask a human whether to buy the property they
 * landed on ({@link #decideBuy}). Bots decide on their own, and {@link #runBots()} plays until a human
 * has to act or the game ends.
 *
 * <p>Everything random (dice, deck order) comes from the seed given at construction, so a game can be
 * replayed exactly. The class is not thread-safe; callers synchronize on the instance.
 */
public final class Game {

    public enum Phase { ROLL, BUY, OVER }

    /** A player to seat at the table. */
    public record Spec(String name, Strategy strategy) {}

    private static final int PRISON_SQUARE = 12;
    private static final int BANK_SQUARE = 20;

    private final Rules rules;
    private final Random rng;
    private final IntSupplier die;
    private final List<Player> players = new ArrayList<>();
    private final int[] owner = new int[Board.SIZE];
    private final Deque<Card> deck = new ArrayDeque<>();
    private final List<String> log = new ArrayList<>();
    private boolean logging = true;

    private Phase phase = Phase.ROLL;
    private int current;
    private int round = 1;
    private int pot;
    private int pendingSquare = -1;
    private boolean extraTurn;
    private int[] lastDice = {0, 0};
    private Player winner;

    public Game(List<Spec> specs, Rules rules, long seed) {
        this(specs, rules, new Random(seed), null, null);
    }

    /** Test hook: custom dice and a fixed deck order. */
    Game(List<Spec> specs, Rules rules, Random rng, IntSupplier dice, List<Card> fixedDeck) {
        if (specs.size() < 2 || specs.size() > 4) throw new IllegalArgumentException("2 to 4 players are required");
        this.rules = rules;
        this.rng = rng;
        this.die = dice != null ? dice : () -> rng.nextInt(6) + 1;
        for (Spec s : specs) players.add(new Player(s.name(), s.strategy(), rules.startCash()));
        java.util.Arrays.fill(owner, -1);
        if (fixedDeck != null) deck.addAll(fixedDeck);
        else refillDeck();
    }

    // ================================================================ read access

    public Phase phase() { return phase; }

    public boolean isOver() { return phase == Phase.OVER; }

    public Player currentPlayer() { return players.get(current); }

    public int currentIndex() { return current; }

    public List<Player> players() { return Collections.unmodifiableList(players); }

    /** Index of the player owning square {@code i}, or -1 when it belongs to the bank. */
    public int ownerOf(int i) { return owner[i]; }

    public int pot() { return pot; }

    public int round() { return round; }

    public Rules rules() { return rules; }

    /** Square a human is being asked to buy, or -1. */
    public int pendingSquare() { return pendingSquare; }

    public Player winner() { return winner; }

    public int[] lastDice() { return lastDice.clone(); }

    public List<String> log() { return Collections.unmodifiableList(log); }

    public void setLogging(boolean on) { this.logging = on; }

    /** Cash plus the purchase price of every property owned. */
    public int netWorth(Player p) {
        int total = p.cash;
        for (int sq : p.properties) total += Board.square(sq).price();
        return total;
    }

    public int rentOf(int squareIndex) {
        Square s = Board.square(squareIndex);
        if (!s.buyable() || owner[squareIndex] < 0) return 0;
        int rent = s.price() * rules.rentPercent() / 100;
        return ownsWholeGroup(owner[squareIndex], s.color()) ? rent * 2 : rent;
    }

    public boolean ownsWholeGroup(int playerIndex, Color color) {
        for (int idx : Board.group(color)) if (owner[idx] != playerIndex) return false;
        return true;
    }

    // ================================================================ actions

    /** Plays the current player's roll. Only valid in {@link Phase#ROLL}. */
    public void roll() {
        if (phase != Phase.ROLL) throw new IllegalStateException("cannot roll in phase " + phase);
        Player p = currentPlayer();

        if (p.prisonTurns > 0) {
            p.prisonTurns--;
            say(p.name() + " is in prison and misses this turn");
            endTurn();
            return;
        }

        int d1 = die.getAsInt();
        int d2 = die.getAsInt();
        lastDice = new int[] {d1, d2};
        say(p.name() + " rolls " + d1 + " + " + d2 + " = " + (d1 + d2));
        moveBy(p, d1 + d2);
        resolve(p, p.position, 0);
        if (phase == Phase.ROLL) endTurn();
    }

    /** Answers the pending buy question. Only valid in {@link Phase#BUY}. */
    public void decideBuy(boolean buy) {
        if (phase != Phase.BUY) throw new IllegalStateException("no purchase is pending");
        Player p = currentPlayer();
        int idx = pendingSquare;
        pendingSquare = -1;
        phase = Phase.ROLL;
        if (buy) purchase(p, idx);
        else say(p.name() + " passes on " + Board.square(idx).name());
        endTurn();
    }

    /** Plays bot turns until a human must act or the game is over. */
    public void runBots() {
        while (phase == Phase.ROLL && !currentPlayer().isHuman()) roll();
    }

    // ================================================================ turn flow

    private void endTurn() {
        Player p = currentPlayer();
        if (finishIfOver()) return;
        if (extraTurn && p.alive && p.prisonTurns == 0) {
            extraTurn = false;
            say(p.name() + " rolls again");
            return;
        }
        extraTurn = false;
        int previous = current;
        do {
            current = (current + 1) % players.size();
        } while (!players.get(current).alive);
        if (current <= previous) round++;
        finishIfOver();
    }

    private boolean finishIfOver() {
        List<Player> alive = new ArrayList<>();
        for (Player p : players) if (p.alive) alive.add(p);
        if (alive.size() <= 1) {
            winner = alive.isEmpty() ? null : alive.get(0);
        } else if (round > rules.maxRounds()) {
            winner = alive.stream().max(Comparator.comparingInt(this::netWorth)).orElseThrow();
            say("Round limit reached - the richest player wins");
        } else {
            return false;
        }
        phase = Phase.OVER;
        if (winner != null) say(winner.name() + " wins the game");
        return true;
    }

    // ================================================================ movement and squares

    private void moveBy(Player p, int steps) {
        int target = p.position + steps;
        if (target >= Board.SIZE) paySalary(p);
        p.position = target % Board.SIZE;
    }

    /** Moves forward to {@code target}, collecting the salary when the move passes Start. */
    private void moveForwardTo(Player p, int target) {
        if (target <= p.position) paySalary(p);
        p.position = target;
    }

    private void paySalary(Player p) {
        p.cash += rules.salary();
        say(p.name() + " passes Start and collects " + rules.salary());
    }

    private void resolve(Player p, int idx, int depth) {
        if (depth >= 2) return;
        Square s = Board.square(idx);
        switch (s.kind()) {
            case PARKING -> {
                if (pot > 0) {
                    say(p.name() + " collects " + pot + " from the parking pot");
                    p.cash += pot;
                    pot = 0;
                }
            }
            case LAND, CINEMA -> landOnProperty(p, s);
            case ROAD -> {
                extraTurn = true;
                say(p.name() + " drives on " + s.name() + " and gets another roll");
            }
            case AIRPORT -> {
                if (charge(p, rules.airportFee(), null, true)) {
                    int dest = Board.nextAirport(p.position);
                    say(p.name() + " pays " + rules.airportFee() + " and flies to " + Board.square(dest).name());
                    moveForwardTo(p, dest);
                }
            }
            case AWARD -> {
                p.cash += rules.awardPrize();
                say(p.name() + " wins " + rules.awardPrize() + " at " + s.name());
            }
            case BANK -> {
                int interest = Math.min(rules.bankInterestCap(), p.cash * rules.bankInterestPercent() / 100);
                p.cash += interest;
                say(p.name() + " earns " + interest + " interest at " + s.name());
            }
            case TAX -> {
                int tax = Math.max(rules.minTax(), p.cash * rules.taxPercent() / 100);
                say(p.name() + " owes " + tax + " tax");
                charge(p, tax, null, true);
            }
            case PRISON -> {
                p.prisonTurns = rules.prisonTurns();
                say(p.name() + " goes to prison");
            }
            case CHANCE -> drawCard(p, depth);
        }
    }

    private void landOnProperty(Player p, Square s) {
        int idx = s.index();
        int ownerIdx = owner[idx];
        int me = players.indexOf(p);
        if (ownerIdx == me) {
            say(p.name() + " visits their own " + s.name());
        } else if (ownerIdx < 0) {
            if (p.cash < s.price()) {
                say(p.name() + " lands on " + s.name() + " (" + s.price() + ") but cannot afford it");
            } else if (p.isHuman()) {
                pendingSquare = idx;
                phase = Phase.BUY;
                say(p.name() + " may buy " + s.name() + " for " + s.price());
            } else if (p.strategy().wantsToBuy(p.cash, s.price())) {
                purchase(p, idx);
            } else {
                say(p.name() + " passes on " + s.name());
            }
        } else {
            Player landlord = players.get(ownerIdx);
            int rent = rentOf(idx);
            say(p.name() + " owes " + rent + " rent to " + landlord.name() + " for " + s.name());
            charge(p, rent, landlord, false);
        }
    }

    private void purchase(Player p, int idx) {
        Square s = Board.square(idx);
        if (p.cash < s.price()) throw new IllegalStateException("not enough cash to buy " + s.name());
        p.cash -= s.price();
        owner[idx] = players.indexOf(p);
        p.properties.add(idx);
        say(p.name() + " buys " + s.name() + " for " + s.price());
    }

    // ================================================================ money

    /**
     * Takes {@code amount} from {@code p}, selling properties back to the bank (cheapest first) if the
     * player is short. The money goes to {@code creditor}, or into the parking pot when {@code toPot}.
     * A player who still cannot pay goes bankrupt. Returns whether the full amount was paid.
     */
    private boolean charge(Player p, int amount, Player creditor, boolean toPot) {
        if (p.cash < amount) raiseCash(p, amount);
        if (p.cash >= amount) {
            p.cash -= amount;
            if (creditor != null) creditor.cash += amount;
            else if (toPot) pot += amount;
            return true;
        }
        int rest = p.cash;
        p.cash = 0;
        if (creditor != null) creditor.cash += rest;
        else if (toPot) pot += rest;
        for (int idx : p.properties) owner[idx] = -1;
        p.properties.clear();
        p.alive = false;
        say(p.name() + " cannot pay " + amount + " and is bankrupt");
        return false;
    }

    private void raiseCash(Player p, int needed) {
        List<Integer> byPrice = new ArrayList<>(p.properties);
        byPrice.sort(Comparator.comparingInt(i -> Board.square(i).price()));
        for (int idx : byPrice) {
            if (p.cash >= needed) break;
            int proceeds = Board.square(idx).price() * rules.sellPercent() / 100;
            p.cash += proceeds;
            p.properties.remove(Integer.valueOf(idx));
            owner[idx] = -1;
            say(p.name() + " sells " + Board.square(idx).name() + " to the bank for " + proceeds);
        }
    }

    // ================================================================ chance

    private Card draw() {
        if (deck.isEmpty()) refillDeck();
        return deck.poll();
    }

    private void refillDeck() {
        List<Card> cards = new ArrayList<>(List.of(Card.values()));
        Collections.shuffle(cards, rng);
        deck.addAll(cards);
    }

    private void drawCard(Player p, int depth) {
        Card card = draw();
        say(p.name() + " draws: " + card.text());
        switch (card) {
            case BANK_ERROR -> p.cash += 150;
            case DOCTOR -> charge(p, 80, null, true);
            case TO_START -> {
                moveForwardTo(p, 0);
                resolve(p, 0, depth + 1);
            }
            case TO_PRISON -> {
                p.position = PRISON_SQUARE;
                p.prisonTurns = rules.prisonTurns();
            }
            case BIRTHDAY -> {
                for (Player other : players) if (other != p && other.alive) charge(other, 30, p, false);
            }
            case GENEROUS -> {
                for (Player other : players) {
                    if (other == p || !other.alive) continue;
                    if (!charge(p, 25, other, false)) break;
                }
            }
            case BACK_THREE -> {
                p.position = (p.position - 3 + Board.SIZE) % Board.SIZE;
                resolve(p, p.position, depth + 1);
            }
            case TO_AIRPORT -> moveForwardTo(p, Board.nextAirport(p.position));
            case REPAIRS -> charge(p, 20 * p.properties.size(), null, true);
            case RAFFLE -> p.cash += 100;
            case SPEEDING -> charge(p, 60, null, true);
            case TO_BANK -> {
                moveForwardTo(p, BANK_SQUARE);
                resolve(p, BANK_SQUARE, depth + 1);
            }
        }
    }

    // ================================================================ helpers

    private void say(String line) {
        if (logging) log.add(line);
    }

    /** Test hook. */
    void teleport(Player p, int position) { p.position = position; }

    /** Test hook: hands a property to a player without payment. */
    void grant(Player p, int squareIndex) {
        owner[squareIndex] = players.indexOf(p);
        p.properties.add(squareIndex);
    }

    /** Test hook. */
    void addToPot(int amount) { pot += amount; }
}
