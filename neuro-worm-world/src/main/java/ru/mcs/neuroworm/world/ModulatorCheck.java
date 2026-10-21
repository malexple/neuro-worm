package ru.mcs.neuroworm.world;

import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import ru.mcs.neuroworm.core.ConnectomeLoader;
import ru.mcs.neuroworm.core.Modulator;
import ru.mcs.neuroworm.core.Network;
import ru.mcs.neuroworm.core.Neuron;
import ru.mcs.neuroworm.core.Simulator;

/** Этап 1a: стимуляция источника поднимает уровень модулятора, он затухает, сеть без приступов. */
public final class ModulatorCheck {
    static final double STIM_HZ = 100;

    record Source(String label, String regex, Modulator modulator) { }

    private record Window(double mean, double peak, double last, double popHz) { }

    public static void main(String[] args) {
        Logger.getLogger(ConnectomeLoader.class.getName()).setLevel(Level.SEVERE);
        System.out.println("data='" + Evolution.DATA + "'");
        double[] off = Evolution.settle();
        Source[] sources = {
                new Source("NSM -> serotonin", "^NSM[LR]$", Modulator.SEROTONIN),
                new Source("CEP/ADE/PDE -> dopamine", "^(CEPD|CEPV|ADE|PDE)[LR]$", Modulator.DOPAMINE),
                new Source("RIC -> octopamine", "^RIC[LR]$", Modulator.OCTOPAMINE)};
        System.out.printf("%-26s %8s %8s %10s %8s %8s  %s%n",
                "source", "rest", "peak", "+500 ticks", "rest Hz", "stim Hz", "result");
        for (Source s : sources) check(s, off);
    }

    static void check(Source s, double[] off) {
        Network net = Evolution.newNetwork();
        for (Neuron n : net.neurons()) n.thresholdOffset = off[n.index];
        ModulatorSources.attach(net, ModulatorSources.DEFAULT_AMOUNT);
        Simulator sim = new Simulator(net, 1);
        sim.run(300);
        Window rest = window(sim, net, s.modulator(), 2000);
        setStimulus(net, s.regex(), STIM_HZ);
        Window stim = window(sim, net, s.modulator(), 1000);
        setStimulus(net, s.regex(), 0);
        Window after = window(sim, net, s.modulator(), 500);
        boolean ok = stim.peak() > 5 * Math.max(rest.mean(), 0.01)
                && after.last() < 0.2 * stim.peak()
                && rest.popHz() > 1 && rest.popHz() < 6
                && stim.popHz() < SeizureCheck.LIMIT_HZ;
        System.out.printf("%-26s %8.3f %8.3f %10.3f %8.1f %8.1f  %s%n", s.label(), rest.mean(), stim.peak(),
                after.last(), rest.popHz(), stim.popHz(), ok ? "PASS" : "FAIL");
    }

    private static void setStimulus(Network net, String regex, double hz) {
        Pattern p = Pattern.compile(regex);
        for (Neuron n : net.neurons()) if (p.matcher(n.name).matches()) n.stimulusHz = hz;
    }

    private static Window window(Simulator sim, Network net, Modulator m, int ticks) {
        long before = totalSpikes(net);
        double sum = 0, peak = 0, last = 0;
        for (int i = 0; i < ticks; i++) {
            sim.step();
            last = sim.modulator(m);
            sum += last;
            peak = Math.max(peak, last);
        }
        double hz = (totalSpikes(net) - before) / (ticks / 1000.0) / net.size();
        return new Window(sum / ticks, peak, last, hz);
    }

    private static long totalSpikes(Network net) {
        long s = 0;
        for (Neuron n : net.neurons()) s += n.spikeCount();
        return s;
    }
}