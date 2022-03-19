public class Main_game {
    public static void main(String[] args) {
        boolean x = false;
        Game g1 = Game.create_game();
        try {
            g1.StartGame();
        } catch (Exception e) {
            System.out.println("no game created");
        }
        while (x == false) {
            System.out.println("round " + g1.getRound());
            switch (key) {
                case value:
                    
                    break;
            
                default:
                    break;
            }
        }
    }
}
