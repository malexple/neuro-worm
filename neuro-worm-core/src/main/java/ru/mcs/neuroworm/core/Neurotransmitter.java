package ru.mcs.neuroworm.core;

public enum Neurotransmitter {
    GLU(1), ACH(1), GABA(-1), NONE(0);

    public final int sign;

    Neurotransmitter(int sign) { this.sign = sign; }
}