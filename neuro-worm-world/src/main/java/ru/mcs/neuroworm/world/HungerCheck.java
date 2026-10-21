package ru.mcs.neuroworm.world;

import java.util.SplittableRandom;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.IntStream;
import ru.mcs.neuroworm.core.ConnectomeLoader;
import ru.mcs.neuroworm.core.Network;
import ru.mcs.neuroworm.core.Neuron;
import ru.mcs.neuroworm.core.Simulator;

/** Этап 1c: голод повышает чувствительность к запаху; при h = 0 поведение прежнее. */
public final class HungerCheck {
    static final int SEEDS = 20, MAX_STEPS = 3000;
    static final double T_MAX = MAX_STEPS / 100.0, HUNGER_GAIN = 2.0, HANDICAP = 0.3;

    private record Result(double found, double time) { }

    static double[] run(double[] off, long seed, double handicap, double hunger, double hungerGain) {
        double[] g = PlasticityEvolution.baseGenome();
        Network net = Evolution.newNetwork();
        for (Neuron n : net.neurons()) n.thresholdOffset = off[n.index];
        Evolution.scale(net, Evolution.SENS, Evolution.INTER, g[7]);
        Evolution.scale(net, Evolution.INTER, Evolution.HEAD, g[8]);
        Arena arena = new Arena(200);
        arena.food.add(new Arena.Source(Evolution.FOOD[0], Evolution.FOOD[1], 1.0, 60));
        Simulator sim = new Simulator(net, seed);
        sim.run(300);
        SplittableRandom r = new SplittableRandom(seed);
        Worm w = new Worm(arena, net, 80, 100, r.nextDouble() * 2 * Math.PI);
        w.cfg.odorGainHz = handicap * g[0];
        w.cfg.awaDerivGain = handicap * g[1];
        w.cfg.awcOffGain = handicap * g[2];
        w.cfg.awcTonicHz = g[3];
        w.cfg.speedGain = g[4];
        w.cfg.turnGain = g[5];
        w.cfg.baseSpeed = g[6];
        w.cfg.feedRadius = 3;
        w.cfg.hungerGain = hungerGain;
        w.hunger = hunger;
        World world = new World(arena, w, sim);
        for (int i = 0; i < MAX_STEPS && !w.feeding(); i++) world.step();
        return new double[] {w.feeding() ? 1 : 0, w.feeding() ? w.contactStep() / 100.0 : T_MAX};
    }

    private static Result mean(double[] off, double handicap, double hunger, double hungerGain) {
        double[][] r = IntStream.range(0, SEEDS).parallel()
                .mapToObj(i -> run(off, 900_000 + i, handicap, hunger, hungerGain)).toArray(double[][]::new);
        double found = 0, time = 0;
        for (double[] x : r) { found += x[0]; time += x[1]; }
        return new Result(found, time / SEEDS);
    }

    public static void main(String[] args) {
        Logger.getLogger(ConnectomeLoader.class.getName()).setLevel(Level.SEVERE);
        System.out.println("data='" + Evolution.DATA + "'");
        double[] off = Evolution.settle();
        Result plain = mean(off, 1.0, 0, 0);
        Result plainK = mean(off, 1.0, 0, HUNGER_GAIN);
        Result fed = mean(off, HANDICAP, 0, HUNGER_GAIN);
        Result half = mean(off, HANDICAP, 0.5, HUNGER_GAIN);
        Result hungry = mean(off, HANDICAP, 1, HUNGER_GAIN);
        System.out.printf("full odor, k=0: found %.0f/%d time %.1f s | full odor, k=%.1f, fed: found %.0f time %.1f%n",
                plain.found(), SEEDS, plain.time(), HUNGER_GAIN, plainK.found(), plainK.time());
        System.out.printf("odor x%.1f: fed found %.0f time %.1f | half-hungry found %.0f time %.1f"
                        + " | hungry found %.0f time %.1f%n", HANDICAP, fed.found(), fed.time(),
                half.found(), half.time(), hungry.found(), hungry.time());
        boolean same = Math.abs(plain.time() - plainK.time()) < 1e-9 && plain.found() == plainK.found();
        boolean handicapBites = fed.found() < plain.found() || fed.time() >= 1.25 * plain.time();
        boolean better = hungry.found() >= fed.found()
                && (hungry.found() > fed.found() || hungry.time() <= 0.8 * fed.time());
        if (!handicapBites) System.out.println("INCONCLUSIVE: handicap too weak, lower HANDICAP");
        else System.out.println(same && better ? "PASS" : "FAIL");
    }
}