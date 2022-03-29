package main;
public class Player {
    private String name;
    private int Turn;
    private double CashAssets = 1500;
    private int[] Non_cashAssets = new int[12];
    private int place;
    boolean RedMonopoly = false;
    boolean BlueMonopoly = false;
    boolean GreenMonopoly = false;
    boolean YeloowMonopoly = false;
    public Player(String name, int turn) {
        this.name = name;
        Turn = turn;
    }
    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }
    public int getTurn() {
        return Turn;
    }
    public void setTurn(int turn) {
        Turn = turn;
    }
    public double getCashAssets() {
        return CashAssets;
    }
    public void setCashAssets(double cashAssets) {
        CashAssets = cashAssets;
    }
    public int[] getNon_cashAssets() {
        return Non_cashAssets;
    }
    public void setNon_cashAssets(int[] non_cashAssets) {
        Non_cashAssets = non_cashAssets;
    }
    public int getPlace() {
        return place;
    }
    public void setPlace(int place) {
        this.place = place;
    }
    
    
    
}
