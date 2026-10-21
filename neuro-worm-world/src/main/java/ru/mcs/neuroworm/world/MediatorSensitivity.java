package ru.mcs.neuroworm.world;

import java.util.logging.Level;
import java.util.logging.Logger;
import ru.mcs.neuroworm.core.ConnectomeLoader;

/** Чувствительность эволюционировавшего хемотаксиса к знаку нейронов без медиатора, по классам. */
public final class MediatorSensitivity {
    static final String[] CLASSES = {"ADE", "ASI", "AVH", "AVK", "AWA", "BDU", "CEPD", "CEPV", "I4", "I6",
            "NSM", "PDE", "PLM", "PVT", "PVW", "RIC", "RID", "RMG"};

    public static void main(String[] args) {
        Logger.getLogger(ConnectomeLoader.class.getName()).setLevel(Level.SEVERE);
        double[] g = EfficiencyEvolution.EVOLVED;
        System.out.printf("%-12s %7s %8s %9s %8s%n", "zeroed", "found", "time,s", "rot,rad", "peak,Hz");
        report("none", "", g);
        for (String c : CLASSES) report(c, "^" + c + "[LR]?$", g);
        report("all 32", "^(" + String.join("|", CLASSES) + ")[LR]?$", g);
    }

    private static void report(String label, String regex, double[] g) {
        System.setProperty("neuroworm.noneFor", regex);
        double[] off = Evolution.settle();
        long found = EfficiencyEvolution.foundCount(g, off, 200_000, 20);
        var r = EfficiencyEvolution.mean(g, off, 200_000, 20);
        double peak = SeizureCheck.peakRate(g, off, 200_000);
        System.out.printf("%-12s %4d/20 %8.1f %9.0f %8.1f%n", label, found, r.time(), r.rotation(), peak);
    }
}