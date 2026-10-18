package ru.mcs.neuroworm.world;

import java.util.Arrays;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.IntStream;
import ru.mcs.neuroworm.core.ConnectomeLoader;

/** Замер побега из зоны боли: нет зоны / зона с целым ASH / зона с отключённым ASH. */
public final class PainCheck {
    static final Arena.Zone ZONE = new Arena.Zone(110, 100, 12);

    static double[] episode(double[] g, double[] off, long seed, Set<String> lesions, Arena.Zone zone) {
        ChemotaxisScenario s = new ChemotaxisScenario(off, seed, lesions, g, zone);
        boolean inside = false;
        int entries = 0, zoneSteps = 0;
        for (int i = 0; i < 3000 && !s.worm.feeding(); i++) {
            s.step();
            boolean now = s.arena.pain(s.worm.x, s.worm.y) > 0;
            if (now) zoneSteps++;
            if (now && !inside) entries++;
            inside = now;
        }
        boolean found = s.worm.feeding();
        return new double[] {found ? 1 : 0, found ? s.worm.contactStep() / 100.0 : 30, zoneSteps / 100.0, entries};
    }

    static double avg(double[][] r, int k) {
        return Arrays.stream(r).mapToDouble(a -> a[k]).average().orElse(0);
    }

    static void report(String name, double[] g, double[] off, Set<String> lesions, Arena.Zone zone) {
        int n = 20;
        double[][] r = IntStream.range(0, n).parallel()
                .mapToObj(i -> episode(g, off, 300_000 + i, lesions, zone)).toArray(double[][]::new);
        System.out.printf("%-20s found %2.0f/%d | time %.1f s | in zone %.2f s | entries %.1f%n",
                name, avg(r, 0) * n, n, avg(r, 1), avg(r, 2), avg(r, 3));
    }

    public static void main(String[] args) {
        Logger.getLogger(ConnectomeLoader.class.getName()).setLevel(Level.SEVERE);
        double[] off = Evolution.settle();
        double[] g = EfficiencyEvolution.EVOLVED;
        report("no zone", g, off, Set.of(), null);
        report("zone, ASH intact", g, off, Set.of(), ZONE);
        report("zone, ASH lesioned", g, off, Set.of("ASHL", "ASHR"), ZONE);
    }
}