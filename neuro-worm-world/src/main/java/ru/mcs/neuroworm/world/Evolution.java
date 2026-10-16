package ru.mcs.neuroworm.world;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;
import java.util.SplittableRandom;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import ru.mcs.neuroworm.core.ConnectomeLoader;
import ru.mcs.neuroworm.core.Network;
import ru.mcs.neuroworm.core.Neuron;
import ru.mcs.neuroworm.core.Simulator;
import ru.mcs.neuroworm.core.Synapse;

/** Эволюция 9 параметров. Фитнес парный: d(с запахом) - d(слепой) на одних и тех же seed. */
public final class Evolution {
    // 0 awaC, 1 awaDeriv, 2 awcOff, 3 awcTonic, 4 speedGain, 5 turnGain, 6 baseSpeed, 7 sens->inter, 8 inter->head
    static final double[] LO = {0, 0, 0, 0, 0.002, -0.1, 0.0, 0.1, 0.1};
    static final double[] HI = {400, 3000, 3000, 40, 0.05, 0.1, 0.3, 10, 10};
    static final int GENES = 9;
    static final double K_CHEM = 0.1, K_GAP = 0.0009;
    static final String SENS = "^(AWA|AWC)[LR]$";
    static final String INTER = "^(AIY|AIA|AIB|AIZ)[LR]$";
    static final String HEAD = "^(SMD[DV]|RMD[DV]?|RIM|RIA|RIB|AIB)[LR]$";
    static final double[] FOOD = {140, 100};

    static double[] decode(double[] x) {
        double[] g = new double[GENES];
        for (int i = 0; i < GENES; i++) g[i] = LO[i] + (HI[i] - LO[i]) * Math.max(0, Math.min(1, x[i]));
        return g;
    }

    static Network newNetwork() {
        try (InputStream c = Evolution.class.getResourceAsStream("/connectome.csv");
             InputStream t = Evolution.class.getResourceAsStream("/neuron_types.csv")) {
            Network net = ConnectomeLoader.load(
                    new InputStreamReader(t, StandardCharsets.UTF_8),
                    new InputStreamReader(c, StandardCharsets.UTF_8), K_CHEM, K_GAP).network();
            for (Neuron n : net.neurons()) n.baselineHz = 1;
            return net;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static double[] settle() {
        Network net = newNetwork();
        for (Neuron n : net.neurons()) n.homeostasisEta = 2e-5;
        new Simulator(net, 1).run(60_000);
        return net.neurons().stream().mapToDouble(n -> n.thresholdOffset).toArray();
    }

    static void scale(Network net, String preRx, String postRx, double s) {
        Pattern a = Pattern.compile(preRx), b = Pattern.compile(postRx);
        for (Neuron pre : net.neurons()) {
            if (!a.matcher(pre.name).matches()) continue;
            for (Synapse syn : pre.synapses()) {
                if (b.matcher(net.get(syn.target()).name).matches()) syn.scale = s;
            }
        }
    }

    static double trial(double[] g, double[] offsets, long seed, boolean blind) {
        return trial(g, offsets, seed, blind, 80, 100, FOOD[0], FOOD[1]);
    }

    static double trial(double[] g, double[] offsets, long seed, boolean blind,
                        double sx, double sy, double fx, double fy) {
        Network net = newNetwork();
        for (Neuron n : net.neurons()) n.thresholdOffset = offsets[n.index];
        scale(net, SENS, INTER, g[7]);
        scale(net, INTER, HEAD, g[8]);

        Arena arena = new Arena(200);
        arena.food.add(new Arena.Source(FOOD[0], FOOD[1], 1.0, 60));
        Simulator sim = new Simulator(net, seed);
        sim.run(300);
        SplittableRandom r = new SplittableRandom(seed);
        Worm w = new Worm(arena, net, 80, 100, r.nextDouble() * 2 * Math.PI);
        w.cfg.odorGainHz = blind ? 0 : g[0];
        w.cfg.awaDerivGain = blind ? 0 : g[1];
        w.cfg.awcOffGain = blind ? 0 : g[2];
        w.cfg.awcTonicHz = g[3];
        w.cfg.speedGain = g[4];
        w.cfg.turnGain = g[5];
        w.cfg.baseSpeed = g[6];
        World world = new World(arena, w, sim);

        double sum = 0;
        int cnt = 0;
        for (int i = 0; i < 2000; i++) {
            world.step();
            if (i >= 1500) {
                sum += arena.distance(w.x, w.y, FOOD[0], FOOD[1]);
                cnt++;
            }
        }
        return sum / cnt;
    }

    static double[] odorAndBlind(double[] g, double[] off, long firstSeed, int n) {
        double[] a = IntStream.range(0, n).parallel().mapToDouble(i -> trial(g, off, firstSeed + i, false)).toArray();
        double[] b = IntStream.range(0, n).parallel().mapToDouble(i -> trial(g, off, firstSeed + i, true)).toArray();
        return new double[] {Arrays.stream(a).average().orElse(0), Arrays.stream(b).average().orElse(0)};
    }

    public static void main(String[] args) {
        Logger.getLogger(ConnectomeLoader.class.getName()).setLevel(Level.SEVERE);
        double[] off = settle();
        int pop = 32, elite = 6, generations = 25, seedsPerEval = 4;
        SplittableRandom rng = new SplittableRandom(0);
        double[][] p = new double[pop][];
        for (int i = 0; i < pop; i++) p[i] = rng.doubles(GENES).toArray();

        double[] best = p[0];
        for (int gen = 0; gen < generations; gen++) {
            double[][] cur = p;
            long seed0 = gen * 1000L;
            double[] fit = new double[pop];
            IntStream.range(0, pop).parallel().forEach(i -> {
                double[] ob = odorAndBlind(decode(cur[i]), off, seed0, seedsPerEval);
                fit[i] = ob[0] - ob[1];
            });
            Integer[] order = IntStream.range(0, pop).boxed().toArray(Integer[]::new);
            Arrays.sort(order, Comparator.comparingDouble(i -> fit[i]));
            double[][] sorted = new double[pop][];
            for (int i = 0; i < pop; i++) sorted[i] = cur[order[i]];
            best = sorted[0];
            System.out.printf("gen %d best(odor-blind) %.1f median %.1f genes %s%n",
                    gen, fit[order[0]], fit[order[pop / 2]], Arrays.toString(decode(best)));
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
        double[] ob = odorAndBlind(decode(best), off, 100_000, 20);
        System.out.printf("FINAL 20 fresh seeds: with odor %.1f | blind %.1f | difference %.1f (start distance 60)%n",
                ob[0], ob[1], ob[0] - ob[1]);
    }
}