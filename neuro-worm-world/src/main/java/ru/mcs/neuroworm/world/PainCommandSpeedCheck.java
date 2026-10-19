package ru.mcs.neuroworm.world;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.IntStream;
import ru.mcs.neuroworm.core.ConnectomeLoader;

/** Скорость по командным интернейронам: реверс при боли, без эволюции. */
public final class PainCommandSpeedCheck {
    static final int N = 20;
    static final long FIRST = 700_000;

    static double[] run(double[] g, double[] off, long seed, boolean on, double k, Set<String> lesions) {
        ChemotaxisScenario s = new ChemotaxisScenario(off, seed, lesions, g, PainEvolution.zoneFor(seed));
        Worm w = s.worm;
        w.cfg.commandSpeed = on;
        w.cfg.cmdRest = 2.0;
        w.cfg.cmdScale = k;
        int zone = 0;
        for (int i = 0; i < EfficiencyEvolution.RUN_STEPS && !w.feeding(); i++) {
            s.step();
            if (s.arena.pain(w.x, w.y) > 0) zone++;
        }
        boolean found = w.feeding();
        return new double[] {found ? 1 : 0, found ? w.contactStep() / 100.0 : 100, zone / 100.0, w.totalRotation()};
    }

    static void report(String name, double[] g, double[] off, boolean on, double k, Set<String> lesions) {
        double[][] r = IntStream.range(0, N).parallel()
                .mapToObj(i -> run(g, off, FIRST + i, on, k, lesions)).toArray(double[][]::new);
        System.out.printf("%-30s found %2.0f/%d | time %.1f s | in zone %.2f s | rotation %.0f rad%n", name,
                Arrays.stream(r).mapToDouble(a -> a[0]).sum(), N,
                Arrays.stream(r).mapToDouble(a -> a[1]).average().orElse(0),
                Arrays.stream(r).mapToDouble(a -> a[2]).average().orElse(0),
                Arrays.stream(r).mapToDouble(a -> a[3]).average().orElse(0));
    }

    public static void main(String[] args) throws IOException {
        Logger.getLogger(ConnectomeLoader.class.getName()).setLevel(Level.SEVERE);
        double[] off = Evolution.settle();
        for (int n = 1; n <= 3; n++) {
            Path f = Path.of("genomes_scaled_full", "run_" + n + ".txt");
            if (!Files.exists(f)) continue;
            double[] g = Arrays.copyOf(IndependentRuns.load(f), PainEvolution.GENES);
            g[9] = 0;
            g[15] = 500;
            report("run" + n + " ASH lesioned", g, off, false, 8, Set.of("ASHL", "ASHR"));
            report("run" + n + " speed from pools", g, off, false, 8, Set.of());
            for (double k : new double[] {4, 6, 8, 12}) {
                report("run" + n + " command speed k=" + k, g, off, true, k, Set.of());
            }
        }
    }
}