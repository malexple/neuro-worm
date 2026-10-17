package ru.mcs.neuroworm.world;

import java.util.regex.Pattern;
import ru.mcs.neuroworm.core.Network;
import ru.mcs.neuroworm.core.Neuron;

/** Тело червя: читает пулы моторных нейронов, двигается, выставляет стимулы сенсорам. */
public final class Worm {
    public static final class Config {
        public double speedGain = 0.01;   // единиц за шаг на 1 Гц (вперёд - назад)
        public double turnGain = 0.02;    // радиан за шаг на 1 Гц (дорсальный - вентральный)
        public double odorGainHz = 100;   // Гц сенсора на единицу концентрации
        public double painGainHz = 200;
        public double sensorOffset = 2.0;
        public double emaAlpha = 0.2;     // сглаживание частот пулов за шаг мира
        public double baseSpeed = 0;
        public double awaDerivGain = 0;   // Гц на единицу концентрации в секунду (рост запаха)
        public double awcOffGain = 0;     // Гц на единицу концентрации в секунду (падение запаха)
        public double awcTonicHz = 0;     // тоническая активность AWC без запаха
        public double derivAlpha = 0.1;   // сглаживание производной
        public double aseConcGain = 0;   // Гц на единицу концентрации соли
        public double aseOnGain = 0;     // ASEL: Гц на единицу/с роста
        public double aseOffGain = 0;    // ASER: Гц на единицу/с падения
        public double aseTonicHz = 0;
        public double feedRadius = 0;   // 0 = питание выключено
    }

    public final Config cfg = new Config();
    public double x, y, heading;

    private final Arena arena;
    private final Network net;
    private final int[] fwd, back, dor, ven;
    private final Neuron awaL, awaR, ashL, ashR, awcL, awcR;
    private final long[] lastCounts;
    private double fwdHz, backHz, dorHz, venHz;
    private double prevL = Double.NaN, prevR = Double.NaN, dcL, dcR;
    private double dcBase;
    public double dcMean() { return 0.5 * (dcL + dcR); }
    public double dcBaseline() { return dcBase; }
    private double prevSL = Double.NaN, prevSR = Double.NaN, dcSL, dcSR;
    /** NaN: чувствовать поле соли в арене; число: подать постоянный уровень CS (обучение). */
    public double csOverride = Double.NaN;
    private final Neuron aseL, aseR;
    private boolean feeding;
    private int steps;
    private int contactStep = -1;

    public boolean feeding() { return feeding; }
    public int contactStep() { return contactStep; }

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
        awaL = net.get("AWAL");
        awcL = net.get("AWCL");
        awcR = net.get("AWCR");
        aseL = net.get("ASEL");
        aseR = net.get("ASER");
        awaR = net.get("AWAR");
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

    /** Вызывается после каждых `ticks` тиков симуляции. */
    public void update(int ticks) {
        steps++;
        double f = poolHz(fwd, ticks), b = poolHz(back, ticks), d = poolHz(dor, ticks), v = poolHz(ven, ticks);
        for (Neuron n : net.neurons()) lastCounts[n.index] = n.spikeCount();
        fwdHz += cfg.emaAlpha * (f - fwdHz);
        backHz += cfg.emaAlpha * (b - backHz);
        dorHz += cfg.emaAlpha * (d - dorHz);
        venHz += cfg.emaAlpha * (v - venHz);
        drive(fwdHz, backHz, dorHz, venHz);
        sense(ticks / 1000.0);
    }

    void drive(double fwdHz, double backHz, double dorHz, double venHz) {
        if (feeding) return;
        heading += cfg.turnGain * (dorHz - venHz);
        double speed = cfg.baseSpeed + cfg.speedGain * (fwdHz - backHz);
        x = arena.wrap(x + speed * Math.cos(heading));
        y = arena.wrap(y + speed * Math.sin(heading));
        if (cfg.feedRadius > 0 && arena.foodDistance(x, y) <= cfg.feedRadius) {
            feeding = true;
            contactStep = steps;
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
        awaL.stimulusHz = cfg.odorGainHz * left + cfg.awaDerivGain * Math.max(0, dcL);
        awaR.stimulusHz = cfg.odorGainHz * right + cfg.awaDerivGain * Math.max(0, dcR);
        awcL.stimulusHz = cfg.awcTonicHz + cfg.awcOffGain * Math.max(0, -dcL);
        awcR.stimulusHz = cfg.awcTonicHz + cfg.awcOffGain * Math.max(0, -dcR);

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

        double pain = cfg.painGainHz * arena.pain(x, y);
        ashL.stimulusHz = pain;
        ashR.stimulusHz = pain;
    }
}