package ru.mcs.neuroworm.world;

import java.util.Arrays;
import java.util.Comparator;
import java.util.SplittableRandom;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.IntStream;
import ru.mcs.neuroworm.core.ConnectomeLoader;
import ru.mcs.neuroworm.core.Network;
import ru.mcs.neuroworm.core.Neuron;
import ru.mcs.neuroworm.core.PlasticityManager;
import ru.mcs.neuroworm.core.Simulator;

/** Эволюция правила обучения: покалеченный червь + R-STDP на путях AWA/AWC -> AIY/AIA/AIB/AIZ. */
public final class PlasticityEvolution {
    record Rule(double eta, double tauElig, double aMinus, double kappa, double d0, double blend) { }

    static final double[] BASE = {390.283, 945.166, 1663.948, 3.553, 0.011, 0.033, 0.263, 6.488, 1.03};
    static final int GENES = 6, TRAIN = 4, EVAL = 4;
    static final boolean FIX_D0 = Boolean.getBoolean("fixD0");

    static double[] crippled() {
        double[] g = BASE.clone();
        g[7] = 1.0;
        return g;
    }

    static double c(double v) { return Math.max(0, Math.min(1, v)); }

    static Rule decode(double[] x) {
        return new Rule(Math.pow(10, -4 + 3 * c(x[0])), 100 + 1900 * c(x[1]), 2 * c(x[2]),
                20 * c(x[3]), FIX_D0 ? 0 : -1 + 2 * c(x[4]), c(x[5]));
    }

    static final class Learner { volatile double[][] scales; }

    static double episode(double[] g, double[] off, long seed, Rule rule, Learner learner, boolean learn) {
        Network net = Evolution.newNetwork();
        for (Neuron n : net.neurons()) n.thresholdOffset = off[n.index];
        Evolution.scale(net, Evolution.SENS, Evolution.INTER, g[7]);
        Evolution.scale(net, Evolution.INTER, Evolution.HEAD, g[8]);

        PlasticityManager pm = null;
        if (rule != null) {
            pm = new PlasticityManager(net, Evolution.SENS, Evolution.INTER);
            pm.eta = rule.eta();
            pm.tauElig = rule.tauElig();
            pm.aMinus = rule.aMinus();
            if (learner.scales != null) pm.applyScales(learner.scales);
            pm.setLearning(learn);
        }

        Arena arena = new Arena(200);
        arena.food.add(new Arena.Source(Evolution.FOOD[0], Evolution.FOOD[1], 1.0, 60));
        Simulator sim = new Simulator(net, seed);
        if (pm != null) sim.setTickListener(pm::afterTick);
        sim.run(300);
        SplittableRandom r = new SplittableRandom(seed);
        Worm w = new Worm(arena, net, 80, 100, r.nextDouble() * 2 * Math.PI);
        w.cfg.odorGainHz = g[0];
        w.cfg.awaDerivGain = g[1];
        w.cfg.awcOffGain = g[2];
        w.cfg.awcTonicHz = g[3];
        w.cfg.speedGain = g[4];
        w.cfg.turnGain = g[5];
        w.cfg.baseSpeed = g[6];
        World world = new World(arena, w, sim);

        double sum = 0;
        int cnt = 0;
        for (int i = 0; i < 2000; i++) {
            if (pm != null && learn) {
                double d = rule.d0() + rule.kappa() * (w.dcMean() - rule.blend() * w.dcBaseline());
                pm.reward = Math.max(-1, Math.min(1, d));
            }
            world.step();
            if (i >= 1500) {
                sum += arena.distance(w.x, w.y, Evolution.FOOD[0], Evolution.FOOD[1]);
                cnt++;
            }
        }
        if (pm != null && learn) learner.scales = pm.scales();
        return sum / cnt;
    }

    static double fitness(double[] x, double[] off, long seed0) {
        Rule rule = decode(x);
        Learner learner = new Learner();
        double[] g = crippled();
        for (int k = 0; k < TRAIN; k++) episode(g, off, seed0 + k, rule, learner, true);
        double sum = 0;
        for (int k = 0; k < EVAL; k++) sum += episode(g, off, seed0 + 100 + k, rule, learner, false);
        return sum / EVAL;
    }

    static double control(double[] g, double[] off, long firstSeed, int n) {
        return IntStream.range(0, n).parallel()
                .mapToDouble(k -> episode(g, off, firstSeed + k, null, null, false)).average().orElse(0);
    }

    public static void main(String[] args) {
        Logger.getLogger(ConnectomeLoader.class.getName()).setLevel(Level.SEVERE);
        double[] off = Evolution.settle();
        int pop = 32, elite = 6, generations = 25;
        SplittableRandom rng = new SplittableRandom(0);
        double[][] p = new double[pop][];
        for (int i = 0; i < pop; i++) p[i] = rng.doubles(GENES).toArray();

        double[] best = p[0];
        for (int gen = 0; gen < generations; gen++) {
            double[][] cur = p;
            long seed0 = gen * 1000L;
            double[] fit = new double[pop];
            IntStream.range(0, pop).parallel().forEach(i -> fit[i] = fitness(cur[i], off, seed0));
            Integer[] order = IntStream.range(0, pop).boxed().toArray(Integer[]::new);
            Arrays.sort(order, Comparator.comparingDouble(i -> fit[i]));
            double[][] sorted = new double[pop][];
            for (int i = 0; i < pop; i++) sorted[i] = cur[order[i]];
            best = sorted[0];
            System.out.printf("gen %d best %.1f median %.1f | control(no learning) %.1f | %s%n", gen,
                    fit[order[0]], fit[order[pop / 2]], control(crippled(), off, seed0 + 100, EVAL), decode(best));
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

        Rule rule = decode(best);
        double learned = trainAndEval(rule, off);
        Rule flipped = new Rule(rule.eta(), rule.tauElig(), rule.aMinus(), -rule.kappa(), rule.d0(), rule.blend());
        double flippedDist = trainAndEval(flipped, off);
        double ctrl = control(crippled(), off, 300_000, 20);
        double ref = control(BASE, off, 300_000, 20);
        System.out.printf("FINAL 20 fresh seeds: learned %.1f | reward sign flipped %.1f | crippled no learning %.1f"
                + " | evolved reference %.1f%n", learned, flippedDist, ctrl, ref);
    }

    static double trainAndEval(Rule rule, double[] off) {
        Learner learner = new Learner();
        for (int k = 0; k < TRAIN; k++) episode(crippled(), off, 200_000 + k, rule, learner, true);
        return IntStream.range(0, 20).parallel()
                .mapToDouble(k -> episode(crippled(), off, 300_000 + k, rule, learner, false))
                .average().orElse(0);
    }

}