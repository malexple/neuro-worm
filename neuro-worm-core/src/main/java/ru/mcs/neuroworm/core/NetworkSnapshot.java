package ru.mcs.neuroworm.core;

import java.util.List;

public record NetworkSnapshot(int version, List<NeuronState> neurons, List<SynapseScale> synapses) {
    public static final int CURRENT_VERSION = 1;

    public record NeuronState(String name, double thresholdOffset) { }

    public record SynapseScale(String pre, String post, double scale) { }
}