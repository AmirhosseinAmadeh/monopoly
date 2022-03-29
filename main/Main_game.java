package main;

public class Main_game {
    public static void main(String[] args) {
        boolean x = false;
        int Place = 0;
        while (x == false) {
            switch (Place) {
                case 0:
                    Pre_Game g1 = Pre_Game.create_game();
                    try {

                        g1.StartGame();
                    } catch (Exception e) {
                        System.out.println("no game created");
                        break;
                    }
                    Place++;
                    break;
                case 1:

                    break;

                default:
                    break;
            }
        }
    }
}
