package ru.mcs.neuroworm.world;

import java.util.SplittableRandom;
import ru.mcs.neuroworm.core.Network;
import ru.mcs.neuroworm.core.Neuron;
import ru.mcs.neuroworm.core.Simulator;

/** Сцена хемотаксиса с эволюционным геномом. Единая точка входа для viewer и тестов. */
public final class ChemotaxisScenario {
    public static final double FOOD_X = 140, FOOD_Y = 100;

    public final Arena arena = new Arena(200);
    public final Network net;
    public final Simulator sim;
    public final Worm worm;
    public final World world;

    /** Дорогая операция (60 с симуляции для гомеостаза). Вызывать один раз. */
    public static double[] settle() { return Evolution.settle(); }

    public ChemotaxisScenario(double[] offsets, long seed) {
        double[] g = PlasticityEvolution.BASE;
        net = Evolution.newNetwork();
        for (Neuron n : net.neurons()) n.thresholdOffset = offsets[n.index];
        Evolution.scale(net, Evolution.SENS, Evolution.INTER, g[7]);
        Evolution.scale(net, Evolution.INTER, Evolution.HEAD, g[8]);
        arena.food.add(new Arena.Source(FOOD_X, FOOD_Y, 1.0, 60));
        sim = new Simulator(net, seed);
        sim.run(300);
        worm = new Worm(arena, net, 80, 100, new SplittableRandom(seed).nextDouble() * 2 * Math.PI);
        worm.cfg.odorGainHz = g[0];
        worm.cfg.awaDerivGain = g[1];
        worm.cfg.awcOffGain = g[2];
        worm.cfg.awcTonicHz = g[3];
        worm.cfg.speedGain = g[4];
        worm.cfg.turnGain = g[5];
        worm.cfg.baseSpeed = g[6];
        world = new World(arena, worm, sim);
    }

    public double distanceToFood() { return arena.distance(worm.x, worm.y, FOOD_X, FOOD_Y); }
}