package ru.mcs.neuroworm.core;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class ConnectomeLoaderTest {

    private static final String TYPES = """
            Neuron,Neurotransmitter,Class
            AWA,ACh,SENSORY
            DD1,GABA,MOTOR
            AVB,GLU,INTER
            """;

    private static final String EDGES = """
            Source,Target,Type,Weight
            AWA,AVB,S,3
            DD1,AVB,S,2
            AVB,AVA,S,1
            AVA,AVB,G,4
            AVB,AVA,G,4
            """;

    @Test
    void loadsMiniConnectome() throws IOException {
        var res = ConnectomeLoader.load(new StringReader(TYPES), new StringReader(EDGES), 0.1, 0.05);
        Network net = res.network();

        assertEquals(4, res.report().neurons());
        assertEquals(3, res.report().chemical());
        assertEquals(1, res.report().gap());
        assertEquals(java.util.List.of("AVA"), res.report().inferred());

        assertEquals(NeuronType.SENSORY, net.get("AWA").type);
        assertEquals(Neurotransmitter.GABA, net.get("DD1").transmitter);
        assertEquals(NeuronType.INTER, net.get("AVA").type);
        assertEquals(1, net.get("AWA").synapses().size());
        assertEquals(0.3, net.get("AWA").synapses().get(0).weight(), 1e-12);
        assertEquals(1, net.get("AVA").gapLinks().size());
        assertEquals(1, net.get("AVB").gapLinks().size());
        assertEquals(0.2, net.get("AVB").gapLinks().get(0).conductance(), 1e-12);
    }

    @Test
    void reportsLineNumberOnBadType() {
        String bad = "Source,Target,Type,Weight\nAWA,AVB,X,3\n";
        var ex = assertThrows(IllegalArgumentException.class, () ->
                ConnectomeLoader.load(new StringReader(TYPES), new StringReader(bad), 0.1, 0.05));
        assertTrue(ex.getMessage().contains("line 2"), ex.getMessage());
    }

    @Test
    void realConnectomeIfPresent() throws IOException {
        try (InputStream c = getClass().getResourceAsStream("/connectome.csv");
             InputStream t = getClass().getResourceAsStream("/neuron_types.csv")) {
            Assumptions.assumeTrue(c != null && t != null, "connectome files not in resources");
            var res = ConnectomeLoader.load(
                    new InputStreamReader(t, StandardCharsets.UTF_8),
                    new InputStreamReader(c, StandardCharsets.UTF_8), 0.01, 0.005);
            System.out.println("REPORT: " + res.report());
            assertTrue(res.report().neurons() >= 250 && res.report().neurons() <= 500);
            assertTrue(res.report().chemical() > 1000);
        }
    }
}