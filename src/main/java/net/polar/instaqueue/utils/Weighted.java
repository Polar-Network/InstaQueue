package net.polar.instaqueue.utils;

import org.jetbrains.annotations.NotNull;

import java.util.Comparator;

public class Weighted implements Comparable<Weighted> {

    private static final Comparator<Weighted> comparator = Comparator.comparing(Weighted::getWeight, Comparator.reverseOrder());

    private final int weight;

    public Weighted(int weight) {
        this.weight = weight;
    }

    public int getWeight() {
        return weight;
    }

    @Override
    public int compareTo(@NotNull Weighted other) {
        return comparator.compare(this, other);
    }
}
