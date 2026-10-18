package ru.mcs.neuroworm.world;

import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import ru.mcs.neuroworm.core.ConnectomeLoader;
import ru.mcs.neuroworm.core.Network;
import ru.mcs.neuroworm.core.Neuron;

/** Отклик сети на стимуляцию ASH: частоты AVA/AVD/AVB и моторных пулов. */
public final class PainProbe {
    static double rate(Network net, long[] before, String regex, int ticks) {
        Pattern p = Pattern.compile(regex);
        long spikes = 0;
        int count = 0;
        for (Neuron n : net.neurons()) {
            if (!p.matcher(n.name).matches()) continue;
            spikes += n.spikeCount() - before[n.index];
            count++;
        }
        return count == 0 ? Double.NaN : spikes * 1000.0 / ticks / count;
    }

    static void probe(String name, double[] g, double[] off) {
        System.out.println(name);
        System.out.println("painHz |  ASH   AVA   AVD   AVB |  fwd  back | back-fwd | dor-ven");
        for (double hz : new double[] {0, 100, 200, 500, 1000}) {
            ChemotaxisScenario s = new ChemotaxisScenario(off, 1, Set.of(), g);
            Network net = s.net;
            net.get("ASHL").stimulusHz = hz;
            net.get("ASHR").stimulusHz = hz;
            s.sim.run(500);
            long[] before = new long[net.size()];
            for (Neuron n : net.neurons()) before[n.index] = n.spikeCount();
            int ticks = 2000;
            s.sim.run(ticks);
            double ash = rate(net, before, "^ASH[LR]$", ticks);
            double ava = rate(net, before, "^AVA[LR]$", ticks);
            double avd = rate(net, before, "^AVD[LR]$", ticks);
            double avb = rate(net, before, "^AVB[LR]$", ticks);
            double fwd = rate(net, before, "^(VB|DB)\\d\\d$", ticks);
            double back = rate(net, before, "^(VA|DA)\\d\\d$", ticks);
            double dor = rate(net, before, "^(SMDD|RMDD)[LR]$", ticks);
            double ven = rate(net, before, "^(SMDV|RMDV)[LR]$", ticks);
            System.out.printf("%6.0f | %5.1f %5.1f %5.1f %5.1f | %5.1f %5.1f | %8.1f | %7.1f%n",
                    hz, ash, ava, avd, avb, fwd, back, back - fwd, dor - ven);
        }
    }

    public static void main(String[] args) {
        Logger.getLogger(ConnectomeLoader.class.getName()).setLevel(Level.SEVERE);
        double[] off = Evolution.settle();
        probe("evolved", PainEvolution.reference(), off);
        probe("base", EfficiencyEvolution.extend(PlasticityEvolution.baseGenome()), off);
    }
}