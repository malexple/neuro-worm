package ru.mcs.neuroworm.world;

/** Параметры цикла голод - еда - насыщение (секунды модели при шаге мира 10 мс). */
public final class MealCycle {
    public static final double HUNGER_GAIN = 2.0, HUNGER_FLOOR = 0.1, SATIATED = 0.05,
            EAT_THRESHOLD = 0.5, START_HUNGER = 0.8;
    public static final double RISE_PER_STEP = 1.0 / 6000, FALL_PER_STEP = 1.0 / 500;

    private MealCycle() { }

    public static void apply(Worm w) {
        w.cfg.hungerGain = HUNGER_GAIN;
        w.cfg.hungerFloor = HUNGER_FLOOR;
        w.cfg.hungerRise = RISE_PER_STEP;
        w.cfg.hungerFall = FALL_PER_STEP;
        w.cfg.satiatedLevel = SATIATED;
        w.cfg.eatThreshold = EAT_THRESHOLD;
        w.hunger = START_HUNGER;
    }
}