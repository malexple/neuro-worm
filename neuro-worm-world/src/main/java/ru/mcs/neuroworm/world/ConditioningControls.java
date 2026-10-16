package ru.mcs.neuroworm.world;

import java.util.logging.Level;
import java.util.logging.Logger;
import ru.mcs.neuroworm.core.ConnectomeLoader;

public final class ConditioningControls {
    public static void main(String[] args) {
        Logger.getLogger(ConnectomeLoader.class.getName()).setLevel(Level.SEVERE);
        double[] off = Evolution.settle();
        var cd = new ConditioningEvolution.Cond(201.41447635835283, 1785.9056477375857, 2813.328520497241,
                22.98303364691824, 7.278228212404638, 0.014821520389183088, 1281.1734028023334,
                1.0575997135100692, 0.6426611029113689);
        var deaf = new ConditioningEvolution.Cond(0, 0, 0, 0, cd.sAse(), cd.eta(), cd.tauElig(),
                cd.aMinus(), cd.rewardGain());
        var none = new ConditioningEvolution.Learner();
        System.out.printf("blind (chance level)        %.1f%n", ConditioningEvolution.test(deaf, off, none, 300_000, 20));
        System.out.printf("naive                       %.1f%n", ConditioningEvolution.test(cd, off, none, 300_000, 20));
        long seed = 200_000;
        for (ConditioningEvolution.Mode m : new ConditioningEvolution.Mode[] {
                ConditioningEvolution.Mode.PAIRED, ConditioningEvolution.Mode.UNPAIRED,
                ConditioningEvolution.Mode.CS_ONLY, ConditioningEvolution.Mode.US_ONLY}) {
            var learner = ConditioningEvolution.train(cd, off, seed, m);
            System.out.printf("%-27s %.1f%n", m, ConditioningEvolution.test(cd, off, learner, 300_000, 20));
            seed += 10_000;
        }
    }
}