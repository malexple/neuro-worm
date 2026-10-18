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

/** Рефлекс по боли на геномах физического масштаба: пороги командного триггера без эволюции. */
public final class PainReflexCheck {
    static final int N = 20;
    static final long FIRST = 700_000;
    static final double PAIN_GAIN = 500;

    static double[] withReflex(double[] g15, double threshold, int reverseSteps, double omega) {
        double[] g = Arrays.copyOf(g15, PainEvolution.GENES);
        g[9] = threshold;
        g[10] = reverseSteps;
        g[11] = 0.02;
        g[12] = omega;
        g[14] = 200;
        g[15] = PAIN_GAIN;
        return g;
    }

    static void report(String name, double[] g, double[] off, Set<String> lesions) {
        PainEvolution.Result[] r = IntStream.range(0, N).parallel()
                .mapToObj(i -> PainEvolution.run(g, off, FIRST + i, lesions)).toArray(PainEvolution.Result[]::new);
        System.out.printf("%-22s found %2d/%d | time %.1f s | rotation %.0f rad | in zone %.2f s%n", name,
                Arrays.stream(r).filter(PainEvolution.Result::found).count(), N,
                Arrays.stream(r).mapToDouble(PainEvolution.Result::time).average().orElse(0),
                Arrays.stream(r).mapToDouble(PainEvolution.Result::rotation).average().orElse(0),
                Arrays.stream(r).mapToDouble(PainEvolution.Result::zoneTime).average().orElse(0));
    }

    public static void main(String[] args) throws IOException {
        Logger.getLogger(ConnectomeLoader.class.getName()).setLevel(Level.SEVERE);
        double[] off = Evolution.settle();
        for (int k = 1; k <= 3; k++) {
            Path f = Path.of("genomes_scaled_full", "run_" + k + ".txt");
            if (!Files.exists(f)) continue;
            double[] g15 = IndependentRuns.load(f);
            report("run" + k + " ASH lesioned", withReflex(g15, 0, 100, 2.0), off, Set.of("ASHL", "ASHR"));
            report("run" + k + " no reflex", withReflex(g15, 0, 100, 2.0), off, Set.of());
            for (int rev : new int[] {100, 200, 300}) {
                for (double om : new double[] {2.0, 3.0}) {
                    report("run" + k + " T=15 rev=" + rev + " om=" + om, withReflex(g15, 15, rev, om), off, Set.of());
                }
            }
        }
    }

    static double meanPirouettes(double[] g, double[] off, Set<String> lesions) {
        return IntStream.range(0, N).parallel().mapToDouble(i -> {
            ChemotaxisScenario s = new ChemotaxisScenario(off, FIRST + i, lesions, g, PainEvolution.zoneFor(FIRST + i));
            for (int k = 0; k < EfficiencyEvolution.RUN_STEPS && !s.worm.feeding(); k++) s.step();
            return s.worm.pirouetteCount();
        }).average().orElse(0);
    }

}