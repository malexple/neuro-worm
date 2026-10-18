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
    static final double[] LO = {0, 0, 0, 0, 0.0002, -0.1, 0.008, 0.1, 0.1, 0, 20, 0.002, 0.5, 0, 50};
    static final double[] HI = {400, 30000, 30000, 40, 0.005, 0.1, 0.03, 10, 10, 15, 150, 0.02, 3.0, 10, 400};
    static final int RUN_STEPS = 10_000;
    static final double T_MAX = 100, MISS_PENALTY = 100, ROT_WEIGHT = 0.02;
    public static final double[] EVOLVED = {99.30105452440773, 1901.8658246127948, 3000.0, 0.0,
            0.0040122839022799905, -0.014803350686865988, 0.3, 10.0, 8.690448777106926, 0.0,
            56.12782208572409, 0.12835368423677415, 0.5879719900339873, 7.2910889115466855, 120.45916972623894};

    static final boolean FREEZE_PIROUETTE = Boolean.getBoolean("neuroworm.freezePirouette");

    /** Переводит геном из унаследованного масштаба скорости (до 0,3 за шаг) в физический (до 0,03). */
    static double[] scaleToPhysical(double[] legacy) {
        double[] g = legacy.clone();
        g[1] *= 10;
        g[2] *= 10;
        g[4] *= 0.1;
        g[6] *= 0.1;
        g[11] *= 0.1;
        for (int i = 0; i < GENES; i++) g[i] = Math.max(LO[i], Math.min(HI[i], g[i]));
        return g;
    }

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

    static Result run(double[] g, double[] off, long seed) { return run(g, off, seed, Set.of()); }

    static Result run(double[] g, double[] off, long seed, Set<String> lesions) {
        ChemotaxisScenario s = new ChemotaxisScenario(off, seed, lesions, g);
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
        double[] g = evolve(off, Long.getLong("neuroworm.evoSeed", 0), reference, 25);
        report("reference", reference, off);
        report("evolved", g, off);
        System.out.println("GENOME " + Arrays.toString(g));
    }

    static double[] evolve(double[] off, long evoSeed, double[] seedGenome, int generations) {
        double sigma = Double.parseDouble(System.getProperty("neuroworm.sigma", "0.12"));
        int pop = Integer.getInteger("neuroworm.pop", 32), elite = 6;
        int seeds = Integer.getInteger("neuroworm.seeds", 6);
        SplittableRandom rng = new SplittableRandom(evoSeed);
        double[][] p = new double[pop][];
        int first = 0;
        if (seedGenome != null) p[first++] = encode(seedGenome);
        for (int i = first; i < pop; i++) p[i] = rng.doubles(GENES).toArray();
        if (FREEZE_PIROUETTE) for (double[] x : p) x[9] = 0;

        double[] best = p[0];
        for (int gen = 0; gen < generations; gen++) {
            double[][] cur = p;
            long seed0 = evoSeed * 1_000_000L + gen * 1000L;
            double[] fit = new double[pop];
            IntStream.range(0, pop).parallel().forEach(i -> {
                double[] g = decode(cur[i]);
                double sum = 0;
                int done = 0;
                for (int k = 0; k < seeds; k++) {
                    Result r = run(g, off, seed0 + k);
                    sum += r.cost();
                    done++;
                    if (k == 0 && !r.found()) break;
                }
                fit[i] = sum / done;
            });
            Integer[] order = IntStream.range(0, pop).boxed().toArray(Integer[]::new);
            Arrays.sort(order, Comparator.comparingDouble(i -> fit[i]));
            double[][] sorted = new double[pop][];
            for (int i = 0; i < pop; i++) sorted[i] = cur[order[i]];
            best = sorted[0];
            System.out.printf("evo %d gen %d best cost %.1f median %.1f genes %s%n", evoSeed, gen,
                    fit[order[0]], fit[order[pop / 2]], Arrays.toString(decode(best)));
            for (int i = elite; i < pop; i++) {
                double[] parent = sorted[rng.nextInt(elite)];
                double[] child = new double[GENES];
                for (int k = 0; k < GENES; k++) {
                    child[k] = Math.max(0, Math.min(1, parent[k] + sigma * rng.nextGaussian()));
                }
                if (FREEZE_PIROUETTE) child[9] = 0;
                sorted[i] = child;
            }
            p = sorted;
        }
        return decode(best);
    }
}