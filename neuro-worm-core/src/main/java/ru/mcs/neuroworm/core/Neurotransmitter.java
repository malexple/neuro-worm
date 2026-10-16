package ru.mcs.neuroworm.core;

public enum Neurotransmitter {
    GLU(1), ACH(1), GABA(-1);

    public final int sign;

    Neurotransmitter(int sign) { this.sign = sign; }
}