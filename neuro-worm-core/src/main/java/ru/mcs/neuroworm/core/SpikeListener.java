package ru.mcs.neuroworm.core;

@FunctionalInterface
public interface SpikeListener {
    void onSpike(int tick, int neuronIndex);
}