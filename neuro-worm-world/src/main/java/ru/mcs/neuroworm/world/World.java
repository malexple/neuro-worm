package ru.mcs.neuroworm.world;

import ru.mcs.neuroworm.core.Simulator;

/** Мультитемповая связка: ядро 1 мс, мир 10 мс. */
public final class World {
    public static final int TICKS_PER_STEP = 10;

    public final Arena arena;
    public final Worm worm;
    private final Simulator sim;

    public World(Arena arena, Worm worm, Simulator sim) {
        this.arena = arena;
        this.worm = worm;
        this.sim = sim;
    }

    public void step() {
        sim.run(TICKS_PER_STEP);
        worm.update(TICKS_PER_STEP);
    }
}