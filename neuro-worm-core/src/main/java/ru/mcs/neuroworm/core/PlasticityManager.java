package ru.mcs.neuroworm.core;

import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * Трёхфакторная пластичность (R-STDP) на синапсах между двумя группами нейронов.
 * Меняет только Synapse.scale. Знак награды задаёт внешний код через поле reward.
 * Набор обучаемых связей выбирается либо по группе из колонки Is_Plastic (forGroup),
 * либо по regex имён нейронов (устаревший способ).
 */
public final class PlasticityManager {
    public double eta = 1e-3;
    public double tauTrace = 20;
    public double tauElig = 500;
    public double aMinus = 1;
    public double minScale = 0.05;
    public double maxScale = 10;
    /** Знаковая награда, обычно в [-1, 1]. */
    public double reward;
    /** Уровень стресса (0..1), задаётся внешним кодом; усиливает скорость обучения. */
    public double stress;
    public double stressGain = 0;

    private boolean learning = true;
    private final String group;
    private final Neuron[] pre, post;
    private final Synapse[][] syn;
    private final double[] xPre, xPost;
    private final double[][] elig;

    /** Устаревший способ: выбор по regex. Предпочтительно forGroup. */
    public PlasticityManager(Network net, String preRegex, String postRegex) {
        this(net, select(net, preRegex), select(net, postRegex), null);
    }

    /** Обучаемые связи берутся из колонки Is_Plastic коннектома (метка группы). */
    public static PlasticityManager forGroup(Network net, String group) {
        Set<Integer> pre = new TreeSet<>();
        Set<Integer> post = new TreeSet<>();
        for (Neuron n : net.neurons()) {
            for (Synapse s : n.synapses()) {
                if (s.inPlasticGroup(group)) {
                    pre.add(n.index);
                    post.add(s.target());
                }
            }
        }
        if (pre.isEmpty()) {
            throw new IllegalStateException("No synapses with Is_Plastic group '" + group + "'");
        }
        return new PlasticityManager(net, byIndex(net, pre), byIndex(net, post), group);
    }

    private static Neuron[] select(Network net, String regex) {
        Pattern p = Pattern.compile(regex);
        return net.neurons().stream().filter(n -> p.matcher(n.name).matches()).toArray(Neuron[]::new);
    }

    private static Neuron[] byIndex(Network net, Set<Integer> indices) {
        return indices.stream().map(net::get).toArray(Neuron[]::new);
    }

    private PlasticityManager(Network net, Neuron[] pre, Neuron[] post, String group) {
        this.pre = pre;
        this.post = post;
        this.group = group;
        syn = new Synapse[post.length][pre.length];
        for (int p = 0; p < pre.length; p++) {
            for (Synapse s : pre[p].synapses()) {
                if (group != null && !s.inPlasticGroup(group)) continue;
                for (int q = 0; q < post.length; q++) {
                    if (s.target() == post[q].index && syn[q][p] == null) syn[q][p] = s;
                }
            }
        }
        xPre = new double[pre.length];
        xPost = new double[post.length];
        elig = new double[post.length][pre.length];
    }

    public void setLearning(boolean on) { this.learning = on; }

    /** Вызывать в конце каждого тика (Simulator.setTickListener). */
    public void afterTick() {
        double etaEff = eta * (1 + stressGain * Math.max(0, Math.min(1, stress)));
        for (int p = 0; p < pre.length; p++) xPre[p] += -xPre[p] / tauTrace + (pre[p].spikedLastTick() ? 1 : 0);
        for (int q = 0; q < post.length; q++) xPost[q] += -xPost[q] / tauTrace + (post[q].spikedLastTick() ? 1 : 0);
        for (int q = 0; q < post.length; q++) {
            for (int p = 0; p < pre.length; p++) {
                Synapse s = syn[q][p];
                if (s == null) continue;
                double e = elig[q][p];
                e += (post[q].spikedLastTick() ? xPre[p] : 0) - aMinus * (pre[p].spikedLastTick() ? xPost[q] : 0);
                e -= e / tauElig;
                elig[q][p] = e;
                if (learning && reward != 0) {
                    s.scale = Math.max(minScale, Math.min(maxScale, s.scale + etaEff * reward * e));
                }
            }
        }
    }

    public double[][] scales() {
        double[][] r = new double[post.length][pre.length];
        for (int q = 0; q < post.length; q++) {
            for (int p = 0; p < pre.length; p++) r[q][p] = syn[q][p] == null ? Double.NaN : syn[q][p].scale;
        }
        return r;
    }

    public void applyScales(double[][] s) {
        for (int q = 0; q < post.length; q++) {
            for (int p = 0; p < pre.length; p++) if (syn[q][p] != null) syn[q][p].scale = s[q][p];
        }
    }

    public String[] preNames() { return Arrays.stream(pre).map(n -> n.name).toArray(String[]::new); }

    public String[] postNames() { return Arrays.stream(post).map(n -> n.name).toArray(String[]::new); }
}