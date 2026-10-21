package ru.mcs.neuroworm.core;
import java.util.Set;

/** Химический синапс; знак определяет медиатор пресинаптического нейрона. */
public final class Synapse {
    private final int target;
    private final double baseWeight;
    private final int delay;
    /** Множитель веса: ручка для эволюции и пластичности. */
    public double scale = 1.0;
    private final int sign;
    private final Set<String> plasticGroups;

    public Synapse(int target, double baseWeight, int delay) { this(target, baseWeight, delay, 0); }

    public Synapse(int target, double baseWeight, int delay, int sign) {
        this(target, baseWeight, delay, sign, Set.of());
    }

    public Synapse(int target, double baseWeight, int delay, int sign, Set<String> plasticGroups) {
        this.target = target;
        this.baseWeight = baseWeight;
        this.delay = delay;
        this.sign = sign;
        this.plasticGroups = Set.copyOf(plasticGroups);
    }

    public int target() { return target; }
    public int delay() { return delay; }
    public double baseWeight() { return baseWeight; }
    public double weight() { return baseWeight * scale; }
    /** +1 / -1 перекрывает знак по медиатору; 0 значит «по медиатору». */
    public int sign() { return sign; }
    public Set<String> plasticGroups() { return plasticGroups; }
    public boolean inPlasticGroup(String group) { return plasticGroups.contains(group); }
}