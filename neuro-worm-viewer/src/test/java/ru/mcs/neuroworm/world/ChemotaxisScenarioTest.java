package ru.mcs.neuroworm.world;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class ChemotaxisScenarioTest {

    private static double lateDistance(double[] off, long seed) {
        ChemotaxisScenario s = new ChemotaxisScenario(off, seed);
        double sum = 0;
        int n = 0;
        for (int i = 0; i < 2000; i++) {
            s.world.step();
            if (i >= 1500) {
                sum += s.distanceToFood();
                n++;
            }
        }
        return sum / n;
    }

    @Test
    void wormReachesFoodAcrossSeeds() {
        double[] off = ChemotaxisScenario.settle();
        double mean = IntStream.range(0, 6).parallel().mapToDouble(i -> lateDistance(off, 700_000 + i))
                .average().orElse(1e9);
        System.out.printf("SCENARIO mean late distance %.1f (start 60)%n", mean);
        assertTrue(mean < 40, "mean = " + mean);
    }
}