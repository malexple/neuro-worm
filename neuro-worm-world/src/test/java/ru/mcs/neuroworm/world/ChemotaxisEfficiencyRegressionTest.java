package ru.mcs.neuroworm.world;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ChemotaxisEfficiencyRegressionTest {
    @BeforeAll
    static void useSignedData() { Evolution.DATA = "_signed"; }

    @AfterAll
    static void restore() { Evolution.DATA = ""; }

    @Test
    void evolvedGenomeFindsFoodWithoutSeizures() {
        double[] off = Evolution.settle();
        double[] g = EfficiencyEvolution.EVOLVED;
        long found = EfficiencyEvolution.foundCount(g, off, 200_000, 20);
        var r = EfficiencyEvolution.mean(g, off, 200_000, 20);
        double peak = SeizureCheck.peakRate(g, off, 200_000);
        System.out.printf("EFFICIENCY: found %d/20 time %.1f s rotation %.0f rad peak %.1f Hz%n",
                found, r.time(), r.rotation(), peak);
        assertTrue(found >= 18, "found " + found);
        assertTrue(r.rotation() < 220, "rotation " + r.rotation());
        assertTrue(r.time() < 12, "time " + r.time());
        assertTrue(peak < SeizureCheck.LIMIT_HZ, "peak " + peak);
    }
}