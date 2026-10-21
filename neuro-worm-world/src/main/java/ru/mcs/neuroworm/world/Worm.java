package ru.mcs.neuroworm.world;

import java.util.regex.Pattern;
import ru.mcs.neuroworm.core.Network;
import ru.mcs.neuroworm.core.Neuron;

/** Тело червя: читает пулы моторных нейронов, двигается, выставляет стимулы сенсорам. */
public final class Worm {
    public static final class Config {
        public double speedGain = 0.01;   // единиц за шаг на 1 Гц (вперёд - назад)
        public double turnGain = 0.02;    // радиан за шаг на 1 Гц (дорсальный - вентральный)
        public double odorGainHz = 100;
        public double painGainHz = 200;
        public double sensorOffset = 2.0;
        public double emaAlpha = 0.2;
        public double baseSpeed = 0;
        public double awaDerivGain = 0;
        public double awcOffGain = 0;
        public double awcTonicHz = 0;
        public double derivAlpha = 0.1;
        public double aseConcGain = 0;
        public double aseOnGain = 0;
        public double aseOffGain = 0;
        public double aseTonicHz = 0;
        public double feedRadius = 0;     // 0 = питание выключено
        public double pirouetteThreshold = 0; // Гц (назад - вперёд); 0 = пируэты выключены
        public int reverseSteps = 50;
        public double reverseSpeed = 0.1;
        public double omegaAngle = 2.0;   // рад
        public int turnSteps = 30;
        public int cooldownSteps = 100;
        public double turnDeadband = 0;   // Гц
        public boolean commandTrigger = false; // триггер пируэта по (AVA,AVD) минус AVB, а не по моторным пулам
        public double triggerAlpha = 0.05;
        public boolean commandSpeed = false;  // скорость по командным интернейронам (реверс при боли)
        public double cmdRest = 2.0;          // уровень (AVA+AVD)/2 - AVB в покое, Гц
        public double cmdScale = 8.0;         // масштаб k, Гц
        public double serotoninSlowing = 0;   // замедление на единицу уровня серотонина
        public double dopamineSlowing = 0;
        public double hungerGain = 0;   // усиление запахового входа при голоде: 1 + hungerGain * hunger
        public double hungerFloor = 1;       // чувствительность к запаху при h = 0 (1 = как раньше)
        public double hungerRise = 0;        // рост голода за шаг, пока червь не ест (0 = динамика выключена)
        public double hungerFall = 0;        // падение голода за шаг во время еды
        public double satiatedLevel = 0.05;  // при этом голоде червь перестаёт есть
        public double eatThreshold = 0;      // минимальный голод, при котором червь начинает есть
    }

    private static final int RUN = 0, REVERSE = 1, TURN = 2;

    public final Config cfg = new Config();
    public double x, y, heading;
    /** NaN: чувствовать поле соли в арене; число: подать постоянный уровень CS (обучение). */
    public double csOverride = Double.NaN;
    /** NaN: боль от арены; число: подать постоянный стимул ASH в Гц (обучение). */
    public double painOverride = Double.NaN;
    /** 0 = сыт, 1 = голоден; задаётся снаружи. */
    public double hunger = 0;
    private int meals;
    public int meals() { return meals; }

    private final Arena arena;
    private final Network net;
    private final int[] fwd, back, dor, ven;
    private final Neuron awaL, awaR, awcL, awcR, aseL, aseR, ashL, ashR;
    private final long[] lastCounts;
    private double fwdHz, backHz, dorHz, venHz;
    private double prevL = Double.NaN, prevR = Double.NaN, dcL, dcR, dcBase;
    private double prevSL = Double.NaN, prevSR = Double.NaN, dcSL, dcSR;
    private boolean feeding;
    private int steps;
    private int contactStep = -1;
    private int phase = RUN, phaseLeft, cooldown, turnDir = 1;
    private double rotation, pathLength;
    private final int[] cmdBack, cmdFwd;
    private double triggerHz;
    private int pirouettes;
    private double serotonin, dopamine;

    public Worm(Arena arena, Network net, double x, double y, double heading) {
        this.arena = arena;
        this.net = net;
        this.x = x;
        this.y = y;
        this.heading = heading;
        fwd = pool(net, "^(VB|DB)\\d\\d$");
        back = pool(net, "^(VA|DA)\\d\\d$");
        dor = pool(net, "^(SMDD|RMDD)[LR]$");
        ven = pool(net, "^(SMDV|RMDV)[LR]$");
        cmdBack = optionalPool(net, "^(AVA|AVD)[LR]$");
        cmdFwd = optionalPool(net, "^AVB[LR]$");
        awaL = net.get("AWAL");
        awaR = net.get("AWAR");
        awcL = net.get("AWCL");
        awcR = net.get("AWCR");
        aseL = net.get("ASEL");
        aseR = net.get("ASER");
        ashL = net.get("ASHL");
        ashR = net.get("ASHR");
        lastCounts = new long[net.size()];
        for (Neuron n : net.neurons()) lastCounts[n.index] = n.spikeCount();
    }

    private static int[] pool(Network net, String regex) {
        Pattern p = Pattern.compile(regex);
        int[] ids = net.neurons().stream().filter(n -> p.matcher(n.name).matches()).mapToInt(n -> n.index).toArray();
        if (ids.length == 0) throw new IllegalArgumentException("Empty motor pool: " + regex);
        return ids;
    }

    private double poolHz(int[] ids, int ticks) {
        long spikes = 0;
        for (int i : ids) spikes += net.get(i).spikeCount() - lastCounts[i];
        return spikes * 1000.0 / ticks / ids.length;
    }

    public boolean feeding() { return feeding; }
    public int contactStep() { return contactStep; }
    public double totalRotation() { return rotation; }
    public double pathLength() { return pathLength; }
    public double dcMean() { return 0.5 * (dcL + dcR); }
    public double dcBaseline() { return dcBase; }

    /** Вызывается после каждых `ticks` тиков симуляции. */
    public void update(int ticks) {
        steps++;
        double f = poolHz(fwd, ticks), b = poolHz(back, ticks), d = poolHz(dor, ticks), v = poolHz(ven, ticks);
        double cb = cmdBack.length > 0 ? poolHz(cmdBack, ticks) : 0;
        double cf = cmdFwd.length > 0 ? poolHz(cmdFwd, ticks) : 0;
        for (Neuron n : net.neurons()) lastCounts[n.index] = n.spikeCount();
        fwdHz += cfg.emaAlpha * (f - fwdHz);
        backHz += cfg.emaAlpha * (b - backHz);
        dorHz += cfg.emaAlpha * (d - dorHz);
        venHz += cfg.emaAlpha * (v - venHz);
        triggerHz += cfg.triggerAlpha * (cb - cf - triggerHz);
        drive(fwdHz, backHz, dorHz, venHz);
        sense(ticks / 1000.0);
    }

    void drive(double fwdHz, double backHz, double dorHz, double venHz) {
        if (cfg.hungerRise > 0 || cfg.hungerFall > 0) updateHunger();
        if (feeding) return;
        double diff = dorHz - venHz;
        double dh = 0, speed;
        switch (phase) {
            case REVERSE -> {
                speed = -cfg.reverseSpeed;
                if (--phaseLeft <= 0) {
                    phase = TURN;
                    phaseLeft = Math.max(1, cfg.turnSteps);
                }
            }
            case TURN -> {
                speed = 0;
                dh = turnDir * cfg.omegaAngle / Math.max(1, cfg.turnSteps);
                if (--phaseLeft <= 0) {
                    phase = RUN;
                    cooldown = cfg.cooldownSteps;
                }
            }
            default -> {
                double excess = Math.max(0, Math.abs(diff) - cfg.turnDeadband) * Math.signum(diff);
                dh = cfg.turnGain * excess;
                speed = cfg.commandSpeed
                        ? cfg.baseSpeed * Math.max(-1, Math.min(1, 1 - (triggerHz - cfg.cmdRest) / cfg.cmdScale))
                        : cfg.baseSpeed + cfg.speedGain * (fwdHz - backHz);
                speed /= 1 + cfg.serotoninSlowing * serotonin + cfg.dopamineSlowing * dopamine;
                if (cfg.pirouetteThreshold > 0) {
                    if (cooldown > 0) {
                        cooldown--;
                    } else if ((cfg.commandTrigger ? triggerHz : backHz - fwdHz) > cfg.pirouetteThreshold) {
                        phase = REVERSE;
                        phaseLeft = Math.max(1, cfg.reverseSteps);
                        turnDir = diff >= 0 ? 1 : -1;
                    }
                }
            }
        }
        heading += dh;
        rotation += Math.abs(dh);
        pathLength += Math.abs(speed);
        x = arena.wrap(x + speed * Math.cos(heading));
        y = arena.wrap(y + speed * Math.sin(heading));
        if (cfg.feedRadius > 0 && arena.foodDistance(x, y) <= cfg.feedRadius && hunger >= cfg.eatThreshold) {
            feeding = true;
            meals++;
            if (contactStep < 0) contactStep = steps;
        }
    }

    void sense() { sense(0.01); }

    void sense(double dtSec) {
        double nx = -Math.sin(heading), ny = Math.cos(heading);
        double lx = x + cfg.sensorOffset * nx, ly = y + cfg.sensorOffset * ny;
        double rx = x - cfg.sensorOffset * nx, ry = y - cfg.sensorOffset * ny;
        double left = arena.odor(lx, ly), right = arena.odor(rx, ry);
        if (Double.isNaN(prevL)) { prevL = left; prevR = right; }
        dcL += cfg.derivAlpha * ((left - prevL) / dtSec - dcL);
        dcR += cfg.derivAlpha * ((right - prevR) / dtSec - dcR);
        dcBase += 0.001 * (0.5 * (dcL + dcR) - dcBase);
        prevL = left;
        prevR = right;
        double h = Math.max(0, Math.min(1, hunger));
        double k = cfg.hungerFloor + (1 + cfg.hungerGain - cfg.hungerFloor) * h;
        awaL.stimulusHz = k * (cfg.odorGainHz * left + cfg.awaDerivGain * Math.max(0, dcL));
        awaR.stimulusHz = k * (cfg.odorGainHz * right + cfg.awaDerivGain * Math.max(0, dcR));
        awcL.stimulusHz = cfg.awcTonicHz + k * cfg.awcOffGain * Math.max(0, -dcL);
        awcR.stimulusHz = cfg.awcTonicHz + k * cfg.awcOffGain * Math.max(0, -dcR);

        if (aseL != null) {
            boolean cs = !Double.isNaN(csOverride);
            double sl = cs ? csOverride : arena.salt(lx, ly);
            double sr = cs ? csOverride : arena.salt(rx, ry);
            if (cs || Double.isNaN(prevSL)) { prevSL = sl; prevSR = sr; }
            if (cs) {
                dcSL = 0;
                dcSR = 0;
            } else {
                dcSL += cfg.derivAlpha * ((sl - prevSL) / dtSec - dcSL);
                dcSR += cfg.derivAlpha * ((sr - prevSR) / dtSec - dcSR);
                prevSL = sl;
                prevSR = sr;
            }
            aseL.stimulusHz = cfg.aseTonicHz + cfg.aseConcGain * sl + cfg.aseOnGain * Math.max(0, dcSL);
            aseR.stimulusHz = cfg.aseTonicHz + cfg.aseConcGain * sr + cfg.aseOffGain * Math.max(0, -dcSR);
        }

        double pain = Double.isNaN(painOverride) ? cfg.painGainHz * arena.pain(x, y) : painOverride;
        ashL.stimulusHz = pain;
        ashR.stimulusHz = pain;
    }

    private static int[] optionalPool(Network net, String regex) {
        Pattern p = Pattern.compile(regex);
        return net.neurons().stream().filter(n -> p.matcher(n.name).matches()).mapToInt(n -> n.index).toArray();
    }

    public int pirouetteCount() { return pirouettes; }

    public void setModulators(double serotonin, double dopamine) {
        this.serotonin = serotonin;
        this.dopamine = dopamine;
    }

    private void updateHunger() {
        if (feeding) {
            hunger = Math.max(0, hunger - cfg.hungerFall);
            if (hunger <= cfg.satiatedLevel) feeding = false;
        } else {
            hunger = Math.min(1, hunger + cfg.hungerRise);
        }
    }
}