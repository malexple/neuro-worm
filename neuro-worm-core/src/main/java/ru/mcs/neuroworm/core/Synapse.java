package ru.mcs.neuroworm.core;

/** Химический синапс; знак определяет медиатор пресинаптического нейрона. */
public final class Synapse {
    private final int target;
    private final double baseWeight;
    private final int delay;
    /** Множитель веса: ручка для эволюции и пластичности. */
    public double scale = 1.0;
    private final int sign;

    public Synapse(int target, double baseWeight, int delay) { this(target, baseWeight, delay, 0); }

    public Synapse(int target, double baseWeight, int delay, int sign) {
        this.target = target;
        this.baseWeight = baseWeight;
        this.delay = delay;
        this.sign = sign;
    }

    public int target() { return target; }
    public int delay() { return delay; }
    public double baseWeight() { return baseWeight; }
    public double weight() { return baseWeight * scale; }
    /** +1 / -1 перекрывает знак по медиатору; 0 значит «по медиатору». */
    public int sign() { return sign; }
}