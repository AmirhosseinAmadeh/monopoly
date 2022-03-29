package Homes;
public abstract class Home {
    enum Color {red, green, blue, white, yellow};
    private int woner = 0;
    private Color color = Color.white;

    

    public Home(int woner, Home.Color color) {
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
    
    
}
