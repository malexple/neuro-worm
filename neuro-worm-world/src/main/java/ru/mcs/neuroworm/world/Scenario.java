package ru.mcs.neuroworm.world;

import ru.mcs.neuroworm.core.Network;
import ru.mcs.neuroworm.core.Simulator;

/** Сцена, которую умеет показывать viewer. */
public interface Scenario {
    Arena arena();

    Network net();

    Simulator sim();

    Worm worm();

    void step();

    double distanceToTarget();

    double targetX();

    double targetY();

    /** Концентрация, которую рисуем фоном (запах или соль). */
    double fieldAt(double x, double y);

    default String info() { return ""; }
}