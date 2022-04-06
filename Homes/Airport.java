package Homes;
import java.util.Scanner;

import main.Player;

public class Airport extends Home {
    Scanner scanner = new Scanner(System.in);

    public Airport(int woner, Color color) {
        super(woner, color);
    }

    @Override
    public void Action(Player player) {
        if(player.getCashAssets() < 50){
            System.out.println("you dont have enough mony");
            System.out.println("Would you like to sell your property? (Y & N)");
            if (scanner.nextLine().charAt(0) == 'Y') {
                player.sell_Non_cashAssets();
                this.Action(player);
            }else{
                System.out.println("Your turn is over");
                return;
            }
        }
        
    }

    

}
