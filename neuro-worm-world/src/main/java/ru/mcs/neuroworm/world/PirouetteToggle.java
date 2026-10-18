package ru.mcs.neuroworm.world;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.IntStream;
import ru.mcs.neuroworm.core.ConnectomeLoader;

/** Помогают ли пируэты: тот же геном с порогом пируэта и с порогом 0, парами по seed. */
public final class PirouetteToggle {
    static final int N = 50;
    static final long FIRST = 600_000;

    static EfficiencyEvolution.Result[] runAll(double[] g, double[] off) {
        return IntStream.range(0, N).parallel()
                .mapToObj(i -> EfficiencyEvolution.run(g, off, FIRST + i))
                .toArray(EfficiencyEvolution.Result[]::new);
    }

    static double avg(EfficiencyEvolution.Result[] r, java.util.function.ToDoubleFunction<EfficiencyEvolution.Result> f) {
        return Arrays.stream(r).mapToDouble(f).average().orElse(0);
    }

    static void compare(String name, double[] g, double[] off) {
        if (g[9] <= 0) {
            System.out.printf("%-6s pirouettes already off (pirThr 0)%n", name);
            return;
        }
        double[] without = g.clone();
        without[9] = 0;
        EfficiencyEvolution.Result[] a = runAll(g, off);
        EfficiencyEvolution.Result[] b = runAll(without, off);
        double[] d = new double[N];
        int wins = 0;
        for (int i = 0; i < N; i++) {
            d[i] = b[i].cost() - a[i].cost();
            if (d[i] > 0) wins++;
        }
        double mean = Arrays.stream(d).average().orElse(0);
        double se = Math.sqrt(Arrays.stream(d).map(x -> (x - mean) * (x - mean)).sum() / (N - 1) / N);
        System.out.printf("%-6s pirThr %.2f | with: time %.1f rot %.0f cost %.1f | without: time %.1f rot %.0f cost %.1f"
                        + " | delta %+.2f +- %.2f (pirouettes better in %d/%d seeds)%n",
                name, g[9], avg(a, EfficiencyEvolution.Result::time), avg(a, EfficiencyEvolution.Result::rotation),
                avg(a, EfficiencyEvolution.Result::cost), avg(b, EfficiencyEvolution.Result::time),
                avg(b, EfficiencyEvolution.Result::rotation), avg(b, EfficiencyEvolution.Result::cost),
                mean, se, wins, N);
    }

    public static void main(String[] args) throws IOException {
        Logger.getLogger(ConnectomeLoader.class.getName()).setLevel(Level.SEVERE);
        double[] off = Evolution.settle();
        for (int k = 1; k <= IndependentRuns.RUNS; k++) {
            Path f = IndependentRuns.DIR.resolve("run_" + k + ".txt");
            if (Files.exists(f)) compare("run" + k, IndependentRuns.load(f), off);
        }
    }
}