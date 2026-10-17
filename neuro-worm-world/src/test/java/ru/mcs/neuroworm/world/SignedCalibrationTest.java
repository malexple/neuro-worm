package ru.mcs.neuroworm.world;

import static org.junit.jupiter.api.Assertions.*;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import ru.mcs.neuroworm.core.ConnectomeLoader;
import ru.mcs.neuroworm.core.Network;
import ru.mcs.neuroworm.core.Neuron;
import ru.mcs.neuroworm.core.Simulator;

class SignedCalibrationTest {
    private static final int SETTLE = 60_000, WINDOW = 6_000;

    private static double[] window(Network net, Simulator sim) {
        long[] before = net.neurons().stream().mapToLong(Neuron::spikeCount).toArray();
        sim.run(WINDOW);
        double[] hz = new double[net.size()];
        for (Neuron n : net.neurons()) hz[n.index] = (n.spikeCount() - before[n.index]) * 1000.0 / WINDOW;
        return hz;
    }

    private static double pair(Network net, double[] hz, String a, String b) {
        return hz[net.get(a).index] + hz[net.get(b).index];
    }

    @Test
    void signedNetworkIsStableAndResponds() throws Exception {
        try (InputStream c = getClass().getResourceAsStream("/connectome_signed.csv");
             InputStream t = getClass().getResourceAsStream("/neuron_types_signed.csv")) {
            var res = ConnectomeLoader.load(new InputStreamReader(t, StandardCharsets.UTF_8),
                    new InputStreamReader(c, StandardCharsets.UTF_8), 0.1, 0.0009);
            Network net = res.network();
            System.out.println("SIGNED REPORT " + res.report().neurons() + " " + res.report().chemical() + " "
                    + res.report().gap() + " overrides " + res.report().signOverrides() + " inferred "
                    + res.report().inferred().size());
            for (Neuron n : net.neurons()) {
                n.baselineHz = 1;
                n.homeostasisEta = 2e-5;
            }
            Simulator sim = new Simulator(net, 1);
            sim.run(SETTLE);
            for (Neuron n : net.neurons()) n.homeostasisEta = 0;
            double[] rest = window(net, sim);
            for (String s : List.of("AWAL", "AWAR", "AWCL", "AWCR")) net.get(s).stimulusHz = 80;
            double[] odor = window(net, sim);

            double mean = Arrays.stream(rest).average().orElse(0);
            double max = Arrays.stream(rest).max().orElse(0);
            double aiy = pair(net, odor, "AIYL", "AIYR") / pair(net, rest, "AIYL", "AIYR");
            double aib = pair(net, odor, "AIBL", "AIBR") / pair(net, rest, "AIBL", "AIBR");
            double ava = pair(net, odor, "AVAL", "AVAR") / pair(net, rest, "AVAL", "AVAR");
            double avb = pair(net, odor, "AVBL", "AVBR") / pair(net, rest, "AVBL", "AVBR");
            System.out.printf("SIGNED rest mean=%.1f max=%.1f | AIY=%.2f AIB=%.2f AVA=%.2f AVB=%.2f%n",
                    mean, max, aiy, aib, ava, avb);
            assertEquals(1705, res.report().signOverrides());
            assertTrue(mean > 1.5 && mean < 6, "mean " + mean);
            assertTrue(max < 30, "max " + max);
            assertTrue(aiy > 1.15 && aib > 1.8 && ava > 1.1 && avb > 1.1);
        }
    }
}