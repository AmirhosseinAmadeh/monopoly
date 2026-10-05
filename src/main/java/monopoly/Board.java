package monopoly;

import java.util.ArrayList;
import java.util.List;

import monopoly.Square.Kind;

/**
 * The 24-square board. The layout (which kind of square and which colour sits where) is the one
 * from the original project; names and prices are added here.
 */
public final class Board {
    public static final int SIZE = 24;

    private static final List<Square> SQUARES = build();

    private Board() {}

    public static List<Square> squares() { return SQUARES; }

    public static Square square(int index) { return SQUARES.get(index); }

    /** Indexes of the buyable squares of a colour group. */
    public static List<Integer> group(Color color) {
        List<Integer> out = new ArrayList<>();
        for (Square s : SQUARES) if (s.buyable() && s.color() == color) out.add(s.index());
        return out;
    }

    /** Next airport strictly after {@code from}, wrapping around the board. */
    public static int nextAirport(int from) {
        for (int i = 1; i <= SIZE; i++) {
            int idx = (from + i) % SIZE;
            if (SQUARES.get(idx).kind() == Kind.AIRPORT) return idx;
        }
        throw new IllegalStateException("board has no airport");
    }

    private static int tier(Color c) {
        return switch (c) {
            case GREEN -> 100;
            case RED -> 140;
            case YELLOW -> 180;
            case BLUE -> 220;
            case NONE -> 0;
        };
    }

    private static List<Square> build() {
        Object[][] layout = {
            {Kind.PARKING, "Parking & Start", Color.NONE},
            {Kind.LAND, "Garden Street", Color.GREEN},
            {Kind.AIRPORT, "North Airport", Color.NONE},
            {Kind.CINEMA, "Red Screen Cinema", Color.RED},
            {Kind.ROAD, "Highway One", Color.NONE},
            {Kind.AWARD, "Lottery Hall", Color.NONE},
            {Kind.LAND, "Sunny Square", Color.YELLOW},
            {Kind.CINEMA, "Blue Theatre", Color.BLUE},
            {Kind.LAND, "Rose Avenue", Color.RED},
            {Kind.ROAD, "Ring Road", Color.NONE},
            {Kind.AIRPORT, "East Airport", Color.NONE},
            {Kind.LAND, "Park Lane", Color.GREEN},
            {Kind.PRISON, "Prison", Color.NONE},
            {Kind.LAND, "Harbor Street", Color.BLUE},
            {Kind.CINEMA, "Green Cinema", Color.GREEN},
            {Kind.ROAD, "Mountain Road", Color.NONE},
            {Kind.TAX, "Tax Office", Color.NONE},
            {Kind.LAND, "Ruby Street", Color.RED},
            {Kind.LAND, "Gold Street", Color.YELLOW},
            {Kind.AIRPORT, "South Airport", Color.NONE},
            {Kind.BANK, "Central Bank", Color.NONE},
            {Kind.CINEMA, "Yellow Cinema", Color.YELLOW},
            {Kind.LAND, "Ocean Drive", Color.BLUE},
            {Kind.CHANCE, "Chance", Color.NONE},
        };
        List<Square> out = new ArrayList<>();
        for (int i = 0; i < layout.length; i++) {
            Kind kind = (Kind) layout[i][0];
            Color color = (Color) layout[i][2];
            int price = kind == Kind.LAND ? tier(color) : kind == Kind.CINEMA ? tier(color) + 40 : 0;
            out.add(new Square(i, kind, (String) layout[i][1], color, price));
        }
        return List.copyOf(out);
    }
}
