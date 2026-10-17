package ru.mcs.neuroworm.world;

import java.util.logging.Level;
import java.util.logging.Logger;
import ru.mcs.neuroworm.core.ConnectomeLoader;

public final class FeedingCheck {
    public static void main(String[] args) {
        Logger.getLogger(ConnectomeLoader.class.getName()).setLevel(Level.SEVERE);
        System.out.println("data='" + Evolution.DATA + "'");
        double[] off = ChemotaxisScenario.settle();
        for (boolean blind : new boolean[] {false, true}) {
            int found = 0;
            double sumT = 0, sumD = 0;
            for (int k = 0; k < 20; k++) {
                ChemotaxisScenario s = new ChemotaxisScenario(off, 1_000_000 + k);
                if (blind) {
                    s.worm.cfg.odorGainHz = 0;
                    s.worm.cfg.awaDerivGain = 0;
                    s.worm.cfg.awcOffGain = 0;
                }
                for (int i = 0; i < 2000; i++) s.world.step();
                if (s.worm.feeding()) {
                    found++;
                    sumT += s.worm.contactStep() / 100.0;
                }
                sumD += s.distanceToFood();
            }
            System.out.printf("%-6s found %2d/20 | mean time to food %.1f s | mean final distance %.1f%n",
                    blind ? "blind" : "odor", found, found == 0 ? 0 : sumT / found, sumD / 20);
        }
    }
}