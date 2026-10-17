package ru.mcs.neuroworm.core;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.io.StringReader;
import org.junit.jupiter.api.Test;

class SignOverrideTest {

    private static long qSpikes(int sign) {
        Network net = new Network();
        Neuron p = net.addNeuron("P1", NeuronType.SENSORY, Neurotransmitter.GLU);
        Neuron q = net.addNeuron("Q1", NeuronType.INTER, Neurotransmitter.GLU);
        p.baselineHz = 0;
        p.stimulusHz = 100;
        q.baselineHz = 0;
        q.stimulusHz = 100;
        net.connect(p, q, 0.9, 1, sign);
        new Simulator(net, 3).run(3000);
        return q.spikeCount();
    }

    @Test
    void negativeOverrideInhibitsEvenGlutamatePresynapse() {
        assertTrue(qSpikes(-1) < qSpikes(0), "inhibited " + qSpikes(-1) + " free " + qSpikes(0));
    }

    @Test
    void loaderReadsOptionalSignColumn() throws IOException {
        String types = "Neuron,Neurotransmitter,Class\nA,GLU,SENSORY\nB,GLU,INTER\n";
        String edges = "Source,Target,Type,Weight,Sign\nA,B,S,3,-1\nB,A,S,2\n";
        var res = ConnectomeLoader.load(new StringReader(types), new StringReader(edges), 0.1, 0.05);
        assertEquals(1, res.report().signOverrides());
        assertEquals(-1, res.network().get("A").synapses().get(0).sign());
        assertEquals(0, res.network().get("B").synapses().get(0).sign());
    }
}