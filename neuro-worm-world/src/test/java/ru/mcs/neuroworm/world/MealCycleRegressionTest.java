package ru.mcs.neuroworm.world;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class MealCycleRegressionTest {
    @BeforeAll
    static void useSignedData() { Evolution.DATA = "_signed"; }

    @AfterAll
    static void restore() { Evolution.DATA = ""; }

    @Test
    void wormEatsRepeatedlyAndLeavesBetweenMeals() {
        double[] off = Evolution.settle();
        ChemotaxisScenario s = new ChemotaxisScenario(off, 800_000, Set.of(), EfficiencyEvolution.EVOLVED);
        MealCycle.apply(s.worm);
        boolean wasFeeding = false;
        int mealEnd = -1;
        double maxDistAfterFirstMeal = 0;
        for (int i = 0; i < 20_000; i++) {
            s.step();
            boolean f = s.worm.feeding();
            if (wasFeeding && !f && mealEnd < 0) mealEnd = i;
            if (mealEnd >= 0 && i - mealEnd < 3000) {
                maxDistAfterFirstMeal = Math.max(maxDistAfterFirstMeal, s.distanceToFood());
            }
            wasFeeding = f;
        }
        System.out.printf("MEAL CYCLE: meals %d, max distance after first meal %.1f%n",
                s.worm.meals(), maxDistAfterFirstMeal);
        assertTrue(s.worm.meals() >= 2, "meals " + s.worm.meals());
        assertTrue(maxDistAfterFirstMeal >= 20, "distance " + maxDistAfterFirstMeal);
    }
}