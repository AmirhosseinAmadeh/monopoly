import Homes.*;

class GameBoard {
    private Home[] homes = new Home[24];
    {
        homes[0]  = new Parking(0, Color.white);
        homes[1]  = new EmptyLand(0, Color.green);
        homes[2]  = new Airport(0, Color.white);
        homes[3]  = new cinema(0, Color.red);
        homes[4]  = new Road(0, Color.white);
        homes[5]  = new AwardArea(0, Color.white);
        homes[6]  = new EmptyLand(0, Color.yellow);
        homes[7]  = new cinema(0, Color.blue);
        homes[8]  = new EmptyLand(0, Color.red);
        homes[9]  = new Road(0, Color.white);
        homes[10] = new Airport(0, Color.white);
        homes[11] = new EmptyLand(0, Color.green);
        homes[12] = new Prison(0, Color.white);
        homes[13] = new EmptyLand(0, Color.blue);
        homes[14] = new cinema(0, Color.green);
        homes[15] = new Road(0, Color.white);
        homes[16] = new TaxArea(0, Color.white);
        homes[17] = new EmptyLand(0, Color.red);
        homes[18] = new EmptyLand(0, Color.yellow);
        homes[19] = new Airport(0, Color.white);
        homes[20] = new Bank(0, Color.white);
        homes[21] = new cinema(0, Color.yellow);
        homes[22] = new EmptyLand(0, Color.blue);
        homes[23] = new ChanceArea(0, Color.white);
    }
}
