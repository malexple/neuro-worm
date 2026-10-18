package ru.mcs.neuroworm.core;

import java.util.Arrays;
import java.util.regex.Pattern;

/**
 * Трёхфакторная пластичность (R-STDP) на синапсах между двумя группами нейронов.
 * Меняет только Synapse.scale. Знак награды задаёт внешний код через поле reward.
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

    private boolean learning = true;
    private final Neuron[] pre, post;
    private final Synapse[][] syn;
    private final double[] xPre, xPost;
    private final double[][] elig;

    public PlasticityManager(Network net, String preRegex, String postRegex) {
        Pattern a = Pattern.compile(preRegex), b = Pattern.compile(postRegex);
        pre = net.neurons().stream().filter(n -> a.matcher(n.name).matches()).toArray(Neuron[]::new);
        post = net.neurons().stream().filter(n -> b.matcher(n.name).matches()).toArray(Neuron[]::new);
        syn = new Synapse[post.length][pre.length];
        for (int p = 0; p < pre.length; p++) {
            for (Synapse s : pre[p].synapses()) {
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
                    s.scale = Math.max(minScale, Math.min(maxScale, s.scale + eta * reward * e));
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