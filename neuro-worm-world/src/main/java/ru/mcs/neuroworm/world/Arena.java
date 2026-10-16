package ru.mcs.neuroworm.world;

import java.util.ArrayList;
import java.util.List;

/** Тороидальный мир: источники запаха (еда) и зоны боли. */
public final class Arena {
    public record Source(double x, double y, double amplitude, double lambda) { }
    public record Zone(double x, double y, double radius) { }

    public final double size;
    public final List<Source> food = new ArrayList<>();
    public final List<Zone> painZones = new ArrayList<>();
    public final List<Source> salt = new ArrayList<>();

    public Arena(double size) { this.size = size; }

    public double wrap(double v) {
        double r = v % size;
        return r < 0 ? r + size : r;
    }

    private double delta(double a, double b) {
        double d = Math.abs(a - b) % size;
        return Math.min(d, size - d);
    }

    public double distance(double x1, double y1, double x2, double y2) {
        return Math.hypot(delta(x1, x2), delta(y1, y2));
    }

    /** Концентрация запаха: сумма A * exp(-d / lambda). */
    public double odor(double x, double y) {
        double c = 0;
        for (Source s : food) c += s.amplitude() * Math.exp(-distance(x, y, s.x(), s.y()) / s.lambda());
        return c;
    }

    /** 1 внутри любой зоны боли, иначе 0. */
    public double pain(double x, double y) {
        for (Zone z : painZones) if (distance(x, y, z.x(), z.y()) <= z.radius()) return 1;
        return 0;
    }

    public double salt(double x, double y) {
        double c = 0;
        for (Source s : salt) c += s.amplitude() * Math.exp(-distance(x, y, s.x(), s.y()) / s.lambda());
        return c;
    }
}