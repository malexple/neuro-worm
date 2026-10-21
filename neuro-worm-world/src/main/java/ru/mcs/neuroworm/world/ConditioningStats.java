package ru.mcs.neuroworm.world;

import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;
import ru.mcs.neuroworm.core.ConnectomeLoader;
import ru.mcs.neuroworm.core.PlasticityManager;

public final class ConditioningStats {
    static double mean(double[] v) { return Arrays.stream(v).average().orElse(0); }

    static double se(double[] v) {
        double m = mean(v);
        double s = Arrays.stream(v).map(x -> (x - m) * (x - m)).sum() / (v.length - 1);
        return Math.sqrt(s / v.length);
    }

    public static void main(String[] args) {
        Logger.getLogger(ConnectomeLoader.class.getName()).setLevel(Level.SEVERE);
        System.out.println("data='" + Evolution.DATA + "'");
        double[] off = Evolution.settle();
        var cd = new ConditioningEvolution.Cond(72.67748465146242, 3000.0, 2339.3681199859384,
                3.515398863875265, 10.0, 0.00179437094059225, 271.71582918241495,
                1.568973640348443, -0.6507395051063134);
        var deaf = new ConditioningEvolution.Cond(0, 0, 0, 0, cd.sAse(), cd.eta(), cd.tauElig(),
                cd.aMinus(), cd.rewardGain());
        var none = new ConditioningEvolution.Learner();
        double[] blind = ConditioningEvolution.testValues(deaf, off, none, 400_000, 60);
        double[] naive = ConditioningEvolution.testValues(cd, off, none, 400_000, 60);
        System.out.printf("blind   %.1f +- %.1f%n", mean(blind), se(blind));
        System.out.printf("naive   %.1f +- %.1f%n", mean(naive), se(naive));

        var modes = new ConditioningEvolution.Mode[] {ConditioningEvolution.Mode.PAIRED,
                ConditioningEvolution.Mode.UNPAIRED, ConditioningEvolution.Mode.US_ONLY};
        ConditioningEvolution.Learner firstPaired = null;
        for (int m = 0; m < modes.length; m++) {
            double[] all = new double[60];
            StringBuilder per = new StringBuilder();
            for (int r = 0; r < 5; r++) {
                var learner = ConditioningEvolution.train(cd, off, 500_000 + 10_000L * r + 100_000L * m, modes[m]);
                if (m == 0 && r == 0) firstPaired = learner;
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
        System.out.printf("PAIRED, reward 0 %.1f +- %.1f (expected ~ naive)%n", mean(z), se(z));

        PlasticityManager pm = PlasticityManager.forGroup(Evolution.newNetwork(), Evolution.GROUP_SALT);
        String[] pre = pm.preNames(), post = pm.postNames();
        System.out.println("changed ASE synapses after paired training (initial scale " + cd.sAse() + "):");
        for (int q = 0; q < post.length; q++) {
            for (int p = 0; p < pre.length; p++) {
                double s = firstPaired.scales[q][p];
                if (Math.abs(s - cd.sAse()) > 0.05) System.out.printf("  %s -> %s  %.2f%n", pre[p], post[q], s);
            }
        }
    }
}