package ru.mcs.neuroworm.world;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.IntStream;
import ru.mcs.neuroworm.core.ConnectomeLoader;
import ru.mcs.neuroworm.core.Network;
import ru.mcs.neuroworm.core.Neuron;

/** Отключает по одному классу нейронов и измеряет, насколько ухудшается поиск еды. */
public final class AblationScan {
    static final int SEEDS = 12;

    static String classOf(String name) {
        if (name.matches(".*\\d\\d$")) return name.substring(0, name.length() - 2);
        if (name.length() > 3 && (name.endsWith("L") || name.endsWith("R"))) {
            return name.substring(0, name.length() - 1);
        }
        return name;
    }

    static double[] distances(double[] off, Set<String> lesions) {
        double[] d = new double[SEEDS];
        for (int k = 0; k < SEEDS; k++) {
            ChemotaxisScenario s = new ChemotaxisScenario(off, 800_000 + k, lesions);
            double sum = 0;
            int n = 0;
            for (int i = 0; i < 2000; i++) {
                s.world.step();
                if (i >= 1500) {
                    sum += s.distanceToFood();
                    n++;
                }
            }
            d[k] = sum / n;
        }
        return d;
    }

    static double mean(double[] v) { return Arrays.stream(v).average().orElse(0); }

    static double se(double[] v) {
        double m = mean(v);
        return Math.sqrt(Arrays.stream(v).map(x -> (x - m) * (x - m)).sum() / (v.length - 1) / v.length);
    }

    public static void main(String[] args) throws IOException {
        Logger.getLogger(ConnectomeLoader.class.getName()).setLevel(Level.SEVERE);
        double[] off = ChemotaxisScenario.settle();
        double[] intact = distances(off, Set.of());
        System.out.printf("intact: %.1f +- %.1f (%d seeds)%n", mean(intact), se(intact), SEEDS);

        Network ref = Evolution.newNetwork();
        Map<String, List<String>> classes = new TreeMap<>();
        for (Neuron n : ref.neurons()) {
            classes.computeIfAbsent(classOf(n.name), k -> new ArrayList<>()).add(n.name);
        }
        List<Map.Entry<String, List<String>>> entries = new ArrayList<>(classes.entrySet());
        double[] result = new double[entries.size()];
        IntStream.range(0, entries.size()).parallel()
                .forEach(i -> result[i] = mean(distances(off, new HashSet<>(entries.get(i).getValue()))));

        Integer[] order = IntStream.range(0, entries.size()).boxed().toArray(Integer[]::new);
        Arrays.sort(order, (a, b) -> Double.compare(result[b], result[a]));
        try (PrintWriter w = new PrintWriter("ablation.csv")) {
            w.println("class,neurons,distance,delta");
            for (int i : order) {
                var e = entries.get(i);
                w.printf(java.util.Locale.ROOT, "%s,%d,%.2f,%.2f%n", e.getKey(), e.getValue().size(),
                        result[i], result[i] - mean(intact));
            }
        }
        System.out.println("class    n   distance   delta (top 25 by damage)");
        for (int r = 0; r < Math.min(25, order.length); r++) {
            int i = order[r];
            System.out.printf("%-8s %2d  %8.1f  %+7.1f%n", entries.get(i).getKey(),
                    entries.get(i).getValue().size(), result[i], result[i] - mean(intact));
        }
    }
}