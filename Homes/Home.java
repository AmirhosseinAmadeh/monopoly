package Homes;
import main.Player;

public abstract class Home {
    private int woner = 0;
    private Color color = Color.white;
    private double value;
    public Home(int woner, Color color) {
        this.woner = woner;
        this.color = color;
    }

    public int getWoner() {
        return woner;
    }

    public void setWoner(int woner) {
        this.woner = woner;
    }

    public Color getColor() {
        return color;
    }

    public void setColor(Color color) {
        this.color = color;
    }
    public abstract void Action(Player player);

    @Override
    public String toString() {
        return "Type: " + this.getClass().getSimpleName() + ", color: " + color + "[value: " + value/2 + "]";
    }
     
    
}
