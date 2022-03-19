import java.util.Objects;
import java.util.Scanner;

public class Game{
    private static Game game = new Game();
    private Player[] players = new Player[4];
    Scanner scanner = new Scanner(System.in);

    private Game() {
    }
    public static Game create_game(){
        return game;
    }
    public void StartGame() {
        if(Objects.isNull(this)){
            System.out.println("no game created");
            return;
        }
        for (int i = 0; i < players.length; i++) {
            players[i] = new Player(scanner.nextLine(), i+1);
        }
    }
    
}