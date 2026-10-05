package monopoly;

/**
 * How a player decides whether to buy a property. {@code reserve} is the cash the bot insists on
 * keeping after the purchase; a human decides interactively.
 */
public enum Strategy {
    HUMAN(-1),
    CAUTIOUS(400),
    BALANCED(200),
    AGGRESSIVE(0);

    private final int reserve;

    Strategy(int reserve) { this.reserve = reserve; }

    public boolean isBot() { return this != HUMAN; }

    public boolean wantsToBuy(int cash, int price) {
        return isBot() && cash - price >= reserve;
    }
}
