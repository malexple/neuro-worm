package ru.mcs.neuroworm.core;

/** Электрическая связь; хранится у обоих нейронов. */
public record GapLink(int other, double conductance) { }