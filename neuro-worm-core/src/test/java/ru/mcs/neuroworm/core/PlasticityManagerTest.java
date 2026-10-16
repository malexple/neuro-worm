package ru.mcs.neuroworm.core;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class PlasticityManagerTest {

    private static double scaleAfter(double reward) {
        Network net = new Network();
        Neuron p = net.addNeuron("P1", NeuronType.SENSORY, Neurotransmitter.GLU);
        Neuron q = net.addNeuron("Q1", NeuronType.INTER, Neurotransmitter.GLU);
        p.baselineHz = 0;
        p.stimulusHz = 200;
        q.baselineHz = 0;
        net.connect(p, q, 0.9, 1);
        PlasticityManager pm = new PlasticityManager(net, "^P\\d$", "^Q\\d$");
        pm.aMinus = 0;
        pm.reward = reward;
        Simulator sim = new Simulator(net, 1);
        sim.setTickListener(pm::afterTick);
        sim.run(3000);
        return p.synapses().get(0).scale;
    }

    @Test
    void positiveRewardStrengthens() { assertTrue(scaleAfter(1) > 1.0); }

    @Test
    void negativeRewardWeakens() { assertTrue(scaleAfter(-1) < 1.0); }

    @Test
    void noRewardNoChange() { assertEquals(1.0, scaleAfter(0), 0.0); }
}