public class Game{
    private static Game game = new Game();

    private Game() {
    }
    public static Game create_game(){
        return game;
    }
    
}