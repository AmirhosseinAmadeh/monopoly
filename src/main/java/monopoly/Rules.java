package monopoly;

/** Tunable game constants. {@link #standard()} is what the web UI and the simulator use. */
public record Rules(int startCash, int salary, int rentPercent, int sellPercent, int airportFee,
                    int awardPrize, int taxPercent, int minTax, int bankInterestPercent, int bankInterestCap,
                    int prisonTurns, int maxRounds) {

    public static Rules standard() {
        return new Rules(600, 20, 80, 50, 50, 100, 10, 30, 10, 100, 1, 150);
    }

    public Rules withMaxRounds(int rounds) {
        return new Rules(startCash, salary, rentPercent, sellPercent, airportFee, awardPrize, taxPercent, minTax,
                bankInterestPercent, bankInterestCap, prisonTurns, rounds);
    }
}
