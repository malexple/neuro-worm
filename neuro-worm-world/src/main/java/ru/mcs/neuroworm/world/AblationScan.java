package ru.mcs.neuroworm.world;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.IntStream;
import ru.mcs.neuroworm.core.ConnectomeLoader;
import ru.mcs.neuroworm.core.Network;
import ru.mcs.neuroworm.core.Neuron;

/** Отключает по одному классу нейронов; метрики: расстояние до еды и скорость вращения. */
public final class AblationScan {
    static final int SEEDS = 12;

    record Result(double dist, double turn, double[] perSeed) { }

    static String classOf(String name) {
        if (name.matches(".*\\d\\d$")) return name.substring(0, name.length() - 2);
        if (name.length() > 3 && (name.endsWith("L") || name.endsWith("R"))) {
            return name.substring(0, name.length() - 1);
        }
        return name;
    }

    static Result run(double[] off, Set<String> lesions) {
        double[] d = new double[SEEDS];
        double turn = 0;
        for (int k = 0; k < SEEDS; k++) {
            ChemotaxisScenario s = new ChemotaxisScenario(off, 800_000 + k, lesions);
            double h0 = s.worm.heading, sum = 0;
            int n = 0;
            for (int i = 0; i < 2000; i++) {
                s.world.step();
                if (i >= 1500) {
                    sum += s.distanceToFood();
                    n++;
                }
            }
            d[k] = sum / n;
            turn += Math.abs((s.worm.heading - h0) / 20.0);
        }
        return new Result(Arrays.stream(d).average().orElse(0), turn / SEEDS, d);
    }

    static double se(double[] v) {
        double m = Arrays.stream(v).average().orElse(0);
        return Math.sqrt(Arrays.stream(v).map(x -> (x - m) * (x - m)).sum() / (v.length - 1) / v.length);
    }

    public static void main(String[] args) throws IOException {
        Logger.getLogger(ConnectomeLoader.class.getName()).setLevel(Level.SEVERE);
        System.out.println("data='" + Evolution.DATA + "' genome="
                + java.util.Arrays.toString(PlasticityEvolution.baseGenome()));
        double[] off = ChemotaxisScenario.settle();
        Result intact = run(off, Set.of());
        System.out.printf("intact: %.1f +- %.1f | turn rate %.2f rad/s (%d seeds)%n",
                intact.dist(), se(intact.perSeed()), intact.turn(), SEEDS);
        if (intact.dist() > 25) {
            throw new IllegalStateException("Baseline is broken (intact " + intact.dist()
                    + "): check the genome used by ChemotaxisScenario");
        }

        Network ref = Evolution.newNetwork();
        Map<String, List<String>> classes = new TreeMap<>();
        for (Neuron n : ref.neurons()) {
            classes.computeIfAbsent(classOf(n.name), k -> new ArrayList<>()).add(n.name);
        }
        List<Map.Entry<String, List<String>>> entries = new ArrayList<>(classes.entrySet());
        Result[] res = new Result[entries.size()];
        IntStream.range(0, entries.size()).parallel()
                .forEach(i -> res[i] = run(off, new HashSet<>(entries.get(i).getValue())));

        Integer[] order = IntStream.range(0, entries.size()).boxed().toArray(Integer[]::new);
        Arrays.sort(order, (a, b) -> Double.compare(res[b].dist(), res[a].dist()));
        try (PrintWriter w = new PrintWriter("ablation.csv")) {
            w.println("class,neurons,distance,delta,turn");
            for (int i : order) {
                w.printf(Locale.ROOT, "%s,%d,%.2f,%.2f,%.3f%n", entries.get(i).getKey(),
                        entries.get(i).getValue().size(), res[i].dist(), res[i].dist() - intact.dist(),
                        res[i].turn());
            }
        }
        System.out.println("class    n   distance   delta   turn rad/s (top 20 by damage)");
        for (int r = 0; r < Math.min(20, order.length); r++) {
            int i = order[r];
            System.out.printf("%-8s %2d  %8.1f  %+7.1f  %6.2f%n", entries.get(i).getKey(),
                    entries.get(i).getValue().size(), res[i].dist(), res[i].dist() - intact.dist(),
                    res[i].turn());
        }
    }
}