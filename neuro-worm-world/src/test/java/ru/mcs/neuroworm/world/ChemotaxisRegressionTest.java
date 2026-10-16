package ru.mcs.neuroworm.world;

import static org.junit.jupiter.api.Assertions.*;

import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class ChemotaxisRegressionTest {

    /** Лучший геном запуска 17.10.2026, gen 24 (округлён до 3 знаков). */
    private static final double[] GENOME = {390.283, 945.166, 1663.948, 3.553, 0.011, 0.033, 0.263, 6.488, 1.03};

    private static double mean(double[] off, boolean blind, long first, int n,
                               double sx, double sy, double fx, double fy) {
        return IntStream.range(0, n).parallel()
                .mapToDouble(i -> Evolution.trial(GENOME, off, first + i, blind, sx, sy, fx, fy))
                .average().orElse(0);
    }

    @Test
    void wormFindsFoodInTrainingScene() {
        double[] off = Evolution.settle();
        double odor = mean(off, false, 500_000, 10, 80, 100, 140, 100);
        double blind = mean(off, true, 500_000, 10, 80, 100, 140, 100);
        System.out.printf("CHEMO train scene: odor %.1f blind %.1f%n", odor, blind);
        assertTrue(odor < blind - 30, "odor " + odor + " blind " + blind);
    }

    @Test
    void generalizationToOtherScene() {
        double[] off = Evolution.settle();
        double odor = mean(off, false, 600_000, 10, 30, 40, 90, 100);
        double blind = mean(off, true, 600_000, 10, 30, 40, 90, 100);
        System.out.printf("CHEMO new scene (start distance ~85): odor %.1f blind %.1f%n", odor, blind);
    }
}