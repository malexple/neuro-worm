package ru.mcs.neuroworm.world;

import java.util.logging.Level;
import java.util.logging.Logger;
import ru.mcs.neuroworm.core.ConnectomeLoader;

public final class PlasticityControl {
    public static void main(String[] args) {
        Logger.getLogger(ConnectomeLoader.class.getName()).setLevel(Level.SEVERE);
        double[] off = Evolution.settle();
        var rule = new PlasticityEvolution.Rule(1.9816294929804E-4, 300.37004300308473, 2.0,
                12.467462623232478, 0.0, 0.2418255154578283);
        for (int delay : new int[] {0, 300, 1000}) {
            PlasticityEvolution.rewardDelay = delay;
            System.out.printf("reward delay %4d steps (%.0f s): distance %.1f%n", delay, delay / 100.0,
                    PlasticityEvolution.trainAndEval(rule, off));
        }
        PlasticityEvolution.rewardDelay = 0;
    }
}