package ru.mcs.neuroworm.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import ru.mcs.neuroworm.core.Network;
import ru.mcs.neuroworm.core.PlasticityManager;

class PlasticGroupEquivalenceTest {
    @Test void olfactoryGroupMatchesRegex() {
        for (String data : new String[] {"", "_signed"}) {
            check(data, Evolution.GROUP_OLFACTORY, Evolution.SENS);
        }
    }

    @Test void saltGroupMatchesRegex() {
        for (String data : new String[] {"", "_signed"}) {
            check(data, Evolution.GROUP_SALT, "^ASE[LR]$");
        }
    }

    private static void check(String data, String group, String preRegex) {
        String old = Evolution.DATA;
        Evolution.DATA = data;
        try {
            Network net = Evolution.newNetwork();
            Set<String> byRegex = pairs(new PlasticityManager(net, preRegex, Evolution.INTER));
            Set<String> byGroup = pairs(PlasticityManager.forGroup(net, group));
            assertFalse(byGroup.isEmpty());
            assertEquals(byRegex, byGroup);
        } finally {
            Evolution.DATA = old;
        }
    }

    private static Set<String> pairs(PlasticityManager pm) {
        double[][] s = pm.scales();
        String[] pre = pm.preNames(), post = pm.postNames();
        Set<String> r = new TreeSet<>();
        for (int q = 0; q < post.length; q++) {
            for (int p = 0; p < pre.length; p++) if (!Double.isNaN(s[q][p])) r.add(pre[p] + ">" + post[q]);
        }
        return r;
    }
}