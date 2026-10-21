package ru.mcs.neuroworm.world;

import java.util.SplittableRandom;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import ru.mcs.neuroworm.core.ConnectomeLoader;
import ru.mcs.neuroworm.core.Modulator;
import ru.mcs.neuroworm.core.Network;
import ru.mcs.neuroworm.core.Neuron;
import ru.mcs.neuroworm.core.PlasticityManager;
import ru.mcs.neuroworm.core.Simulator;

/** Аверсивное обучение: соль (CS) вместе с болью (ASH); стресс усиливает обучение. */
public final class AversiveConditioning {
    enum Mode { PAIRED, UNPAIRED, CS_ONLY, US_ONLY }

    record Setup(String name, boolean release, double sensitivity, double etaGain, double sign, Mode mode) { }
    record Outcome(double distance, double[][] scales) { }

    static final ConditioningEvolution.Cond COND = new ConditioningEvolution.Cond(
            72.67748465146242, 3000.0, 2339.3681199859384, 3.515398863875265, 10.0,
            0.00179437094059225, 271.71582918241495, 1.568973640348443, -0.6507395051063134);
    static final double PAIN_HZ = 500, RELEASE = 0.05, ASE_SENSITIVITY = 0.3, STRESS_ETA_GAIN = 2.0;
    static final double[] SALT = {140, 100};
    static final int CYCLE = 600, TRAIN_STEPS = 1200, TEST_STEPS = 2000;
    static final int CHECKPOINTS = 8, LEARNERS = 12, TESTS = 10;
    static final double AVERSION_MARGIN = 15;

    static Outcome episode(Setup su, double[] off, long seed, double[][] scales, boolean train) {
        double[] g = PlasticityEvolution.baseGenome();
        Network net = Evolution.newNetwork();
        for (Neuron n : net.neurons()) n.thresholdOffset = off[n.index];
        Evolution.scale(net, Evolution.SENS, Evolution.INTER, g[7]);
        Evolution.scale(net, Evolution.INTER, Evolution.HEAD, g[8]);
        Evolution.scale(net, ConditioningEvolution.ASE, Evolution.INTER, COND.sAse());
        if (su.release() || su.sensitivity() > 0) {
            Pattern ash = Pattern.compile("^ASH[LR]$"), ase = Pattern.compile("^ASE[LR]$");
            for (Neuron n : net.neurons()) {
                if (su.release() && ash.matcher(n.name).matches()) {
                    n.releases = Modulator.OCTOPAMINE;
                    n.releaseAmount = RELEASE;
                }
                if (su.sensitivity() > 0 && ase.matcher(n.name).matches()) n.stressSensitivity = su.sensitivity();
            }
        }
        PlasticityManager pm = PlasticityManager.forGroup(net, Evolution.GROUP_SALT);
        pm.eta = COND.eta();
        pm.tauElig = COND.tauElig();
        pm.aMinus = COND.aMinus();
        pm.stressGain = su.etaGain();
        pm.maxScale = Double.parseDouble(System.getProperty("neuroworm.maxScale", "10"));
        if (scales != null) pm.applyScales(scales);
        pm.setLearning(train);

        Arena arena = new Arena(200);
        if (!train) arena.salt.add(new Arena.Source(SALT[0], SALT[1], 1.0, 60));
        Simulator sim = new Simulator(net, seed);
        sim.setTickListener(() -> {
            pm.stress = sim.modulator(Modulator.OCTOPAMINE);
            pm.afterTick();
        });
        sim.run(300);
        Worm w = new Worm(arena, net, 80, 100, new SplittableRandom(seed).nextDouble() * 2 * Math.PI);
        w.cfg.odorGainHz = 0;
        w.cfg.speedGain = g[4];
        w.cfg.turnGain = g[5];
        w.cfg.baseSpeed = g[6];
        w.cfg.aseConcGain = COND.aseConc();
        w.cfg.aseOnGain = COND.aseOn();
        w.cfg.aseOffGain = COND.aseOff();
        w.cfg.aseTonicHz = COND.aseTonic();
        World world = new World(arena, w, sim);

        int steps = train ? TRAIN_STEPS : TEST_STEPS;
        double sum = 0;
        int cnt = 0;
        for (int i = 0; i < steps; i++) {
            if (train) {
                int ph = i % CYCLE;
                boolean cs = switch (su.mode()) {
                    case PAIRED -> ph >= 300 && ph < 500;
                    case UNPAIRED, CS_ONLY -> ph < 200;
                    default -> false;
                };
                boolean us = su.mode() != Mode.CS_ONLY && ph >= 400 && ph < 500;
                w.csOverride = cs ? 0.5 : 0.0;
                w.painOverride = us ? PAIN_HZ : 0.0;
                pm.reward = us ? su.sign() * Math.abs(COND.rewardGain()) : 0.0;
            } else {
                w.csOverride = Double.NaN;
            }
            world.step();
            if (!train && i >= 1500) {
                sum += arena.distance(w.x, w.y, SALT[0], SALT[1]);
                cnt++;
            }
        }
        return new Outcome(cnt == 0 ? 0 : sum / cnt, train ? pm.scales() : scales);
    }

    public static void main(String[] args) {
        Logger.getLogger(ConnectomeLoader.class.getName()).setLevel(Level.SEVERE);
        double[] off = Evolution.settle();
        Setup[] setups = {
                new Setup("paired", true, ASE_SENSITIVITY, STRESS_ETA_GAIN, 1, Mode.PAIRED),
                new Setup("unpaired", true, ASE_SENSITIVITY, STRESS_ETA_GAIN, 1, Mode.UNPAIRED),
                new Setup("US (pain) only", true, ASE_SENSITIVITY, STRESS_ETA_GAIN, 1, Mode.US_ONLY),
                new Setup("CS (salt) only", true, ASE_SENSITIVITY, STRESS_ETA_GAIN, 1, Mode.CS_ONLY),
                new Setup("paired, reward 0", true, ASE_SENSITIVITY, STRESS_ETA_GAIN, 0, Mode.PAIRED)};
        for (Setup su : setups) {
            double naive = IntStream.range(0, TESTS).parallel()
                    .mapToDouble(i -> episode(su, off, 2_000_000 + i, null, false).distance()).average().orElse(0);
            double[][] dist = new double[LEARNERS][CHECKPOINTS];
            IntStream.range(0, LEARNERS).parallel().forEach(l -> {
                double[][] scales = null;
                for (int c = 0; c < CHECKPOINTS; c++) {
                    scales = episode(su, off, 1_000_000 + l * 100L + c, scales, true).scales();
                    double[][] sc = scales;
                    dist[l][c] = IntStream.range(0, TESTS)
                            .mapToDouble(i -> episode(su, off, 2_000_000 + i, sc, false).distance()).average().orElse(0);
                }
            });
            StringBuilder sb = new StringBuilder();
            int reached = -1;
            for (int c = 0; c < CHECKPOINTS; c++) {
                double m = 0;
                for (int l = 0; l < LEARNERS; l++) m += dist[l][c] / LEARNERS;
                double var = 0;
                for (int l = 0; l < LEARNERS; l++) var += (dist[l][c] - m) * (dist[l][c] - m);
                double se = Math.sqrt(var / (LEARNERS - 1) / LEARNERS);
                sb.append(String.format(" %6.1f+-%.1f", m, se));
                if (reached < 0 && m >= naive + AVERSION_MARGIN) reached = 2 * (c + 1);
            }
            System.out.printf("%-22s %5.1f |%s | naive+%.0f reached after %s cycles%n", su.name(), naive, sb,
                    AVERSION_MARGIN, reached < 0 ? "never" : String.valueOf(reached));
        }
    }
}