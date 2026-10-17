package ru.mcs.neuroworm.core;

import java.util.ArrayList;
import java.util.List;

/** Снимок обучаемого состояния сети. apply() ожидает свежесобранную сеть той же топологии. */
public final class Snapshots {
    private Snapshots() { }

    public static NetworkSnapshot capture(Network net) {
        List<NetworkSnapshot.NeuronState> neurons = new ArrayList<>();
        List<NetworkSnapshot.SynapseScale> synapses = new ArrayList<>();
        for (Neuron n : net.neurons()) {
            neurons.add(new NetworkSnapshot.NeuronState(n.name, n.thresholdOffset));
            for (Synapse s : n.synapses()) {
                synapses.add(new NetworkSnapshot.SynapseScale(n.name, net.get(s.target()).name, s.scale));
            }
        }
        return new NetworkSnapshot(NetworkSnapshot.CURRENT_VERSION, neurons, synapses);
    }

    public static void apply(Network net, NetworkSnapshot snap) {
        if (snap.version() != NetworkSnapshot.CURRENT_VERSION) {
            throw new IllegalArgumentException("Unsupported snapshot version " + snap.version());
        }
        for (NetworkSnapshot.NeuronState st : snap.neurons()) {
            require(net, st.name()).thresholdOffset = st.thresholdOffset();
        }
        for (NetworkSnapshot.SynapseScale sc : snap.synapses()) {
            Neuron pre = require(net, sc.pre());
            Neuron post = require(net, sc.post());
            boolean found = false;
            for (Synapse s : pre.synapses()) {
                if (s.target() == post.index) {
                    s.scale = sc.scale();
                    found = true;
                }
            }
            if (!found) throw new IllegalArgumentException("No synapse " + sc.pre() + " -> " + sc.post());
        }
    }

    private static Neuron require(Network net, String name) {
        Neuron n = net.get(name);
        if (n == null) throw new IllegalArgumentException("Unknown neuron: " + name);
        return n;
    }
}