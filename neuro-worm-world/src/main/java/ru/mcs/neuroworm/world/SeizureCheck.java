package ru.mcs.neuroworm.world;

import java.util.Arrays;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.IntStream;
import ru.mcs.neuroworm.core.ConnectomeLoader;
import ru.mcs.neuroworm.core.Neuron;

/** Максимальная средняя частота спайков по сети за 1 с за 30 с сцены (покой около 3 Гц). */
public final class SeizureCheck {
    static final double LIMIT_HZ = 20;

    static double peakRate(double[] g, double[] off, long seed) {
        ChemotaxisScenario s = new ChemotaxisScenario(off, seed, Set.of(), g);
        var neurons = s.net().neurons();
        long[] last = new long[s.net().size()];
        for (Neuron n : neurons) last[n.index] = n.spikeCount();
        double peak = 0;
        for (int step = 1; step <= 3000; step++) {
            s.step();
            if (step % 100 == 0) {
                long total = 0;
                for (Neuron n : neurons) {
                    long c = n.spikeCount();
                    total += c - last[n.index];
                    last[n.index] = c;
                }
                peak = Math.max(peak, total / (double) neurons.size());
            }
        }
        return peak;
    }

    static void check(String name, double[] g, double[] off) {
        int n = 20;
        double[] peak = IntStream.range(0, n).parallel().mapToDouble(i -> peakRate(g, off, 200_000 + i)).toArray();
        Arrays.sort(peak);
        long bad = Arrays.stream(peak).filter(r -> r > LIMIT_HZ).count();
        System.out.printf("%-10s rate>%.0f Hz in %d/%d runs | median peak %.1f Hz | max %.1f Hz%n",
                name, LIMIT_HZ, bad, n, peak[n / 2], peak[n - 1]);
    }

    public static void main(String[] args) {
        Logger.getLogger(ConnectomeLoader.class.getName()).setLevel(Level.SEVERE);
        double[] off = Evolution.settle();
        check("reference", EfficiencyEvolution.extend(PlasticityEvolution.baseGenome()), off);
        check("evolved", EfficiencyEvolution.EVOLVED, off);
    }
}