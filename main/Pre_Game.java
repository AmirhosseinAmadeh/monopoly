package main;
import java.util.Objects;
import java.util.Scanner;

public class Pre_Game{
    private static Pre_Game game = new Pre_Game();
    private Player[] players = new Player[4];
    Scanner scanner = new Scanner(System.in);
    private int round = 0;

    private Pre_Game() {
    }
    public static Pre_Game create_game(){
        return game;
    }
    public void StartGame() throws RuntimeException {
        if(Objects.isNull(this)){
            throw new RuntimeException();
        }
        for (int i = 0; i < players.length; i++) {
            players[i] = new Player(scanner.nextLine(), i+1);
        }
        
    }
    public int getRound() {
        round++;
        return round;
    }
    
}