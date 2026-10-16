package ru.mcs.neuroworm.core;

import java.util.ArrayList;
import java.util.List;

public final class Neuron {
    public final int index;
    public final String name;
    public final NeuronType type;
    public final Neurotransmitter transmitter;

    public double tau = 20;
    public double vRest = 0;
    public double vReset = 0;
    public double threshold = 1.0;
    public double kick = 1.5;
    public int refractoryTicks = 2;

    public double fatigueInc = 0.25;
    public double fatigueTau = 500;

    public double baselineHz = 3;
    public double stimulusHz = 0;
    public double stressSensitivity = 0;

    public Modulator releases;
    public double releaseAmount;

    double v;
    double fatigue;
    int refractory;
    long spikeCount;
    final List<Synapse> out = new ArrayList<>();
    final List<GapLink> gaps = new ArrayList<>();

    Neuron(int index, String name, NeuronType type, Neurotransmitter transmitter) {
        this.index = index;
        this.name = name;
        this.type = type;
        this.transmitter = transmitter;
    }

    public double effectiveThreshold(double stress) {
        double s = Math.max(0, Math.min(1, stress));
        return threshold * (1 - stressSensitivity * s) + fatigue;
    }

    public double v() { return v; }
    public void setV(double v) { this.v = v; }
    public double fatigue() { return fatigue; }
    public long spikeCount() { return spikeCount; }
}