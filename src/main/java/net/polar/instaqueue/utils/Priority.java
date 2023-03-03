package net.polar.instaqueue.utils;

import net.kyori.adventure.text.Component;

import static net.polar.instaqueue.InstaQueue.MINI_MESSAGE;

public class Priority extends Weighted {

    private final String name;

    public Priority(String name, int weight) {
        super(weight);
        this.name = name;
    }

    public String name() {
        return this.name;
    }
    @Override
    public String toString() {
        return "Priority{" +
                "name='" + name + '\'' +
                ", weight=" + getWeight() +
                '}';
    }

}
