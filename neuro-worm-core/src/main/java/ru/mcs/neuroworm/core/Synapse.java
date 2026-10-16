package ru.mcs.neuroworm.core;

/** Химический синапс; знак определяет медиатор пресинаптического нейрона. */
public record Synapse(int target, double weight, int delay) { }