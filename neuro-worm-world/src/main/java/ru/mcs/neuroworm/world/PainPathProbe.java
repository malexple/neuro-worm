package ru.mcs.neuroworm.world;

import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import ru.mcs.neuroworm.core.ConnectomeLoader;
import ru.mcs.neuroworm.core.Network;
import ru.mcs.neuroworm.core.Neuron;

/** Какие промежуточные классы определяют отклик AVA/AVD/AVB и пулов на боль. */
public final class PainPathProbe {
    static double[] measure(double[] g, double[] off, Set<String> lesions, double hz) {
        double[] acc = new double[6];
        int seeds = 3;
        for (int seed = 1; seed <= seeds; seed++) {
            ChemotaxisScenario s = new ChemotaxisScenario(off, seed, lesions, g);
            Network net = s.net;
            net.get("ASHL").stimulusHz = hz;
            net.get("ASHR").stimulusHz = hz;
            s.sim.run(500);
            long[] before = new long[net.size()];
            for (Neuron n : net.neurons()) before[n.index] = n.spikeCount();
            int ticks = 2000;
            s.sim.run(ticks);
            double fwd = PainProbe.rate(net, before, "^(VB|DB)\\d\\d$", ticks);
            double back = PainProbe.rate(net, before, "^(VA|DA)\\d\\d$", ticks);
            acc[0] += PainProbe.rate(net, before, "^AVA[LR]$", ticks) / seeds;
            acc[1] += PainProbe.rate(net, before, "^AVD[LR]$", ticks) / seeds;
            acc[2] += PainProbe.rate(net, before, "^AVB[LR]$", ticks) / seeds;
            acc[3] += fwd / seeds;
            acc[4] += back / seeds;
            acc[5] += (back - fwd) / seeds;
        }
        return acc;
    }

    public static void main(String[] args) {
        Logger.getLogger(ConnectomeLoader.class.getName()).setLevel(Level.SEVERE);
        double[] off = Evolution.settle();
        double[] g = PainEvolution.reference();
        System.out.println("lesion |  AVA   AVD   AVB |  fwd  back | back-fwd (rise over pain 0 Hz)");
        for (String l : new String[] {"", "AIB", "AIZ", "AIA", "RIM", "RIA", "AVD", "AVE", "ADF"}) {
            Set<String> lesions = l.isEmpty() ? Set.of() : Set.of(l + "L", l + "R");
            double[] rest = measure(g, off, lesions, 0);
            double[] pain = measure(g, off, lesions, 500);
            System.out.printf("%-6s | %5.1f %5.1f %5.1f | %5.1f %5.1f | %5.1f (%+.1f)%n", l.isEmpty() ? "none" : l,
                    pain[0], pain[1], pain[2], pain[3], pain[4], pain[5], pain[5] - rest[5]);
        }
    }
}