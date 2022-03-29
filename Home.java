public abstract class Home {
    enum color {red, green, blue, white, yellow};
    private int woner = 0;
    private color Color = color.white;

    

    public Home(int woner, Home.color color) {
        this.woner = woner;
        Color = color;
    }

    public int getWoner() {
        return woner;
    }

    public void setWoner(int woner) {
        this.woner = woner;
    }

    public color getColor() {
        return Color;
    }

    public void setColor(color color) {
        Color = color;
    }
    
    
}
