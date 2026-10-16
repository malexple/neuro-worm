package ru.mcs.neuroworm.core;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class HomeostasisTest {

    private static final int SETTLE = 60_000;
    private static final int WINDOW = 6_000;

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
    void homeostasisTamesNetworkAndKeepsOdorResponse() throws IOException {
        try (InputStream c = getClass().getResourceAsStream("/connectome.csv");
             InputStream t = getClass().getResourceAsStream("/neuron_types.csv")) {
            Assumptions.assumeTrue(c != null && t != null, "connectome files not in resources");
            Network net = ConnectomeLoader.load(
                    new InputStreamReader(t, StandardCharsets.UTF_8),
                    new InputStreamReader(c, StandardCharsets.UTF_8), 0.1, 0.0009).network();
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

            double mean = java.util.Arrays.stream(rest).average().orElse(0);
            double max = java.util.Arrays.stream(rest).max().orElse(0);
            double aiy = pair(net, odor, "AIYL", "AIYR") / pair(net, rest, "AIYL", "AIYR");
            double ava = pair(net, odor, "AVAL", "AVAR") / pair(net, rest, "AVAL", "AVAR");
            double avb = pair(net, odor, "AVBL", "AVBR") / pair(net, rest, "AVBL", "AVBR");
            System.out.printf("HOMEO rest mean=%.1f max=%.1f | odor/rest AIY=%.2f AVA=%.2f AVB=%.2f%n",
                    mean, max, aiy, ava, avb);

            assertTrue(mean > 1.5 && mean < 6, "mean Hz = " + mean);
            assertTrue(max < 30, "max Hz = " + max);
            assertTrue(aiy > 1.8, "AIY ratio = " + aiy);
            assertTrue(ava > 1.15, "AVA ratio = " + ava);
            assertTrue(avb > 1.15, "AVB ratio = " + avb);
        }
    }
}