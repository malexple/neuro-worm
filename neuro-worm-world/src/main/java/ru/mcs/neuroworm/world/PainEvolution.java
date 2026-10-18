package ru.mcs.neuroworm.world;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.IntStream;
import ru.mcs.neuroworm.core.ConnectomeLoader;

/** Эволюция 16 генов на сцене с зоной боли: время + поворот + время в зоне. */
public final class PainEvolution {
    static final int GENES = 16;
    static final double[] LO = with(EfficiencyEvolution.LO, 0);
    static final double[] HI = with(EfficiencyEvolution.HI, 1000);
    static final double PAIN_WEIGHT = 4;
    static final long VALIDATION_SEED = 400_000;

    static { LO[9] = 3; }

    record Result(boolean found, double time, double rotation, double zoneTime, double cost) { }

    private static double[] with(double[] a, double v) {
        double[] b = Arrays.copyOf(a, a.length + 1);
        b[a.length] = v;
        return b;
    }

    static double[] decode(double[] x) {
        double[] g = new double[GENES];
        for (int i = 0; i < GENES; i++) g[i] = LO[i] + (HI[i] - LO[i]) * Math.max(0, Math.min(1, x[i]));
        return g;
    }

    static double[] encode(double[] g) {
        double[] x = new double[GENES];
        for (int i = 0; i < GENES; i++) x[i] = (g[i] - LO[i]) / (HI[i] - LO[i]);
        return x;
    }

    static double[] reference() {
        double[] g = Arrays.copyOf(EfficiencyEvolution.EVOLVED, GENES);
        g[15] = 200;
        return g;
    }

    static Arena.Zone zoneFor(long seed) {
        SplittableRandom r = new SplittableRandom(seed * 31 + 7);
        return new Arena.Zone(110 + r.nextDouble() * 16 - 8, 100 + r.nextDouble() * 16 - 8, 12);
    }

    static Result run(double[] g, double[] off, long seed, Set<String> lesions) {
        ChemotaxisScenario s = new ChemotaxisScenario(off, seed, lesions, g, zoneFor(seed));
        Worm w = s.worm;
        int zoneSteps = 0;
        for (int i = 0; i < EfficiencyEvolution.RUN_STEPS && !w.feeding(); i++) {
            s.step();
            if (s.arena.pain(w.x, w.y) > 0) zoneSteps++;
        }
        boolean found = w.feeding();
        double t = found ? w.contactStep() / 100.0
                : EfficiencyEvolution.T_MAX + EfficiencyEvolution.MISS_PENALTY + 0.2 * s.distanceToFood();
        double zt = zoneSteps / 100.0;
        return new Result(found, found ? t : EfficiencyEvolution.T_MAX, w.totalRotation(), zt,
                t + EfficiencyEvolution.ROT_WEIGHT * w.totalRotation() + PAIN_WEIGHT * zt);
    }

    static void report(String name, double[] g, double[] off, Set<String> lesions) {
        int n = 20;
        Result[] r = IntStream.range(0, n).parallel()
                .mapToObj(i -> run(g, off, VALIDATION_SEED + i, lesions)).toArray(Result[]::new);
        System.out.printf("%-26s found %2d/%d | time %.1f s | rotation %.0f rad | in zone %.2f s%n", name,
                Arrays.stream(r).filter(Result::found).count(), n,
                Arrays.stream(r).mapToDouble(Result::time).average().orElse(0),
                Arrays.stream(r).mapToDouble(Result::rotation).average().orElse(0),
                Arrays.stream(r).mapToDouble(Result::zoneTime).average().orElse(0));
    }

    public static void main(String[] args) {
        Logger.getLogger(ConnectomeLoader.class.getName()).setLevel(Level.SEVERE);
        double[] off = Evolution.settle();
        Set<String> noAsh = Set.of("ASHL", "ASHR");
        report("reference, ASH intact", reference(), off, Set.of());
        report("reference, ASH lesioned", reference(), off, noAsh);

        int pop = 32, elite = 6, generations = 25, seeds = 6;
        SplittableRandom rng = new SplittableRandom(0);
        double[] refX = encode(reference());
        refX[9] = 1.0;
        double[][] p = new double[pop][];
        p[0] = refX;
        for (int i = 1; i < 12; i++) {
            double[] x = refX.clone();
            for (int k = 9; k < GENES; k++) x[k] = rng.nextDouble();
            x[9] = 0.1 + 0.4 * rng.nextDouble();
            p[i] = x;
        }
        for (int i = 12; i < pop; i++) p[i] = rng.doubles(GENES).toArray();

        double[] best = p[0];
        for (int gen = 0; gen < generations; gen++) {
            double[][] cur = p;
            long seed0 = gen * 1000L;
            double[] fit = new double[pop];
            IntStream.range(0, pop).parallel().forEach(i -> {
                double[] g = decode(cur[i]);
                double sum = 0;
                for (int k = 0; k < seeds; k++) sum += run(g, off, seed0 + k, Set.of()).cost();
                fit[i] = sum / seeds;
            });
            Integer[] order = IntStream.range(0, pop).boxed().toArray(Integer[]::new);
            Arrays.sort(order, Comparator.comparingDouble(i -> fit[i]));
            double[][] sorted = new double[pop][];
            for (int i = 0; i < pop; i++) sorted[i] = cur[order[i]];
            best = sorted[0];
            System.out.printf("gen %d best cost %.1f median %.1f genes %s%n", gen, fit[order[0]],
                    fit[order[pop / 2]], Arrays.toString(decode(best)));
            for (int i = elite; i < pop; i++) {
                double[] parent = sorted[rng.nextInt(elite)];
                double[] child = new double[GENES];
                for (int k = 0; k < GENES; k++) {
                    child[k] = Math.max(0, Math.min(1, parent[k] + 0.12 * rng.nextGaussian()));
                }
                sorted[i] = child;
            }
            p = sorted;
        }
        double[] g = decode(best);
        report("evolved, ASH intact", g, off, Set.of());
        report("evolved, ASH lesioned", g, off, noAsh);
        System.out.println("GENOME " + Arrays.toString(g));
    }
}