package ru.mcs.neuroworm.world;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.IntStream;
import ru.mcs.neuroworm.core.ConnectomeLoader;

/** Эволюция 15 генов хемотаксиса с ценой пути: время до еды + штраф за суммарный поворот. */
public final class EfficiencyEvolution {
    static final int GENES = 15;
    static final double[] LO = {0, 0, 0, 0, 0.002, -0.1, 0.0, 0.1, 0.1, 0, 20, 0.02, 0.5, 0, 50};
    static final double[] HI = {400, 3000, 3000, 40, 0.05, 0.1, 0.3, 10, 10, 15, 150, 0.2, 3.0, 10, 400};
    static final int RUN_STEPS = 3000;
    static final double T_MAX = 30, MISS_PENALTY = 40, ROT_WEIGHT = 0.02;

    record Result(boolean found, double time, double rotation, double cost) { }

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

    /** Базовый геном (9 генов) расширяется выключенными пируэтами. */
    static double[] extend(double[] base) {
        double[] g = Arrays.copyOf(base, GENES);
        g[9] = 0;
        g[10] = 50;
        g[11] = 0.1;
        g[12] = 2.0;
        g[13] = 0;
        g[14] = 100;
        return g;
    }

    static Result run(double[] g, double[] off, long seed) {
        ChemotaxisScenario s = new ChemotaxisScenario(off, seed, Set.of(), g);
        Worm w = s.worm;
        for (int i = 0; i < RUN_STEPS && !w.feeding(); i++) s.step();
        boolean found = w.feeding();
        double t = found ? w.contactStep() / 100.0 : T_MAX + MISS_PENALTY + 0.2 * s.distanceToFood();
        return new Result(found, found ? t : T_MAX, w.totalRotation(), t + ROT_WEIGHT * w.totalRotation());
    }

    static Result mean(double[] g, double[] off, long first, int n) {
        Result[] r = IntStream.range(0, n).parallel().mapToObj(i -> run(g, off, first + i)).toArray(Result[]::new);
        return new Result(Arrays.stream(r).filter(Result::found).count() * 2 >= n,
                Arrays.stream(r).mapToDouble(Result::time).average().orElse(0),
                Arrays.stream(r).mapToDouble(Result::rotation).average().orElse(0),
                Arrays.stream(r).mapToDouble(Result::cost).average().orElse(0));
    }

    static long foundCount(double[] g, double[] off, long first, int n) {
        return IntStream.range(0, n).parallel().filter(i -> run(g, off, first + i).found()).count();
    }

    static void report(String name, double[] g, double[] off) {
        Result r = mean(g, off, 100_000, 20);
        System.out.printf("%-10s found %2d/20 | time %.1f s | rotation %.0f rad | cost %.1f%n", name,
                foundCount(g, off, 100_000, 20), r.time(), r.rotation(), r.cost());
    }

    public static void main(String[] args) {
        Logger.getLogger(ConnectomeLoader.class.getName()).setLevel(Level.SEVERE);
        System.out.println("data='" + Evolution.DATA + "'");
        double[] off = Evolution.settle();
        double[] reference = extend(PlasticityEvolution.baseGenome());
        report("reference", reference, off);

        int pop = 32, elite = 6, generations = 25, seeds = 6;
        SplittableRandom rng = new SplittableRandom(0);
        double[][] p = new double[pop][];
        p[0] = encode(reference);
        for (int i = 1; i < pop; i++) p[i] = rng.doubles(GENES).toArray();

        double[] best = p[0];
        for (int gen = 0; gen < generations; gen++) {
            double[][] cur = p;
            long seed0 = gen * 1000L;
            double[] fit = new double[pop];
            IntStream.range(0, pop).parallel().forEach(i -> {
                double[] g = decode(cur[i]);
                double sum = 0;
                for (int k = 0; k < seeds; k++) sum += run(g, off, seed0 + k).cost();
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
        report("reference", reference, off);
        report("evolved", g, off);
        System.out.println("GENOME " + Arrays.toString(g));
    }
}