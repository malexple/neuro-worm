package ru.mcs.neuroworm.core;

import java.util.List;
import java.util.SplittableRandom;

/** Фиксированный шаг 1 мс, одиночный поток, детерминизм по seed. */
public final class Simulator {
    /** Условие устойчивости явного Эйлера для gap junction при C = 1, dt = 1. */
    public static final double MAX_GAP_SUM = 0.5;
    private static final int RING = Network.MAX_DELAY + 1;
    private static final double MODULATOR_DECAY = 0.99;
    private static final double DT_SECONDS = 0.001;

    private final Network net;
    private final SplittableRandom rng;
    private final double[][] pending;
    private final double[] vPrev;
    private final double[] modulators = new double[Modulator.values().length];
    private SpikeListener listener = (t, i) -> { };
    private int tick;
    private Runnable tickListener = () -> { };
    public void setTickListener(Runnable r) { this.tickListener = r; }

    public Simulator(Network net, long seed) {
        this.net = net;
        this.rng = new SplittableRandom(seed);
        this.pending = new double[RING][net.size()];
        this.vPrev = new double[net.size()];
        for (Neuron n : net.neurons()) {
            double sum = n.gaps.stream().mapToDouble(GapLink::conductance).sum();
            if (sum > MAX_GAP_SUM) {
                throw new IllegalArgumentException("Gap conductance sum " + sum + " > " + MAX_GAP_SUM
                        + " for neuron " + n.name + " (numerical instability)");
            }
        }
    }

    public void setSpikeListener(SpikeListener l) { this.listener = l; }
    public int tick() { return tick; }
    public double modulator(Modulator m) { return modulators[m.ordinal()]; }
    public void setModulator(Modulator m, double value) { modulators[m.ordinal()] = value; }

    public void run(int ticks) {
        for (int i = 0; i < ticks; i++) step();
    }

    public void step() {
        List<Neuron> neurons = net.neurons();
        double[] in = pending[tick % RING];
        double stress = Math.min(1, modulators[Modulator.OCTOPAMINE.ordinal()]);

        for (Neuron n : neurons) vPrev[n.index] = n.v;

        for (Neuron n : neurons) {
            double gapCurrent = 0;
            for (GapLink g : n.gaps) gapCurrent += g.conductance() * (vPrev[g.other()] - vPrev[n.index]);

            n.fatigue -= n.fatigue / n.fatigueTau;
            double v = n.v + (n.vRest - n.v) / n.tau + in[n.index] + gapCurrent;
            in[n.index] = 0;

            double rate = (n.baselineHz + n.stimulusHz) * DT_SECONDS;
            if (rng.nextDouble() < rate) v += n.kick;

            boolean spiked = false;

            if (n.refractory > 0) {
                n.refractory--;
                v = n.vReset;
            } else if (v >= n.effectiveThreshold(stress)) {
                spiked = true;
                v = n.vReset;
                n.refractory = n.refractoryTicks;
                n.fatigue += n.fatigueInc;
                n.spikeCount++;
                int sign = n.transmitter.sign;
                for (Synapse s : n.out) pending[(tick + s.delay()) % RING][s.target()] += sign * s.weight();
                if (n.releases != null) modulators[n.releases.ordinal()] += n.releaseAmount;
                listener.onSpike(tick, n.index);
            }
            n.v = v;
            n.spiked = spiked;
            if (n.homeostasisEta > 0) {
                n.rateHz += ((spiked ? 1000.0 : 0.0) - n.rateHz) / n.homeostasisRateTau;
                double o = n.thresholdOffset + n.homeostasisEta * (n.rateHz - n.targetHz);
                n.thresholdOffset = Math.max(n.minOffset, Math.min(n.maxOffset, o));
            }
        }

        for (int k = 0; k < modulators.length; k++) modulators[k] *= MODULATOR_DECAY;
        tickListener.run();
        tick++;
    }
}