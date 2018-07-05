import ai.hual.labrador.local.Simulator;

import java.io.IOException;
import java.sql.SQLException;

public class Main {

    public static void main(String[] args) throws IOException, SQLException {
        Simulator.hsmRemoteDevelop(args, Main.class.getClassLoader()).start();
    }

}
