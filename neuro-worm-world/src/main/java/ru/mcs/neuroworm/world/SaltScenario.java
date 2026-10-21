package ru.mcs.neuroworm.world;

import java.util.SplittableRandom;
import ru.mcs.neuroworm.core.Network;
import ru.mcs.neuroworm.core.Neuron;
import ru.mcs.neuroworm.core.PlasticityManager;
import ru.mcs.neuroworm.core.Simulator;

/** Демонстрация ассоциативного обучения: градиент соли, обучение «соль + награда». */
public final class SaltScenario implements Scenario {
    private enum Phase { TEST, TRAIN }

    public static final double SALT_X = 140, SALT_Y = 100;
    private static final double CS_LEVEL = 0.5;
    private static final int CYCLE = 600;
    private static final double STOP_RADIUS = 5;
    private boolean arrived;
    private double holdX, holdY;

    /** Правило, найденное эволюцией на сети со знаками Fenyves (строка COND из лога). */
    private static final ConditioningEvolution.Cond COND = new ConditioningEvolution.Cond(
            72.67748465146242, 3000.0, 2339.3681199859384, 3.515398863875265, 10.0,
            0.00179437094059225, 271.71582918241495, 1.568973640348443, -0.6507395051063134);

    private final Arena arena = new Arena(200);
    private final Network net;
    private final Simulator sim;
    private final PlasticityManager pm;
    private Worm worm;
    private World world;
    private Phase phase = Phase.TEST;
    private int cycleStep;
    private boolean trained;

    public static double[] settle() { return Evolution.settle(); }

    public static SaltScenario create(double[] offsets, long seed) { return new SaltScenario(offsets, seed); }

    private SaltScenario(double[] offsets, long seed) {
        double[] g = PlasticityEvolution.baseGenome();
        net = Evolution.newNetwork();
        for (Neuron n : net.neurons()) n.thresholdOffset = offsets[n.index];
        Evolution.scale(net, Evolution.SENS, Evolution.INTER, g[7]);
        Evolution.scale(net, Evolution.INTER, Evolution.HEAD, g[8]);
        Evolution.scale(net, ConditioningEvolution.ASE, Evolution.INTER, COND.sAse());
        pm = PlasticityManager.forGroup(net, Evolution.GROUP_SALT);
        pm.eta = COND.eta();
        pm.tauElig = COND.tauElig();
        pm.aMinus = COND.aMinus();
        pm.setLearning(false);
        sim = new Simulator(net, seed);
        sim.setTickListener(pm::afterTick);
        sim.run(300);
        enterTest();
        buildWorm(seed);
    }

    private void buildWorm(long seed) {
        double[] g = PlasticityEvolution.baseGenome();
        worm = new Worm(arena, net, 80, 100, new SplittableRandom(seed).nextDouble() * 2 * Math.PI);
        worm.cfg.odorGainHz = 0;
        worm.cfg.speedGain = g[4];
        worm.cfg.turnGain = g[5];
        worm.cfg.baseSpeed = g[6];
        worm.cfg.aseConcGain = COND.aseConc();
        worm.cfg.aseOnGain = COND.aseOn();
        worm.cfg.aseOffGain = COND.aseOff();
        worm.cfg.aseTonicHz = COND.aseTonic();
        world = new World(arena, worm, sim);
        arrived = false;
    }

    private void enterTest() {
        arena.salt.clear();
        arena.salt.add(new Arena.Source(SALT_X, SALT_Y, 1.0, 60));
        pm.setLearning(false);
        pm.reward = 0;
        phase = Phase.TEST;
        arrived = false;
    }

    private void enterTrain() {
        arena.salt.clear();
        pm.setLearning(true);
        cycleStep = 0;
        phase = Phase.TRAIN;
    }

    /** Парное обучение: соль (в окнах) вместе с наградой. Синхронно, без отрисовки. */
    public void train(int episodes, long seed) {
        for (int k = 0; k < episodes; k++) {
            enterTrain();
            buildWorm(seed + k);
            for (int i = 0; i < 2000; i++) step();
        }
        trained = true;
        restartRun(seed + 1000);
    }

    /** Новый прогон в градиенте соли с теми же весами. */
    public void restartRun(long seed) {
        enterTest();
        buildWorm(seed);
    }

    @Override
    public void step() {
        if (phase == Phase.TRAIN) {
            int ph = cycleStep % CYCLE;
            worm.csOverride = (ph >= 300 && ph < 500) ? CS_LEVEL : 0.0;
            pm.reward = (ph >= 400 && ph < 500) ? COND.rewardGain() : 0.0;
            cycleStep++;
        } else {
            worm.csOverride = Double.NaN;
        }
        world.step();
        if (phase == Phase.TEST) {
            if (arrived) {
                worm.x = holdX;
                worm.y = holdY;
            } else if (distanceToTarget() <= STOP_RADIUS) {
                arrived = true;
                holdX = worm.x;
                holdY = worm.y;
            }
        }
    }

    /** Средний масштаб существующих связей ASE -> интернейроны (старт 10,0). */
    public double meanAseScale() {
        double sum = 0;
        int n = 0;
        for (double[] row : pm.scales()) {
            for (double v : row) {
                if (!Double.isNaN(v)) {
                    sum += v;
                    n++;
                }
            }
        }
        return n == 0 ? 0 : sum / n;
    }

    @Override public Arena arena() { return arena; }
    @Override public Network net() { return net; }
    @Override public Simulator sim() { return sim; }
    @Override public Worm worm() { return worm; }
    @Override public double distanceToTarget() { return arena.distance(worm.x, worm.y, SALT_X, SALT_Y); }
    @Override public double targetX() { return SALT_X; }
    @Override public double targetY() { return SALT_Y; }
    @Override public double fieldAt(double x, double y) { return arena.salt(x, y); }

    @Override
    public String info() {
        return String.format("%s | ASE scale %.2f%s", trained ? "TRAINED" : "naive", meanAseScale(),
                arrived ? " | ARRIVED" : "");
    }
}