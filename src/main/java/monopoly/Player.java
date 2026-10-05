package monopoly;

import java.util.ArrayList;
import java.util.List;

/** Mutable per-player state; only {@link Game} changes it. */
public final class Player {
    private final String name;
    private final Strategy strategy;
    int cash;
    int position;
    int prisonTurns;
    boolean alive = true;
    final List<Integer> properties = new ArrayList<>();

    Player(String name, Strategy strategy, int cash) {
        this.name = name;
        this.strategy = strategy;
        this.cash = cash;
    }

    public String name() { return name; }

    public Strategy strategy() { return strategy; }

    public boolean isHuman() { return !strategy.isBot(); }

    public int cash() { return cash; }

    public int position() { return position; }

    public int prisonTurns() { return prisonTurns; }

    public boolean alive() { return alive; }

    public List<Integer> properties() { return List.copyOf(properties); }
}
