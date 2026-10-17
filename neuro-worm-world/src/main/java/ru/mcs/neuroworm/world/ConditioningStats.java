package ru.mcs.neuroworm.world;

import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;
import ru.mcs.neuroworm.core.ConnectomeLoader;

public final class ConditioningStats {
    static double mean(double[] v) { return Arrays.stream(v).average().orElse(0); }

    static double se(double[] v) {
        double m = mean(v);
        double s = Arrays.stream(v).map(x -> (x - m) * (x - m)).sum() / (v.length - 1);
        return Math.sqrt(s / v.length);
    }

    public static void main(String[] args) {
        Logger.getLogger(ConnectomeLoader.class.getName()).setLevel(Level.SEVERE);
        double[] off = Evolution.settle();
        var cd = new ConditioningEvolution.Cond(394.51490408151466, 2547.5137122093274, 1442.1398538327514,
                16.497341126809253, 6.388154907645186, 1.0E-4, 1337.7681215778277,
                0.7672904329251614, -0.11170563157998936);
        var deaf = new ConditioningEvolution.Cond(0, 0, 0, 0, cd.sAse(), cd.eta(), cd.tauElig(),
                cd.aMinus(), cd.rewardGain());
        var none = new ConditioningEvolution.Learner();
        double[] blind = ConditioningEvolution.testValues(deaf, off, none, 400_000, 60);
        double[] naive = ConditioningEvolution.testValues(cd, off, none, 400_000, 60);
        System.out.printf("blind   %.1f +- %.1f%n", mean(blind), se(blind));
        System.out.printf("naive   %.1f +- %.1f%n", mean(naive), se(naive));

        var modes = new ConditioningEvolution.Mode[] {ConditioningEvolution.Mode.PAIRED,
                ConditioningEvolution.Mode.UNPAIRED, ConditioningEvolution.Mode.US_ONLY};
        for (int m = 0; m < modes.length; m++) {
            double[] all = new double[60];
            StringBuilder per = new StringBuilder();
            for (int r = 0; r < 5; r++) {
                var learner = ConditioningEvolution.train(cd, off, 500_000 + 10_000L * r + 100_000L * m, modes[m]);
                double[] v = ConditioningEvolution.testValues(cd, off, learner, 400_000 + 12L * r, 12);
                System.arraycopy(v, 0, all, 12 * r, 12);
                per.append(String.format("%.1f ", mean(v)));
            }
            System.out.printf("%-8s %.1f +- %.1f | per learner: %s%n", modes[m], mean(all), se(all), per);
        }
        var noReward = new ConditioningEvolution.Cond(cd.aseConc(), cd.aseOn(), cd.aseOff(), cd.aseTonic(),
                cd.sAse(), cd.eta(), cd.tauElig(), cd.aMinus(), 0.0);
        double[] z = new double[60];
        for (int r = 0; r < 5; r++) {
            var learner = ConditioningEvolution.train(noReward, off, 900_000 + 10_000L * r,
                    ConditioningEvolution.Mode.PAIRED);
            System.arraycopy(ConditioningEvolution.testValues(noReward, off, learner, 400_000 + 12L * r, 12),
                    0, z, 12 * r, 12);
        }
        System.out.printf("PAIRED, reward 0 %.1f +- %.1f (expected ~ naive 82)%n", mean(z), se(z));
    }
}