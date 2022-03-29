public class Player {
    private String name;
    private int Turn;
    private double CashAssets = 1500;
    private int[] Non_cashAssets = new int[12];
    boolean RedMonopoly = false;
    boolean BlueMonopoly = false;
    boolean GreenMonopoly = false;
    boolean YeloowMonopoly = false;
    public Player(String name, int turn) {
        this.name = name;
        Turn = turn;
    }
    
    
}
