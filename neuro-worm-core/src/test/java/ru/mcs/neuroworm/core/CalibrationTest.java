package ru.mcs.neuroworm.core;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class CalibrationTest {

    private static final double K_CHEM = 0.06;
    private static final double K_GAP = 0.0009;
    private static final int TICKS = 4000;

    private static Network run(boolean stimulate) throws IOException {
        try (InputStream c = CalibrationTest.class.getResourceAsStream("/connectome.csv");
             InputStream t = CalibrationTest.class.getResourceAsStream("/neuron_types.csv")) {
            Assumptions.assumeTrue(c != null && t != null, "connectome files not in resources");
            Network net = ConnectomeLoader.load(
                    new InputStreamReader(t, StandardCharsets.UTF_8),
                    new InputStreamReader(c, StandardCharsets.UTF_8), K_CHEM, K_GAP).network();
            for (Neuron n : net.neurons()) n.baselineHz = 1;
            if (stimulate) {
                for (String s : List.of("AWAL", "AWAR", "AWCL", "AWCR")) net.get(s).stimulusHz = 80;
            }
            new Simulator(net, 1).run(TICKS);
            return net;
        }
    }

    private static double hz(Network net, String name) {
        return net.get(name).spikeCount() * 1000.0 / TICKS;
    }

    @Test
    void restingNetworkIsStable() throws IOException {
        Network net = run(false);
        double mean = net.neurons().stream().mapToDouble(n -> n.spikeCount() * 1000.0 / TICKS).average().orElse(0);
        double max = net.neurons().stream().mapToDouble(n -> n.spikeCount() * 1000.0 / TICKS).max().orElse(0);
        System.out.printf("REST mean=%.1f max=%.1f%n", mean, max);
        assertTrue(mean > 1 && mean < 10, "mean Hz = " + mean);
        assertTrue(max < 50, "max Hz = " + max);
    }

    @Test
    void odorActivatesFirstRelayNeurons() throws IOException {
        Network rest = run(false);
        Network odor = run(true);
        double before = hz(rest, "AIYL") + hz(rest, "AIYR");
        double after = hz(odor, "AIYL") + hz(odor, "AIYR");
        System.out.printf("AIY rest=%.1f odor=%.1f | AVAL %.1f -> %.1f | AVBL %.1f -> %.1f%n",
                before, after, hz(rest, "AVAL"), hz(odor, "AVAL"), hz(rest, "AVBL"), hz(odor, "AVBL"));
        assertTrue(after > 1.5 * before, before + " -> " + after);
    }
}