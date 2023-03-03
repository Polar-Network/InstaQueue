package net.polar.instaqueue.types;

import net.polar.instaqueue.player.QueuedPlayer;
import net.polar.instaqueue.utils.Weighted;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Vector;

public class SubQueue extends Weighted {

    private final String name;
    private final Vector<QueuedPlayer> players = new Vector<>(0);
    private Instant lastPositionMessageTime = Instant.EPOCH;
    private static final int maxSends = 1;

    public SubQueue(String name, int weight) {
        super(weight);
        this.name = name;
    }

    public Vector<QueuedPlayer> players() {
        return this.players;
    }

    public boolean hasPlayer(@NotNull QueuedPlayer player) {
        return players.contains(player);
    }

    public void addPlayer(@NotNull QueuedPlayer player) {
        players.add(player);
    }

    public int getMaxSends() {
        return maxSends;
    }

    public void addPlayer(@NotNull QueuedPlayer player, int index) {
        players.add(index, player);
    }

    public void removePlayer(@NotNull QueuedPlayer player) {
        players.remove(player);
    }

    public QueuedPlayer removePlayer(int index) {
        return players.remove(index);
    }

    @NotNull
    public QueuedPlayer getPlayer(int index) throws IndexOutOfBoundsException {
        return players.get(index);
    }

    public String name() {
        return name;
    }

    public int maxSends() {
        return maxSends;
    }

    public void lastPositionMessageTime(@NotNull Instant instant) {
        this.lastPositionMessageTime = instant;
    }

    public Instant lastPositionMessageTime() {
        return this.lastPositionMessageTime;
    }

    @Override
    public String toString() {
        return "SubQueue{" +
                "name='" + name + '\'' +
                ", players=" + players +
                ", lastPositionMessageTime=" + lastPositionMessageTime +
                ", maxSends=" + maxSends +
                ", weight=" + getWeight() +
                '}';
    }

}
