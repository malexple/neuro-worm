package ru.mcs.neuroworm.core;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * neuron_types.csv: Neuron,Neurotransmitter[,Class]
 * connectome.csv:   Source,Target,Type,Weight   (Type: S = chemical, G = gap junction)
 */
public final class ConnectomeLoader {

    public record Report(int neurons, int chemical, int gap, int signOverrides, List<String> inferred) { }

    public record LoadResult(Network network, Report report) { }

    private record Row(int line, String[] f) { }

    private static final System.Logger LOG = System.getLogger(ConnectomeLoader.class.getName());

    private static final java.util.concurrent.atomic.AtomicBoolean WARNED =
            new java.util.concurrent.atomic.AtomicBoolean();

    private ConnectomeLoader() { }

    public static LoadResult load(Reader types, Reader connectome, double chemicalK, double gapK)
            throws IOException {
        Map<String, Neurotransmitter> transmitters = new HashMap<>();
        Map<String, NeuronType> classes = new HashMap<>();
        for (Row r : rows(types, "Neuron", 2)) {
            String name = r.f[0];
            if (!r.f[1].isEmpty()) transmitters.put(name, parseTransmitter(r));
            if (r.f.length > 2 && !r.f[2].isEmpty()) classes.put(name, parseClass(r));
        }

        List<Row> edges = rows(connectome, "Source", 4);
        TreeSet<String> names = new TreeSet<>(transmitters.keySet());
        for (Row e : edges) {
            names.add(e.f[0]);
            names.add(e.f[1]);
        }

        Network net = new Network();
        List<String> inferred = new ArrayList<>();
        for (String name : names) {
            Neurotransmitter t = transmitters.get(name);
            if (t == null) {
                t = Neurotransmitter.GLU;
                inferred.add(name);
            }
            net.addNeuron(name, classes.getOrDefault(name, NeuronType.INTER), t);
        }
        if (!inferred.isEmpty() && WARNED.compareAndSet(false, true)) {
            LOG.log(System.Logger.Level.WARNING,
                    "Neurotransmitter not defined for {0} neurons, assumed GLU (see Report.inferred)",
                    inferred.size());
        }

        int chemical = 0;
        int gap = 0;
        Set<String> gapSeen = new HashSet<>();
        int overrides = 0;
        for (Row e : edges) {
            double w = parseWeight(e);
            Neuron a = net.get(e.f[0]);
            Neuron b = net.get(e.f[1]);
            switch (e.f[2].toUpperCase(Locale.ROOT)) {
                case "S" -> {
                    int sign = 0;
                    if (e.f.length > 4 && !e.f[4].isEmpty()) {
                        sign = parseSign(e);
                        if (sign != 0) overrides++;
                    }
                    net.connect(a, b, chemicalK * w, 1, sign);
                    chemical++;
                }
                case "G" -> {
                    String key = a.index < b.index ? a.index + "|" + b.index : b.index + "|" + a.index;
                    if (gapSeen.add(key)) {
                        net.connectGap(a, b, gapK * w);
                        gap++;
                    }
                }
                default -> throw new IllegalArgumentException("line " + e.line + ": unknown type '" + e.f[2] + "'");
            }
        }
        return new LoadResult(net, new Report(net.size(), chemical, gap, overrides, List.copyOf(inferred)));
    }

    private static int parseSign(Row r) {
        try {
            int s = Integer.parseInt(r.f[4]);
            if (s < -1 || s > 1) throw new NumberFormatException();
            return s;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("line " + r.line + ": sign must be -1, 0 or 1: '" + r.f[4] + "'");
        }
    }

    private static List<Row> rows(Reader reader, String headerFirstColumn, int minColumns) throws IOException {
        List<Row> out = new ArrayList<>();
        BufferedReader br = new BufferedReader(reader);
        String s;
        int line = 0;
        while ((s = br.readLine()) != null) {
            line++;
            s = s.strip();
            if (s.isEmpty() || s.startsWith("#")) continue;
            String[] f = s.split("\\s*,\\s*", -1);
            if (f[0].equalsIgnoreCase(headerFirstColumn)) continue;
            if (f.length < minColumns) {
                throw new IllegalArgumentException("line " + line + ": expected >= " + minColumns + " columns: " + s);
            }
            out.add(new Row(line, f));
        }
        return out;
    }

    private static Neurotransmitter parseTransmitter(Row r) {
        try {
            return Neurotransmitter.valueOf(r.f[1].toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("line " + r.line + ": unknown neurotransmitter '" + r.f[1] + "'");
        }
    }

    private static NeuronType parseClass(Row r) {
        try {
            return NeuronType.valueOf(r.f[2].toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("line " + r.line + ": unknown class '" + r.f[2] + "'");
        }
    }

    private static double parseWeight(Row r) {
        try {
            double w = Double.parseDouble(r.f[3]);
            if (w <= 0) throw new NumberFormatException();
            return w;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("line " + r.line + ": weight must be a positive number: '" + r.f[3] + "'");
        }
    }
}