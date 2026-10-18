package ru.mcs.neuroworm.world;

import java.util.logging.Level;
import java.util.logging.Logger;
import ru.mcs.neuroworm.core.ConnectomeLoader;
import ru.mcs.neuroworm.core.PlasticityManager;

/** Какие связи ASE -> интернейроны изменились после аверсивного обучения (старт 10,0). */
public final class AversiveWeights {
    public static void main(String[] args) {
        Logger.getLogger(ConnectomeLoader.class.getName()).setLevel(Level.SEVERE);
        double[] off = Evolution.settle();
        PlasticityManager names = new PlasticityManager(Evolution.newNetwork(), ConditioningEvolution.ASE, Evolution.INTER);
        String[] pre = names.preNames(), post = names.postNames();
        for (AversiveConditioning.Mode mode : new AversiveConditioning.Mode[] {
                AversiveConditioning.Mode.PAIRED, AversiveConditioning.Mode.UNPAIRED,
                AversiveConditioning.Mode.US_ONLY}) {
            AversiveConditioning.Setup su = new AversiveConditioning.Setup(mode.name(), true,
                    AversiveConditioning.ASE_SENSITIVITY, AversiveConditioning.STRESS_ETA_GAIN, 1, mode);
            double[][] sc = null;
            for (int c = 0; c < 6; c++) sc = AversiveConditioning.episode(su, off, 1_000_000 + c, sc, true).scales();
            System.out.println(mode + ": changed synapses (start 10.0)");
            for (int q = 0; q < post.length; q++) {
                for (int p = 0; p < pre.length; p++) {
                    if (!Double.isNaN(sc[q][p]) && Math.abs(sc[q][p] - 10.0) > 0.05) {
                        System.out.printf("  %s -> %s  %.2f%n", pre[p], post[q], sc[q][p]);
                    }
                }
            }
        }
    }
}