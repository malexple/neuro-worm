package ru.mcs.neuroworm.world;

import java.util.Set;
import java.util.SplittableRandom;
import ru.mcs.neuroworm.core.Network;
import ru.mcs.neuroworm.core.Neuron;
import ru.mcs.neuroworm.core.Simulator;

/** Сцена хемотаксиса. Геном по умолчанию выбирается по набору данных (Evolution.DATA). */
public final class ChemotaxisScenario implements Scenario {
    public static final double FOOD_X = 140, FOOD_Y = 100;

    public final Arena arena = new Arena(200);
    public final Network net;
    public final Simulator sim;
    public final Worm worm;
    public final World world;

    /** Дорогая операция (60 с симуляции для гомеостаза). Вызывать один раз. */
    public static double[] settle() { return Evolution.settle(); }

    public ChemotaxisScenario(double[] offsets, long seed) {
        this(offsets, seed, Set.of());
    }

    public ChemotaxisScenario(double[] offsets, long seed, Set<String> lesions) {
        this(offsets, seed, lesions, PlasticityEvolution.baseGenome());
    }

    public ChemotaxisScenario(double[] offsets, long seed, Set<String> lesions, double[] g) {
        this(offsets, seed, lesions, g, null);
    }

    /** Геном: 9 генов (базовый) или 15 (с пируэтами и мёртвой зоной). */
    public ChemotaxisScenario(double[] offsets, long seed, Set<String> lesions, double[] g, Arena.Zone pain) {
        net = Evolution.newNetwork();
        for (Neuron n : net.neurons()) {
            n.thresholdOffset = offsets[n.index];
            n.lesioned = lesions.contains(n.name);
        }
        Evolution.scale(net, Evolution.SENS, Evolution.INTER, g[7]);
        Evolution.scale(net, Evolution.INTER, Evolution.HEAD, g[8]);
        arena.food.add(new Arena.Source(FOOD_X, FOOD_Y, 1.0, 60));
        if (pain != null) arena.painZones.add(pain);
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
        worm.cfg.feedRadius = 3.0;
        if (g.length >= 15) {
            if (g.length >= 16) {
                worm.cfg.painGainHz = g[15];
                worm.cfg.commandTrigger = true;
            }
            worm.cfg.pirouetteThreshold = g[9];
            worm.cfg.reverseSteps = (int) Math.round(g[10]);
            worm.cfg.reverseSpeed = g[11];
            worm.cfg.omegaAngle = g[12];
            worm.cfg.turnDeadband = g[13];
            worm.cfg.cooldownSteps = (int) Math.round(g[14]);
        }
        world = new World(arena, worm, sim);
    }

    public double distanceToFood() { return arena.distance(worm.x, worm.y, FOOD_X, FOOD_Y); }
    @Override
    public String info() {
        if (worm.cfg.hungerRise <= 0 && worm.cfg.hungerFall <= 0) return "";
        return String.format("hunger %.2f | meals %d%s", worm.hunger, worm.meals(),
                worm.feeding() ? " | EATING" : "");
    }

    @Override public Arena arena() { return arena; }
    @Override public Network net() { return net; }
    @Override public Simulator sim() { return sim; }
    @Override public Worm worm() { return worm; }
    @Override public void step() { world.step(); }
    @Override public double distanceToTarget() { return distanceToFood(); }
    @Override public double targetX() { return FOOD_X; }
    @Override public double targetY() { return FOOD_Y; }
    @Override public double fieldAt(double x, double y) { return arena.odor(x, y); }
}