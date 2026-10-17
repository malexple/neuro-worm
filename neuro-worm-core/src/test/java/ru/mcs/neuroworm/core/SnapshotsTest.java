package ru.mcs.neuroworm.core;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class SnapshotsTest {

    private static Network build() {
        Network net = new Network();
        Neuron p = net.addNeuron("P1", NeuronType.SENSORY, Neurotransmitter.GLU);
        Neuron q = net.addNeuron("Q1", NeuronType.INTER, Neurotransmitter.GLU);
        net.connect(p, q, 0.4, 1);
        p.baselineHz = 0;
        q.baselineHz = 0;
        p.stimulusHz = 100;
        return net;
    }

    private static long[] run(Network net) {
        new Simulator(net, 7).run(2000);
        return net.neurons().stream().mapToLong(Neuron::spikeCount).toArray();
    }

    @Test
    void captureThenApplyReproducesState() {
        Network a = build();
        a.get("Q1").thresholdOffset = 0.3;
        a.get("P1").synapses().get(0).scale = 2.5;
        NetworkSnapshot snap = Snapshots.capture(a);

        Network b = build();
        Snapshots.apply(b, snap);
        assertEquals(snap, Snapshots.capture(b));
        assertEquals(2.5, b.get("P1").synapses().get(0).scale);
    }

    @Test
    void restoredNetworkBehavesIdentically() {
        Network a = build();
        a.get("Q1").thresholdOffset = 0.3;
        a.get("P1").synapses().get(0).scale = 2.5;
        NetworkSnapshot snap = Snapshots.capture(a);
        Network b = build();
        Snapshots.apply(b, snap);

        long[] original = run(a);
        long[] restored = run(b);
        assertArrayEquals(original, restored);
        assertTrue(original[1] > 0, "Q1 should spike with scale 2.5");
    }

    @Test
    void unknownNeuronIsRejected() {
        NetworkSnapshot snap = new NetworkSnapshot(NetworkSnapshot.CURRENT_VERSION,
                java.util.List.of(new NetworkSnapshot.NeuronState("NOPE", 0)), java.util.List.of());
        assertThrows(IllegalArgumentException.class, () -> Snapshots.apply(build(), snap));
    }
}