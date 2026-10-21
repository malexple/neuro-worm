package ru.mcs.neuroworm.world;

import java.util.regex.Pattern;
import ru.mcs.neuroworm.core.Modulator;
import ru.mcs.neuroworm.core.Network;
import ru.mcs.neuroworm.core.Neuron;

/** Назначает нейронам без медиатора роль источников модуляторов; по умолчанию нигде не вызывается. */
public final class ModulatorSources {
    public static final double DEFAULT_AMOUNT = 0.05;

    private ModulatorSources() { }

    public static void attach(Network net, double amount) {
        assign(net, "^NSM[LR]$", Modulator.SEROTONIN, amount);
        assign(net, "^(CEPD|CEPV|ADE|PDE)[LR]$", Modulator.DOPAMINE, amount);
        assign(net, "^RIC[LR]$", Modulator.OCTOPAMINE, amount);
    }

    private static void assign(Network net, String regex, Modulator m, double amount) {
        Pattern p = Pattern.compile(regex);
        for (Neuron n : net.neurons()) {
            if (p.matcher(n.name).matches()) {
                n.releases = m;
                n.releaseAmount = amount;
            }
        }
    }
}