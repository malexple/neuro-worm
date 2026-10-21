package ru.mcs.neuroworm.core;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.StringReader;
import org.junit.jupiter.api.Test;

class PlasticGroupLoaderTest {
    @Test
    void groupColumnSelectsOnlyFlaggedSynapses() throws IOException {
        String types = "Neuron,Neurotransmitter,Class\nA,GLU,SENSORY\nB,GLU,INTER\nC,GLU,INTER\n";
        String conn = "Source,Target,Type,Weight,Sign,Is_Plastic\nA,B,S,1,,salt\nA,C,S,1,,\n";
        var result = ConnectomeLoader.load(new StringReader(types), new StringReader(conn), 0.1, 0.001);
        assertEquals(1, result.report().plastic());
        PlasticityManager pm = PlasticityManager.forGroup(result.network(), "salt");
        assertArrayEquals(new String[] {"A"}, pm.preNames());
        assertArrayEquals(new String[] {"B"}, pm.postNames());
    }
}