package ru.mcs.neuroworm.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class Network {
    public static final int MAX_DELAY = 7;

    private final List<Neuron> neurons = new ArrayList<>();
    private final Map<String, Neuron> byName = new HashMap<>();

    public Neuron addNeuron(String name, NeuronType type, Neurotransmitter transmitter) {
        if (byName.containsKey(name)) throw new IllegalArgumentException("Duplicate neuron: " + name);
        Neuron n = new Neuron(neurons.size(), name, type, transmitter);
        neurons.add(n);
        byName.put(name, n);
        return n;
    }

    public void connect(Neuron pre, Neuron post, double weight, int delay) {
        if (weight < 0) throw new IllegalArgumentException("Weight must be >= 0; sign comes from transmitter");
        if (delay < 1 || delay > MAX_DELAY) throw new IllegalArgumentException("Delay must be in 1.." + MAX_DELAY);
        pre.out.add(new Synapse(post.index, weight, delay));
    }

    public void connect(Neuron pre, Neuron post, double weight, int delay, int sign) {
        if (weight < 0) throw new IllegalArgumentException("Weight must be >= 0; sign comes from transmitter");
        if (delay < 1 || delay > MAX_DELAY) throw new IllegalArgumentException("Delay must be in 1.." + MAX_DELAY);
        pre.out.add(new Synapse(post.index, weight, delay, sign));
    }

    public void connectGap(Neuron a, Neuron b, double conductance) {
        if (conductance <= 0) throw new IllegalArgumentException("Conductance must be > 0");
        a.gaps.add(new GapLink(b.index, conductance));
        b.gaps.add(new GapLink(a.index, conductance));
    }

    public Neuron get(int index) { return neurons.get(index); }
    public Neuron get(String name) { return byName.get(name); }
    public int size() { return neurons.size(); }
    public List<Neuron> neurons() { return Collections.unmodifiableList(neurons); }
}