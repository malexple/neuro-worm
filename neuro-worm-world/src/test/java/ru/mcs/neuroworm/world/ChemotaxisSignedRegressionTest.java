package ru.mcs.neuroworm.world;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ChemotaxisSignedRegressionTest {
    private static final double[] GENOME = PlasticityEvolution.BASE_SIGNED;

    @BeforeAll
    static void useSignedData() { Evolution.DATA = "_signed"; }

    @AfterAll
    static void restore() { Evolution.DATA = ""; }

    private static double mean(double[] off, boolean blind, long first, int n,
                               double sx, double sy, double fx, double fy) {
        return IntStream.range(0, n).parallel()
                .mapToDouble(i -> Evolution.trial(GENOME, off, first + i, blind, sx, sy, fx, fy))
                .average().orElse(0);
    }

    @Test
    void wormFindsFoodWithSignedConnectome() {
        double[] off = Evolution.settle();
        double odor = mean(off, false, 900_000, 10, 80, 100, 140, 100);
        double blind = mean(off, true, 900_000, 10, 80, 100, 140, 100);
        System.out.printf("SIGNED train scene: odor %.1f blind %.1f%n", odor, blind);
        assertTrue(odor < blind - 40, "odor " + odor + " blind " + blind);
    }

    @Test
    void generalizesToOtherScene() {
        double[] off = Evolution.settle();
        double odor = mean(off, false, 950_000, 10, 30, 40, 90, 100);
        double blind = mean(off, true, 950_000, 10, 30, 40, 90, 100);
        System.out.printf("SIGNED new scene: odor %.1f blind %.1f%n", odor, blind);
    }
}