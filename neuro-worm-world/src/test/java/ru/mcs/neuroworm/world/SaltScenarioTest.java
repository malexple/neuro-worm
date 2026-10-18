package ru.mcs.neuroworm.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class SaltScenarioTest {
    @BeforeAll
    static void useSigned() { Evolution.DATA = "_signed"; }

    @AfterAll
    static void restore() { Evolution.DATA = ""; }

    @Test
    void trainingWeakensAseSynapses() {
        double[] off = SaltScenario.settle();
        SaltScenario s = SaltScenario.create(off, 1);
        assertEquals(10.0, s.meanAseScale(), 1e-9);
        s.train(3, 100);
        System.out.printf("SALT mean ASE scale after training: %.2f%n", s.meanAseScale());
        assertTrue(s.meanAseScale() < 9.5, "scale " + s.meanAseScale());
    }
}