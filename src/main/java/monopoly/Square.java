package monopoly;

/** One square of the board. Only {@link Kind#LAND} and {@link Kind#CINEMA} squares can be owned. */
public record Square(int index, Kind kind, String name, Color color, int price) {

    public enum Kind { PARKING, LAND, CINEMA, ROAD, AIRPORT, AWARD, PRISON, TAX, BANK, CHANCE }

    public boolean buyable() { return kind == Kind.LAND || kind == Kind.CINEMA; }
}
