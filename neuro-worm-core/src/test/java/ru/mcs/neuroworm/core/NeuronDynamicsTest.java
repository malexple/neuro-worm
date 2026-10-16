package ru.mcs.neuroworm.core;

import static org.junit.jupiter.api.Assertions.*;

import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

class NeuronDynamicsTest {

    private static Neuron lone(Network net) {
        return net.addNeuron("N", NeuronType.SENSORY, Neurotransmitter.GLU);
    }

    private static double rateHz(double stimulusHz, int ticks) {
        Network net = new Network();
        Neuron n = lone(net);
        n.stimulusHz = stimulusHz;
        new Simulator(net, 42).run(ticks);
        return n.spikeCount() * 1000.0 / ticks;
    }

    @Test
    void baselineActivityIsLowButNonZero() {
        double hz = rateHz(0, 10_000);
        assertTrue(hz >= 1 && hz <= 6, "baseline Hz = " + hz);
    }

    @Test
    void strongerStimulusGivesHigherRate() {
        double r0 = rateHz(0, 10_000);
        double r20 = rateHz(20, 10_000);
        double r100 = rateHz(100, 10_000);
        assertTrue(r20 > r0 + 2, r0 + " vs " + r20);
        assertTrue(r100 > r20 + 5, r20 + " vs " + r100);
    }

    @Test
    void constantStimulusAdapts() {
        Network net = new Network();
        Neuron n = lone(net);
        n.baselineHz = 0;
        n.stimulusHz = 100;
        Simulator sim = new Simulator(net, 7);
        int[] c = new int[2];
        sim.setSpikeListener((t, i) -> {
            if (t < 300) c[0]++;
            else if (t >= 2000) c[1]++;
        });
        sim.run(5000);
        double early = c[0] / 0.3;
        double late = c[1] / 3.0;
        assertTrue(early > 1.3 * late, "early=" + early + " late=" + late);
    }

    @Test
    void gapJunctionPropagatesActivity() {
        int withGap = spikesOfPassiveNeighbour(0.4);
        int without = spikesOfPassiveNeighbour(0);
        assertEquals(0, without);
        assertTrue(withGap >= 3, "passive neighbour spikes = " + withGap);
    }

    private static int spikesOfPassiveNeighbour(double g) {
        Network net = new Network();
        Neuron a = net.addNeuron("A", NeuronType.SENSORY, Neurotransmitter.GLU);
        Neuron b = net.addNeuron("B", NeuronType.INTER, Neurotransmitter.GLU);
        a.baselineHz = 0;
        a.stimulusHz = 100;
        b.baselineHz = 0;
        if (g > 0) net.connectGap(a, b, g);
        new Simulator(net, 3).run(5000);
        return (int) b.spikeCount();
    }

    @Test
    void gapJunctionIsNumericallyStable() {
        Network net = new Network();
        Neuron a = net.addNeuron("A", NeuronType.INTER, Neurotransmitter.GLU);
        Neuron b = net.addNeuron("B", NeuronType.INTER, Neurotransmitter.GLU);
        a.baselineHz = 0;
        b.baselineHz = 0;
        net.connectGap(a, b, 0.4);
        a.setV(0.8);
        Simulator sim = new Simulator(net, 1);
        for (int i = 0; i < 200; i++) {
            sim.step();
            assertTrue(Math.abs(a.v()) <= 1.0 && Math.abs(b.v()) <= 1.0, "diverged at tick " + i);
        }
        assertTrue(Math.abs(a.v() - b.v()) < 1e-3);
    }

    @Test
    void unstableGapConductanceIsRejected() {
        Network net = new Network();
        Neuron a = net.addNeuron("A", NeuronType.INTER, Neurotransmitter.GLU);
        Neuron b = net.addNeuron("B", NeuronType.INTER, Neurotransmitter.GLU);
        net.connectGap(a, b, 0.6);
        assertThrows(IllegalArgumentException.class, () -> new Simulator(net, 1));
    }

    @Test
    void stressLowersThreshold() {
        Network net = new Network();
        Neuron n = lone(net);
        n.stressSensitivity = 0.5;
        Simulator sim = new Simulator(net, 1);
        double calm = n.effectiveThreshold(sim.modulator(Modulator.OCTOPAMINE));
        sim.setModulator(Modulator.OCTOPAMINE, 1.0);
        double stressed = n.effectiveThreshold(sim.modulator(Modulator.OCTOPAMINE));
        assertTrue(stressed < calm, calm + " -> " + stressed);
    }

    @Test
    void balancedNetworkDoesNotRunAway() {
        Network net = new Network();
        int total = 50, excitatory = 40;
        for (int i = 0; i < total; i++) {
            net.addNeuron("n" + i, NeuronType.INTER, i < excitatory ? Neurotransmitter.GLU : Neurotransmitter.GABA);
        }
        SplittableRandom r = new SplittableRandom(5);
        for (Neuron pre : net.neurons()) {
            boolean exc = pre.transmitter.sign > 0;
            for (int k = 0; k < 10; k++) {
                net.connect(pre, net.get(r.nextInt(total)), exc ? 0.35 : 0.7, 1 + r.nextInt(3));
            }
        }
        new Simulator(net, 11).run(5000);
        double max = net.neurons().stream().mapToDouble(n -> n.spikeCount() / 5.0).max().orElse(0);
        double mean = net.neurons().stream().mapToDouble(n -> n.spikeCount() / 5.0).average().orElse(0);
        assertTrue(max < 20, "max Hz = " + max);
        assertTrue(mean > 0.5, "mean Hz = " + mean);
    }
}