package ru.mcs.neuroworm.core;

/** Химический синапс; знак определяет медиатор пресинаптического нейрона. */
public final class Synapse {
    private final int target;
    private final double baseWeight;
    private final int delay;
    /** Множитель веса: ручка для эволюции и пластичности. */
    public double scale = 1.0;

    public Synapse(int target, double baseWeight, int delay) {
        this.target = target;
        this.baseWeight = baseWeight;
        this.delay = delay;
    }

    public int target() { return target; }
    public int delay() { return delay; }
    public double baseWeight() { return baseWeight; }
    public double weight() { return baseWeight * scale; }
}