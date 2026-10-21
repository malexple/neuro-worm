package ru.mcs.neuroworm.world;

import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.IntStream;
import ru.mcs.neuroworm.core.ConnectomeLoader;

/** Замыкание голода и еды: поев, червь уходит от источника, проголодавшись, возвращается. */
public final class MealCycleCheck {
    static final int SEEDS = 10, STEPS = 20_000, LEAVE_WINDOW = 3000;
    static final double LEAVE_DISTANCE = 20;

    private record Run(int meals, int evaluated, int left) { }

    static Run run(double[] off, long seed) {
        ChemotaxisScenario s = new ChemotaxisScenario(off, seed, Set.of(), EfficiencyEvolution.EVOLVED);
        Worm w = s.worm;
        w.cfg.hungerGain = 2.0;
        w.cfg.hungerFloor = 0.1;
        w.cfg.hungerRise = 1.0 / 6000;
        w.cfg.hungerFall = 1.0 / 500;
        w.cfg.satiatedLevel = 0.05;
        w.cfg.eatThreshold = 0.5;
        w.hunger = 0.8;

        boolean wasFeeding = false;
        int trackFrom = -1, evaluated = 0, left = 0;
        double maxDist = 0;
        for (int i = 0; i < STEPS; i++) {
            s.step();
            boolean f = w.feeding();
            if (wasFeeding && !f) {
                trackFrom = i;
                maxDist = 0;
            }
            if (trackFrom >= 0) {
                maxDist = Math.max(maxDist, s.distanceToFood());
                boolean ateAgain = !wasFeeding && f;
                if (ateAgain || i - trackFrom >= LEAVE_WINDOW) {
                    evaluated++;
                    if (maxDist >= LEAVE_DISTANCE) left++;
                    trackFrom = -1;
                }
            }
            wasFeeding = f;
        }
        return new Run(w.meals(), evaluated, left);
    }

    public static void main(String[] args) {
        Logger.getLogger(ConnectomeLoader.class.getName()).setLevel(Level.SEVERE);
        System.out.println("data='" + Evolution.DATA + "'");
        double[] off = Evolution.settle();
        Run[] r = IntStream.range(0, SEEDS).parallel()
                .mapToObj(i -> run(off, 800_000 + i)).toArray(Run[]::new);
        int evaluated = 0, left = 0, repeaters = 0, meals = 0;
        for (Run x : r) {
            evaluated += x.evaluated();
            left += x.left();
            meals += x.meals();
            if (x.meals() >= 2) repeaters++;
            System.out.printf("meals %d | evaluated %d | left %d%n", x.meals(), x.evaluated(), x.left());
        }
        double leftShare = evaluated == 0 ? 0 : (double) left / evaluated;
        System.out.printf("seeds %d, mean meals %.1f, ate at least twice %d/%d, left by %.0f units: %.0f%%%n",
                SEEDS, (double) meals / SEEDS, repeaters, SEEDS, LEAVE_DISTANCE, 100 * leftShare);
        System.out.println(leftShare >= 0.9 && repeaters >= 0.9 * SEEDS ? "PASS" : "FAIL");
    }
}