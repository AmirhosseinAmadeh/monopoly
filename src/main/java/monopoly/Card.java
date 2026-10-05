package monopoly;

/** Chance cards. The effects live in {@link Game}; this enum is the deck's content and wording. */
public enum Card {
    BANK_ERROR("Bank error in your favour: collect 150"),
    DOCTOR("Doctor's fees: pay 80"),
    TO_START("Advance to Parking & Start and collect your salary"),
    TO_PRISON("Go to prison"),
    BIRTHDAY("It is your birthday: collect 30 from every other player"),
    GENEROUS("You pay 25 to every other player"),
    BACK_THREE("Move back three squares"),
    TO_AIRPORT("Free flight: advance to the nearest airport"),
    REPAIRS("Property repairs: pay 20 for each property you own"),
    RAFFLE("You won the cinema raffle: collect 100"),
    SPEEDING("Speeding fine: pay 60 into the parking pot"),
    TO_BANK("Advance to Central Bank");

    private final String text;

    Card(String text) { this.text = text; }

    public String text() { return text; }
}
