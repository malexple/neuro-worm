package ru.mcs.neuroworm.world;

import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.IntStream;
import ru.mcs.neuroworm.core.ConnectomeLoader;

public final class PlasticityAblation {

    private static void run(String name, PlasticityEvolution.Rule rule, double[] off) {
        PlasticityEvolution.Learner learner = new PlasticityEvolution.Learner();
        double[] g = PlasticityEvolution.crippled();
        for (int k = 0; k < PlasticityEvolution.TRAIN; k++) {
            PlasticityEvolution.episode(g, off, 200_000 + k, rule, learner, true);
        }
        double dist = IntStream.range(0, 20).parallel()
                .mapToDouble(k -> PlasticityEvolution.episode(g, off, 300_000 + k, rule, learner, false))
                .average().orElse(0);
        double min = Double.MAX_VALUE, max = 0, sum = 0;
        int n = 0;
        for (double[] row : learner.scales) {
            for (double v : row) {
                min = Math.min(min, v);
                max = Math.max(max, v);
                sum += v;
                n++;
            }
        }
        System.out.printf("%-40s distance %.1f | scales min %.2f mean %.2f max %.2f (evolved reference 6.49)%n",
                name, dist, min, sum / n, max);
    }

    public static void main(String[] args) {
        Logger.getLogger(ConnectomeLoader.class.getName()).setLevel(Level.SEVERE);
        double[] off = Evolution.settle();
        var b = new PlasticityEvolution.Rule(5.27443841926396E-4, 705.4388742590974, 1.880629234658331,
                1.3078677406977237, -1.0, 0.6702995664621183);
        run("learned rule (gen 24)", b, off);
        run("kappa=0 (constant reward d0=-1)",
                new PlasticityEvolution.Rule(b.eta(), b.tauElig(), b.aMinus(), 0, b.d0(), b.blend()), off);
        run("d0=0 (reward only from odor)",
                new PlasticityEvolution.Rule(b.eta(), b.tauElig(), b.aMinus(), b.kappa(), 0, b.blend()), off);
        run("kappa=0, d0=+1 (opposite constant)",
                new PlasticityEvolution.Rule(b.eta(), b.tauElig(), b.aMinus(), 0, 1.0, b.blend()), off);
    }
}