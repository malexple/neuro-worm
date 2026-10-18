package ru.mcs.neuroworm.world;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.IntStream;
import ru.mcs.neuroworm.core.ConnectomeLoader;
import ru.mcs.neuroworm.core.Network;
import ru.mcs.neuroworm.core.Neuron;

/** Подгонка масштабов нескольких путей вокруг ASH под отклик на боль: AVA/AVD растут, AVB падает. */
public final class PainCircuitFit {
    record Group(String name, String pre, String post) { }

    record Metrics(double[] rest, double[] pain) {
        double d(int i) { return pain[i] - rest[i]; }
        double dBackFwd() { return (pain[4] - pain[3]) - (rest[4] - rest[3]); }
    }

    record Cand(int group, double value, double loss) { }

    static final Group[] GROUPS = {
            new Group("ASH>AIB", "^ASH[LR]$", "^AIB[LR]$"),
            new Group("ASH>AIZ", "^ASH[LR]$", "^AIZ[LR]$"),
            new Group("ASH>RIM", "^ASH[LR]$", "^RIM[LR]$"),
            new Group("ASH>AVA", "^ASH[LR]$", "^AVA[LR]$"),
            new Group("ASH>AVD", "^ASH[LR]$", "^AVD[LR]$"),
            new Group("ASH>AVB", "^ASH[LR]$", "^AVB[LR]$"),
            new Group("ASH>AVE", "^ASH[LR]$", "^AVE[LR]$"),
            new Group("AIB>AVB", "^AIB[LR]$", "^AVB[LR]$"),
            new Group("AIZ>AVB", "^AIZ[LR]$", "^AVB[LR]$"),
            new Group("RIM>AVB", "^RIM[LR]$", "^AVB[LR]$"),
            new Group("AIB>AVA", "^AIB[LR]$", "^AVA[LR]$"),
            new Group("AVA>VA/DA", "^AVA[LR]$", "^(VA|DA)\\d+$"),
            new Group("AVD>VA/DA", "^AVD[LR]$", "^(VA|DA)\\d+$"),
            new Group("AVB>VB/DB", "^AVB[LR]$", "^(VB|DB)\\d+$"),
            new Group("AVA>AVB", "^AVA[LR]$", "^AVB[LR]$")};
    static final double[] VALUES = {-2, -1, 0, 0.5, 2, 4};
    static final String[] READOUTS = {"^AVA[LR]$", "^AVD[LR]$", "^AVB[LR]$", "^(VB|DB)\\d\\d$", "^(VA|DA)\\d\\d$"};

    static double pos(double x) { return Math.max(0, x); }

    static double[] rates(double[] off, double[] g, Map<Integer, Double> ch, double hz, long seed) {
        ChemotaxisScenario s = new ChemotaxisScenario(off, seed, Set.of(), g);
        Network net = s.net;
        for (Map.Entry<Integer, Double> e : ch.entrySet()) {
            Evolution.scale(net, GROUPS[e.getKey()].pre(), GROUPS[e.getKey()].post(), e.getValue());
        }
        net.get("ASHL").stimulusHz = hz;
        net.get("ASHR").stimulusHz = hz;
        s.sim.run(500);
        long[] before = new long[net.size()];
        for (Neuron n : net.neurons()) before[n.index] = n.spikeCount();
        int ticks = 2000;
        s.sim.run(ticks);
        double[] r = new double[READOUTS.length];
        for (int i = 0; i < r.length; i++) r[i] = PainProbe.rate(net, before, READOUTS[i], ticks);
        return r;
    }

    static Metrics metrics(double[] off, double[] g, Map<Integer, Double> ch, long[] seeds, double hz) {
        double[] rest = new double[5], pain = new double[5];
        for (long sd : seeds) {
            double[] a = rates(off, g, ch, 0, sd), b = rates(off, g, ch, hz, sd);
            for (int i = 0; i < 5; i++) {
                rest[i] += a[i] / seeds.length;
                pain[i] += b[i] / seeds.length;
            }
        }
        return new Metrics(rest, pain);
    }

    static double loss(Metrics m, double[] rest0) {
        double l = pos(3 - m.d(0)) / 3 + pos(3 - m.d(1)) / 3 + pos(m.d(2) + 1) + pos(3 - m.dBackFwd()) / 3;
        for (int i = 0; i < 5; i++) {
            l += 2 * pos(Math.abs(m.rest()[i] - rest0[i]) / Math.max(rest0[i], 0.5) - 0.3);
        }
        return l;
    }

    static void print(String name, Metrics m) {
        System.out.printf("%-22s rest AVA %.1f AVD %.1f AVB %.1f fwd %.1f back %.1f | rise AVA %+.1f AVD %+.1f AVB %+.1f"
                        + " back-fwd %+.1f%n", name, m.rest()[0], m.rest()[1], m.rest()[2], m.rest()[3], m.rest()[4],
                m.d(0), m.d(1), m.d(2), m.dBackFwd());
    }

    public static void main(String[] args) {
        Logger.getLogger(ConnectomeLoader.class.getName()).setLevel(Level.SEVERE);
        double[] off = Evolution.settle();
        double[] g = PainEvolution.reference();
        long[] seeds = {1, 2};
        Metrics base = metrics(off, g, Map.of(), seeds, 500);
        double[] rest0 = base.rest();
        double current = loss(base, rest0);
        print("baseline (500 Hz)", base);
        System.out.printf("baseline loss %.2f (0 means all targets met)%n", current);

        Map<Integer, Double> chosen = new LinkedHashMap<>();
        for (int round = 1; round <= 3 && current > 0; round++) {
            List<Cand> list = IntStream.range(0, GROUPS.length * VALUES.length).parallel()
                    .filter(k -> !chosen.containsKey(k / VALUES.length))
                    .mapToObj(k -> {
                        Map<Integer, Double> ch = new LinkedHashMap<>(chosen);
                        ch.put(k / VALUES.length, VALUES[k % VALUES.length]);
                        return new Cand(k / VALUES.length, VALUES[k % VALUES.length],
                                loss(metrics(off, g, ch, seeds, 500), rest0));
                    })
                    .sorted(Comparator.comparingDouble(Cand::loss)).toList();
            System.out.println("round " + round + " best candidates:");
            for (int i = 0; i < Math.min(5, list.size()); i++) {
                System.out.printf("  %-10s scale %+.1f loss %.2f%n", GROUPS[list.get(i).group()].name(),
                        list.get(i).value(), list.get(i).loss());
            }
            Cand best = list.get(0);
            if (best.loss() >= current - 0.05) break;
            chosen.put(best.group(), best.value());
            current = best.loss();
        }

        System.out.println("CHOSEN (loss " + String.format("%.2f", current) + "):");
        for (Map.Entry<Integer, Double> e : chosen.entrySet()) {
            Group gr = GROUPS[e.getKey()];
            System.out.printf("  Evolution.scale(net, \"%s\", \"%s\", %.1f);   // %s%n", gr.pre(), gr.post(),
                    e.getValue(), gr.name());
        }
        long[] val = {11, 12, 13, 14, 15, 16};
        for (double hz : new double[] {200, 500, 1000}) {
            print("baseline @" + (int) hz, metrics(off, g, Map.of(), val, hz));
            print("fitted   @" + (int) hz, metrics(off, g, chosen, val, hz));
        }
    }
}