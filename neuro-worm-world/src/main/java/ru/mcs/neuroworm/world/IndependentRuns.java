package ru.mcs.neuroworm.world;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
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
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import ru.mcs.neuroworm.core.ConnectomeLoader;
import ru.mcs.neuroworm.core.Network;
import ru.mcs.neuroworm.core.Neuron;

/** Независимые эволюции хемотаксиса и сравнение ядра по абляции классов нейронов. */
public final class IndependentRuns {
    static final int RUNS = Integer.getInteger("neuroworm.runs", 5);
    static final int GENERATIONS = Integer.getInteger("neuroworm.generations", 25);
    static final int SEEDS = Integer.getInteger("neuroworm.ablationSeeds", 12);
    static final double IMPORTANT = 10;
    static final String[] GENE_NAMES = {"awaC", "awaDeriv", "awcOff", "awcTonic", "speedGain", "turnGain",
            "baseSpeed", "sens->inter", "inter->head", "pirThr", "revSteps", "revSpeed", "omega", "deadband",
            "cooldown"};
    static final Path DIR = Path.of(System.getProperty("neuroworm.genomeDir", "genomes"));
    static final boolean SEED_SCALED = Boolean.getBoolean("neuroworm.seedScaled");

    static double[] load(Path f) throws IOException {
        return Arrays.stream(Files.readString(f).trim().split(",")).mapToDouble(Double::parseDouble).toArray();
    }

    static void save(Path f, double[] g) throws IOException {
        Files.writeString(f, Arrays.stream(g).mapToObj(Double::toString).collect(Collectors.joining(",")));
    }

    static double meanCost(double[] g, double[] off, Set<String> lesions) {
        double s = 0;
        for (int k = 0; k < SEEDS; k++) s += EfficiencyEvolution.run(g, off, 800_000 + k, lesions).cost();
        return s / SEEDS;
    }

    static boolean atBound(int j, double v) {
        double span = EfficiencyEvolution.HI[j] - EfficiencyEvolution.LO[j];
        return Math.abs(v - EfficiencyEvolution.LO[j]) < 0.01 * span
                || Math.abs(v - EfficiencyEvolution.HI[j]) < 0.01 * span;
    }

    public static void main(String[] args) throws IOException {
        Logger.getLogger(ConnectomeLoader.class.getName()).setLevel(Level.SEVERE);
        Files.createDirectories(DIR);
        double[] off = Evolution.settle();

        List<String> names = new ArrayList<>();
        List<double[]> genomes = new ArrayList<>();
        for (int k = 1; k <= RUNS; k++) {
            Path f = DIR.resolve("run_" + k + ".txt");
            double[] g;
            if (Files.exists(f)) {
                g = load(f);
            } else {
                g = EfficiencyEvolution.evolve(off, k,
                        SEED_SCALED ? EfficiencyEvolution.scaleToPhysical(EfficiencyEvolution.EVOLVED) : null,
                        GENERATIONS);
                save(f, g);
            }
            names.add("run" + k);
            genomes.add(g);
        }
        if (Boolean.parseBoolean(System.getProperty("neuroworm.includeLegacy", "true"))) {
            names.add("EVOLVED");
            genomes.add(EfficiencyEvolution.EVOLVED);
        }

        List<Integer> ok = new ArrayList<>();
        for (int i = 0; i < genomes.size(); i++) {
            double[] g = genomes.get(i);
            long found = EfficiencyEvolution.foundCount(g, off, 500_000, 20);
            EfficiencyEvolution.Result r = EfficiencyEvolution.mean(g, off, 500_000, 20);
            System.out.printf("%-8s found %2d/20 | time %.1f s | rotation %.0f rad | cost %.1f%s%n", names.get(i),
                    found, r.time(), r.rotation(), r.cost(), found >= 18 ? "" : "  <- excluded");
            if (found >= 18) ok.add(i);
        }

        System.out.println("\ngene values by run (" + names + "), at bound among successful runs");
        for (int j = 0; j < EfficiencyEvolution.GENES; j++) {
            StringBuilder sb = new StringBuilder();
            int bound = 0;
            for (int i = 0; i < genomes.size(); i++) sb.append(String.format(" %9.3g", genomes.get(i)[j]));
            for (int i : ok) if (atBound(j, genomes.get(i)[j])) bound++;
            System.out.printf("%-12s%s | at bound %d/%d%n", GENE_NAMES[j], sb, bound, ok.size());
        }

        if (Boolean.getBoolean("neuroworm.skipAblation")) return;
        Network ref = Evolution.newNetwork();
        Map<String, List<String>> classes = new TreeMap<>();
        for (Neuron n : ref.neurons()) {
            classes.computeIfAbsent(AblationScan.classOf(n.name), c -> new ArrayList<>()).add(n.name);
        }
        List<String> classNames = new ArrayList<>(classes.keySet());
        int nc = classNames.size();
        double[][] damage = new double[genomes.size()][nc];
        for (int i : ok) {
            double[] g = genomes.get(i);
            double intact = meanCost(g, off, Set.of());
            int gi = i;
            IntStream.range(0, nc).parallel().forEach(c ->
                    damage[gi][c] = meanCost(g, off, new HashSet<>(classes.get(classNames.get(c)))) - intact);
            System.out.printf("%s: ablation done (intact cost %.1f)%n", names.get(i), intact);
        }

        int[] count = new int[nc];
        double[] mean = new double[nc];
        for (int c = 0; c < nc; c++) {
            for (int i : ok) {
                if (damage[i][c] >= IMPORTANT) count[c]++;
                mean[c] += damage[i][c] / ok.size();
            }
        }
        Integer[] order = IntStream.range(0, nc).boxed().toArray(Integer[]::new);
        Arrays.sort(order, (a, b) -> count[a] != count[b] ? Integer.compare(count[b], count[a])
                : Double.compare(mean[b], mean[a]));

        try (PrintWriter w = new PrintWriter("independent_runs.csv")) {
            w.println("class,neurons," + String.join(",", names) + ",important_in,mean_damage");
            for (int c : order) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < genomes.size(); i++) sb.append(String.format(Locale.ROOT, ",%.2f", damage[i][c]));
                w.printf(Locale.ROOT, "%s,%d%s,%d,%.2f%n", classNames.get(c), classes.get(classNames.get(c)).size(),
                        sb, count[c], mean[c]);
            }
        }

        int need = (int) Math.ceil(0.8 * ok.size());
        System.out.printf("%nclass  n | damage per run (%s) | important in (damage >= %.0f)%n", names, IMPORTANT);
        for (int r = 0; r < Math.min(25, nc); r++) {
            int c = order[r];
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < genomes.size(); i++) sb.append(String.format(" %6.1f", damage[i][c]));
            System.out.printf("%-7s %2d |%s | %d/%d%n", classNames.get(c), classes.get(classNames.get(c)).size(), sb,
                    count[c], ok.size());
        }
        StringBuilder core = new StringBuilder();
        for (int c : order) if (count[c] >= need) core.append(core.length() > 0 ? ", " : "").append(classNames.get(c));
        System.out.println("CORE (important in at least " + need + " of " + ok.size() + " successful genomes): " + core);
    }
}