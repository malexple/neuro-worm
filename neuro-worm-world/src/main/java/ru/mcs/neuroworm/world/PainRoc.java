package ru.mcs.neuroworm.world;

import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import ru.mcs.neuroworm.core.ConnectomeLoader;
import ru.mcs.neuroworm.core.Network;
import ru.mcs.neuroworm.core.Neuron;

/** Различимость боли: сигнал в покое против сигнала в первые 0,5 с после включения ASH. */
public final class PainRoc {
    static final double[] ALPHAS = {0.2, 0.05};
    static final String[] NAMES = {"motor a=0.2", "motor a=0.05", "command a=0.2", "command a=0.05"};
    static final int REST = 300, TOTAL = 350;

    static int[] ids(Network net, String regex) {
        Pattern p = Pattern.compile(regex);
        return net.neurons().stream().filter(n -> p.matcher(n.name).matches()).mapToInt(n -> n.index).toArray();
    }

    static double rate(Network net, int[] ids, long[] last) {
        long spikes = 0;
        for (int i : ids) spikes += net.get(i).spikeCount() - last[i];
        return spikes * 1000.0 / 10 / ids.length;
    }

    static double[][] run(double[] g, double[] off, long seed, double ashHz) {
        ChemotaxisScenario s = new ChemotaxisScenario(off, seed, Set.of(), g);
        Network net = s.net;
        int[] fwd = ids(net, "^(VB|DB)\\d\\d$"), back = ids(net, "^(VA|DA)\\d\\d$");
        int[] cb = ids(net, "^(AVA|AVD)[LR]$"), cf = ids(net, "^AVB[LR]$");
        long[] last = new long[net.size()];
        for (Neuron n : net.neurons()) last[n.index] = n.spikeCount();
        double[] ema = new double[4];
        double[][] sig = new double[4][TOTAL];
        for (int t = 0; t < TOTAL; t++) {
            if (t == REST) {
                net.get("ASHL").stimulusHz = ashHz;
                net.get("ASHR").stimulusHz = ashHz;
            }
            s.sim.run(10);
            double motor = rate(net, back, last) - rate(net, fwd, last);
            double cmd = rate(net, cb, last) - rate(net, cf, last);
            for (Neuron n : net.neurons()) last[n.index] = n.spikeCount();
            double[] raw = {motor, motor, cmd, cmd};
            for (int k = 0; k < 4; k++) {
                ema[k] += ALPHAS[k % 2] * (raw[k] - ema[k]);
                sig[k][t] = ema[k];
            }
        }
        return sig;
    }

    static double max(double[] a, int from, int to) {
        double m = Double.NEGATIVE_INFINITY;
        for (int i = from; i < to; i++) m = Math.max(m, a[i]);
        return m;
    }

    public static void main(String[] args) {
        Logger.getLogger(ConnectomeLoader.class.getName()).setLevel(Level.SEVERE);
        double[] off = Evolution.settle();
        double[] g = PainEvolution.reference();
        int seeds = 40;
        for (double ash : new double[] {200, 500}) {
            double[][][] runs = IntStream.range(0, seeds).parallel()
                    .mapToObj(i -> run(g, off, 500_000 + i, ash)).toArray(double[][][]::new);
            for (int k = 0; k < 4; k++) {
                double bestT = 0, bestHit = 0, bestFa = 1, bestScore = -2;
                for (double T = 0; T <= 20; T += 0.25) {
                    int fa = 0, hit = 0;
                    for (double[][] r : runs) {
                        for (int w = 0; w < 4; w++) if (max(r[k], 100 + 50 * w, 150 + 50 * w) > T) fa++;
                        if (max(r[k], REST, TOTAL) > T) hit++;
                    }
                    double faRate = fa / (seeds * 4.0), hitRate = hit / (double) seeds;
                    if (hitRate - faRate > bestScore) {
                        bestScore = hitRate - faRate;
                        bestT = T;
                        bestHit = hitRate;
                        bestFa = faRate;
                    }
                }
                System.out.printf("ASH %4.0f Hz | %-14s | T=%5.2f | hit %.2f | false alarm %.2f per 0.5 s%n",
                        ash, NAMES[k], bestT, bestHit, bestFa);
            }
        }
    }
}