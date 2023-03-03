package net.polar.instaqueue.player;

import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.audience.ForwardingAudience;
import net.polar.instaqueue.InstaQueue;
import net.polar.instaqueue.types.Queue;
import net.polar.instaqueue.utils.Priority;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.UUID;

public class QueuedPlayer implements ForwardingAudience.Single {
    private static final Priority NONE_PRIORITY = new Priority("none", 0);

    private final UUID uuid;
    private final String name;
    private Queue queue;
    private Priority priority;

    public QueuedPlayer(@NotNull Player player) {
        this.uuid = player.getUniqueId();
        this.name = player.getUsername();
    }

    @Nullable
    public Player player() {
        return InstaQueue.getInstance().getServer().getPlayer(this.uuid).orElse(null);
    }

    /**
     * @return The player's priority, calculating it if required.
     */
    @NotNull
    public Priority priority() {
        if (priority == null)
            priority = calculatePriority();

        return priority;
    }

    /**
     * Gets the player's current position in their sub queue, or -1 if they are not in a queue.
     * @return -1 or the player's sub queue position
     */
    public int position() {
        if (queue == null) return -1;
        return queue.getSubQueue(this).players().indexOf(this);
    }

    public boolean isInQueue() {
        if (this.queue != null)
            if (!this.queue.hasPlayer(this))
                this.queue = null;

        return this.queue != null;
    }

    public Queue queue() {
        return this.queue;
    }

    public void queue(Queue queue) {
        this.queue = queue;
    }

    private @NotNull Priority calculatePriority() {
        Player player = player();
        if (player == null) return NONE_PRIORITY;

        for (Priority priority : InstaQueue.getInstance().getPriorities()) {
            if (player.hasPermission(priority.name().toLowerCase(Locale.ROOT))) return priority;
        }
        return NONE_PRIORITY;
    }

    public void recalculatePriority() {
        // Recalculate the priority if it's set
        if (this.priority != null) this.priority = calculatePriority();
    }

    @Override
    public @NotNull Audience audience() {
        return InstaQueue.getInstance().getServer().getPlayer(this.uuid).map(player -> (Audience) player).orElse(Audience.empty());
    }

    @NotNull
    public UUID uuid() {
        return this.uuid;
    }

    @NotNull
    public String name() {
        return this.name;
    }

}