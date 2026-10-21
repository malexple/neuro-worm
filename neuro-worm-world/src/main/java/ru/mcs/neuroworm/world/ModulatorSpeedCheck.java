package ru.mcs.neuroworm.world;

import java.util.SplittableRandom;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import ru.mcs.neuroworm.core.ConnectomeLoader;
import ru.mcs.neuroworm.core.Network;
import ru.mcs.neuroworm.core.Neuron;
import ru.mcs.neuroworm.core.Simulator;

/** Этап 1b: серотонин и дофамин замедляют червя; без включённых коэффициентов поведение прежнее. */
public final class ModulatorSpeedCheck {
    enum Mode { PLAIN, ATTACHED_K0, REST, SEROTONIN, DOPAMINE }

    static final int SEEDS = 20, STEPS = 1000;
    static final double STIM_HZ = 100, K_SEROTONIN = 2.5, K_DOPAMINE = 0.5;

    static double pathLength(double[] off, long seed, Mode mode) {
        double[] g = PlasticityEvolution.baseGenome();
        Network net = Evolution.newNetwork();
        for (Neuron n : net.neurons()) n.thresholdOffset = off[n.index];
        Evolution.scale(net, Evolution.SENS, Evolution.INTER, g[7]);
        Evolution.scale(net, Evolution.INTER, Evolution.HEAD, g[8]);
        if (mode != Mode.PLAIN) ModulatorSources.attach(net, ModulatorSources.DEFAULT_AMOUNT);
        if (mode == Mode.SEROTONIN) stimulate(net, "^NSM[LR]$");
        if (mode == Mode.DOPAMINE) stimulate(net, "^(CEPD|CEPV|ADE|PDE)[LR]$");

        Arena arena = new Arena(200);
        arena.food.add(new Arena.Source(Evolution.FOOD[0], Evolution.FOOD[1], 1.0, 60));
        Simulator sim = new Simulator(net, seed);
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
        if (mode == Mode.REST || mode == Mode.SEROTONIN || mode == Mode.DOPAMINE) {
            w.cfg.serotoninSlowing = K_SEROTONIN;
            w.cfg.dopamineSlowing = K_DOPAMINE;
        }
        World world = new World(arena, w, sim);
        for (int i = 0; i < STEPS; i++) world.step();
        return w.pathLength();
    }

    private static void stimulate(Network net, String regex) {
        Pattern p = Pattern.compile(regex);
        for (Neuron n : net.neurons()) if (p.matcher(n.name).matches()) n.stimulusHz = STIM_HZ;
    }

    private static double mean(double[] off, Mode mode) {
        return IntStream.range(0, SEEDS).parallel()
                .mapToDouble(i -> pathLength(off, 700_000 + i, mode)).average().orElse(0);
    }

    public static void main(String[] args) {
        Logger.getLogger(ConnectomeLoader.class.getName()).setLevel(Level.SEVERE);
        System.out.println("data='" + Evolution.DATA + "'");
        double[] off = Evolution.settle();
        double plain = mean(off, Mode.PLAIN);
        double k0 = mean(off, Mode.ATTACHED_K0);
        double rest = mean(off, Mode.REST);
        double sero = mean(off, Mode.SEROTONIN);
        double dopa = mean(off, Mode.DOPAMINE);
        System.out.printf("path length over %d steps, %d seeds%n", STEPS, SEEDS);
        System.out.printf("plain %.3f | sources attached, k=0 %.3f | k on, no stimulation %.3f"
                + " | NSM 100 Hz %.3f | CEP/ADE/PDE 100 Hz %.3f%n", plain, k0, rest, sero, dopa);
        System.out.printf("same as plain with k=0: %s | rest/plain %.2f (need >= 0.85)"
                        + " | serotonin/rest %.2f | dopamine/rest %.2f (need <= 0.80)%n",
                Math.abs(plain - k0) < 1e-9, rest / plain, sero / rest, dopa / rest);
        boolean ok = Math.abs(plain - k0) < 1e-9 && rest / plain >= 0.85
                && sero / rest <= 0.8 && dopa / rest <= 0.8;
        System.out.println(ok ? "PASS" : "FAIL");
    }
}