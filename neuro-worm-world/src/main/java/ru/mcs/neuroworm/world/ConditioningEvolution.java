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

/** Ассоциативное обучение: соль (CS) + награда (US). Парная группа против непарной. */
public final class ConditioningEvolution {
    enum Mode { TEST, PAIRED, UNPAIRED, CS_ONLY, US_ONLY }

    record Cond(double aseConc, double aseOn, double aseOff, double aseTonic, double sAse,
                double eta, double tauElig, double aMinus, double rewardGain) { }

    static final String ASE = "^ASE[LR]$";
    static final double[] BASE = PlasticityEvolution.BASE;
    static final double CS_LEVEL = 0.5;
    static final double[] SALT = {140, 100};
    static final int GENES = 9, TRAIN = 3, EVAL = 3, CYCLE = 600;
    static final double CHANCE = 70;

    static double c(double v) { return Math.max(0, Math.min(1, v)); }

    static Cond decode(double[] x) {
        return new Cond(400 * c(x[0]), 3000 * c(x[1]), 3000 * c(x[2]), 40 * c(x[3]), 0.1 + 9.9 * c(x[4]),
                Math.pow(10, -4 + 3 * c(x[5])), 100 + 1900 * c(x[6]), 2 * c(x[7]), -1 + 2 * c(x[8]));
    }

    static final class Learner { volatile double[][] scales; }

    static double episode(Cond cd, double[] off, long seed, Learner learner, Mode mode) {
        Network net = Evolution.newNetwork();
        for (Neuron n : net.neurons()) n.thresholdOffset = off[n.index];
        Evolution.scale(net, Evolution.SENS, Evolution.INTER, BASE[7]);
        Evolution.scale(net, Evolution.INTER, Evolution.HEAD, BASE[8]);
        Evolution.scale(net, ASE, Evolution.INTER, cd.sAse());

        PlasticityManager pm = new PlasticityManager(net, ASE, Evolution.INTER);
        pm.eta = cd.eta();
        pm.tauElig = cd.tauElig();
        pm.aMinus = cd.aMinus();
        if (learner.scales != null) pm.applyScales(learner.scales);
        pm.setLearning(mode != Mode.TEST);

        Arena arena = new Arena(200);
        if (mode == Mode.TEST) arena.salt.add(new Arena.Source(SALT[0], SALT[1], 1.0, 60));
        Simulator sim = new Simulator(net, seed);
        sim.setTickListener(pm::afterTick);
        sim.run(300);
        SplittableRandom r = new SplittableRandom(seed);
        Worm w = new Worm(arena, net, 80, 100, r.nextDouble() * 2 * Math.PI);
        w.cfg.odorGainHz = 0;
        w.cfg.speedGain = BASE[4];
        w.cfg.turnGain = BASE[5];
        w.cfg.baseSpeed = BASE[6];
        w.cfg.aseConcGain = cd.aseConc();
        w.cfg.aseOnGain = cd.aseOn();
        w.cfg.aseOffGain = cd.aseOff();
        w.cfg.aseTonicHz = cd.aseTonic();
        World world = new World(arena, w, sim);

        double sum = 0;
        int cnt = 0;
        for (int i = 0; i < 2000; i++) {
            int phase = i % CYCLE;
            if (mode == Mode.TEST) {
                w.csOverride = Double.NaN;
            } else {
                boolean csOn = switch (mode) {
                    case PAIRED -> phase >= 300 && phase < 500;
                    case UNPAIRED, CS_ONLY -> phase < 200;
                    default -> false;
                };
                boolean usOn = mode != Mode.CS_ONLY && phase >= 400 && phase < 500;
                w.csOverride = csOn ? CS_LEVEL : 0.0;
                pm.reward = usOn ? cd.rewardGain() : 0.0;
            }
            world.step();
            if (mode == Mode.TEST && i >= 1500) {
                sum += arena.distance(w.x, w.y, SALT[0], SALT[1]);
                cnt++;
            }
        }
        if (mode != Mode.TEST) learner.scales = pm.scales();
        return cnt == 0 ? 0 : sum / cnt;
    }

    static Learner train(Cond cd, double[] off, long seed0, Mode mode) {
        Learner l = new Learner();
        for (int k = 0; k < TRAIN; k++) episode(cd, off, seed0 + k, l, mode);
        return l;
    }

    static double test(Cond cd, double[] off, Learner l, long seed0, int n) {
        return IntStream.range(0, n).parallel()
                .mapToDouble(k -> episode(cd, off, seed0 + k, l, Mode.TEST)).average().orElse(0);
    }

    static double fitness(double[] x, double[] off, long seed0) {
        Cond cd = decode(x);
        Learner p = train(cd, off, seed0, Mode.PAIRED);
        Learner u = train(cd, off, seed0 + 50, Mode.UNPAIRED);
        Learner o = train(cd, off, seed0 + 70, Mode.US_ONLY);
        Learner n = new Learner();
        double sp = 0, su = 0, so = 0, sn = 0;
        for (int k = 0; k < EVAL; k++) {
            long s = seed0 + 100 + k;
            sp += episode(cd, off, s, p, Mode.TEST);
            su += episode(cd, off, s, u, Mode.TEST);
            so += episode(cd, off, s, o, Mode.TEST);
            sn += episode(cd, off, s, n, Mode.TEST);
        }
        sp /= EVAL;
        su /= EVAL;
        so /= EVAL;
        sn /= EVAL;
        return (sp - sn) + 0.5 * Math.abs(su - sn) + 0.5 * Math.abs(so - sn) + 0.5 * Math.abs(sn - CHANCE);
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
            System.out.printf("gen %d best fitness %.1f median %.1f | %s%n", gen,
                    fit[order[0]], fit[order[pop / 2]], decode(best));
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

        Cond cd = decode(best);
        double naive = test(cd, off, new Learner(), 300_000, 20);
        double paired = test(cd, off, train(cd, off, 200_000, Mode.PAIRED), 300_000, 20);
        double unpaired = test(cd, off, train(cd, off, 210_000, Mode.UNPAIRED), 300_000, 20);
        double usOnly = test(cd, off, train(cd, off, 220_000, Mode.US_ONLY), 300_000, 20);
        System.out.printf("FINAL 20 fresh seeds: naive %.1f | paired %.1f | unpaired %.1f | US only %.1f%n",
                naive, paired, unpaired, usOnly);
    }
}