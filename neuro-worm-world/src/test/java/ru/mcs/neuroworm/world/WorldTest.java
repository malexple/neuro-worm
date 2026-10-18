package ru.mcs.neuroworm.world;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import ru.mcs.neuroworm.core.Network;
import ru.mcs.neuroworm.core.NeuronType;
import ru.mcs.neuroworm.core.Neurotransmitter;

class WorldTest {

    private static Network miniNet() {
        Network net = new Network();
        for (String n : new String[] {"VB01", "VA01", "SMDDL", "SMDVL", "AWAL", "AWAR", "ASHL", "ASHR", "AWCL", "AWCR"}) {
            net.addNeuron(n, NeuronType.INTER, Neurotransmitter.GLU);
        }
        return net;
    }

    @Test
    void odorDecaysWithDistanceAndWrapsAroundTorus() {
        Arena a = new Arena(200);
        a.food.add(new Arena.Source(10, 10, 1.0, 50));
        assertEquals(1.0, a.odor(10, 10), 1e-12);
        assertTrue(a.odor(30, 10) < a.odor(20, 10));
        assertEquals(a.odor(12, 10), a.odor(8, 10), 1e-12);
        assertEquals(2.0, a.distance(1, 0, 199, 0), 1e-12);
    }

    @Test
    void painZoneIsBinary() {
        Arena a = new Arena(200);
        a.painZones.add(new Arena.Zone(100, 100, 10));
        assertEquals(1, a.pain(105, 100));
        assertEquals(0, a.pain(120, 100));
    }

    @Test
    void forwardPoolMovesWormAndWorldWraps() {
        Arena a = new Arena(200);
        Worm w = new Worm(a, miniNet(), 199.99, 50, 0);
        w.drive(5, 0, 10, 4);
        assertEquals(0.12, w.heading, 1e-12);
        assertTrue(w.x >= 0 && w.x < 1, "x = " + w.x);
        assertTrue(w.y > 50);
    }

    @Test
    void backwardPoolReversesMotion() {
        Arena a = new Arena(200);
        Worm w = new Worm(a, miniNet(), 100, 100, 0);
        w.drive(0, 5, 0, 0);
        assertTrue(w.x < 100);
    }

    @Test
    void closerSensorGetsStrongerStimulus() {
        Arena a = new Arena(200);
        a.food.add(new Arena.Source(110, 105, 1.0, 40));
        Network net = miniNet();
        Worm w = new Worm(a, net, 100, 100, 0);
        w.sense();
        assertTrue(net.get("AWAL").stimulusHz > net.get("AWAR").stimulusHz);
    }

    @Test
    void wormStopsAtFood() {
        Arena a = new Arena(200);
        a.food.add(new Arena.Source(100.05, 100, 1.0, 40));
        Worm w = new Worm(a, miniNet(), 100, 100, 0);
        w.cfg.feedRadius = 3;
        w.drive(5, 0, 0, 0);
        assertTrue(w.feeding());
        double x1 = w.x;
        w.drive(5, 0, 0, 0);
        assertEquals(x1, w.x, 0.0);
    }

    @Test
    void feedingIsOffByDefault() {
        Arena a = new Arena(200);
        a.food.add(new Arena.Source(100.05, 100, 1.0, 40));
        Worm w = new Worm(a, miniNet(), 100, 100, 0);
        w.drive(5, 0, 0, 0);
        double x1 = w.x;
        w.drive(5, 0, 0, 0);
        assertTrue(w.x > x1);
        assertTrue(!w.feeding());
    }

    @Test
    void pirouetteReversesThenTurns() {
        Arena a = new Arena(200);
        Worm w = new Worm(a, miniNet(), 100, 100, 0);
        w.cfg.pirouetteThreshold = 5;
        w.cfg.reverseSteps = 3;
        w.cfg.turnSteps = 2;
        w.cfg.omegaAngle = 1.0;
        w.cfg.cooldownSteps = 0;
        w.cfg.baseSpeed = 0.2;
        w.cfg.reverseSpeed = 0.1;
        w.cfg.speedGain = 0;
        w.cfg.turnGain = 0;
        w.drive(0, 10, 2, 0);          // пробег, запуск пируэта
        double x1 = w.x;
        w.drive(0, 10, 2, 0);          // реверс 1
        assertTrue(w.x < x1);
        w.drive(0, 10, 2, 0);          // реверс 2
        w.drive(0, 10, 2, 0);          // реверс 3
        w.drive(0, 10, 2, 0);          // поворот 1
        w.drive(0, 10, 2, 0);          // поворот 2
        assertEquals(1.0, w.heading, 1e-9);
    }

    @Test
    void deadbandSuppressesSmallTurn() {
        Arena a = new Arena(200);
        Worm w = new Worm(a, miniNet(), 100, 100, 0);
        w.cfg.turnGain = 0.1;
        w.cfg.turnDeadband = 5;
        w.drive(5, 0, 3, 0);
        assertEquals(0.0, w.heading, 1e-12);
        w.drive(5, 0, 8, 0);
        assertEquals(0.3, w.heading, 1e-12);
    }
}