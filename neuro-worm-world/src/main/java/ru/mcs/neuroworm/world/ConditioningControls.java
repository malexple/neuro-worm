package ru.mcs.neuroworm.world;

import java.util.logging.Level;
import java.util.logging.Logger;
import ru.mcs.neuroworm.core.ConnectomeLoader;

public final class ConditioningControls {
    public static void main(String[] args) {
        Logger.getLogger(ConnectomeLoader.class.getName()).setLevel(Level.SEVERE);
        double[] off = Evolution.settle();
        var cd =  new ConditioningEvolution.Cond(72.67748465146242, 3000.0, 2339.3681199859384, 3.515398863875265, 10.0, 0.00179437094059225, 271.71582918241495, 1.568973640348443, -0.6507395051063134);
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