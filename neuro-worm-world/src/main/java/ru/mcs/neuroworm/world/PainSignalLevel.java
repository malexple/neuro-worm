package ru.mcs.neuroworm.world;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.IntStream;
import ru.mcs.neuroworm.core.ConnectomeLoader;

/** Уровень сигналов триггера в покое и при 500 Гц на ASH у геномов физического масштаба. */
public final class PainSignalLevel {
    public static void main(String[] args) throws IOException {
        Logger.getLogger(ConnectomeLoader.class.getName()).setLevel(Level.SEVERE);
        double[] off = Evolution.settle();
        System.out.println("genome  signal            | rest mean  rest max | pain max (mean over seeds)");
        for (int k = 1; k <= 3; k++) {
            Path f = Path.of("genomes_scaled_full", "run_" + k + ".txt");
            if (!Files.exists(f)) continue;
            double[] g = IndependentRuns.load(f);
            double[][][] runs = IntStream.range(0, 10).parallel()
                    .mapToObj(i -> PainRoc.run(g, off, 900_000 + i, 500)).toArray(double[][][]::new);
            for (int s : new int[] {3, 1}) {
                double restMean = 0, restMax = Double.NEGATIVE_INFINITY, painMax = 0;
                for (double[][] r : runs) {
                    double sum = 0;
                    for (int t = 100; t < 300; t++) sum += r[s][t];
                    restMean += sum / 200 / runs.length;
                    restMax = Math.max(restMax, PainRoc.max(r[s], 100, 300));
                    painMax += PainRoc.max(r[s], 300, 350) / runs.length;
                }
                System.out.printf("run%d    %-17s | %8.2f  %8.2f | %8.2f%n", k, PainRoc.NAMES[s], restMean, restMax, painMax);
            }
        }
    }
}