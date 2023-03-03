package net.polar.instaqueue.types;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.polar.instaqueue.InstaQueue;
import net.polar.instaqueue.player.QueuedPlayer;
import net.polar.instaqueue.utils.Ratio;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.Vector;
import java.util.concurrent.TimeUnit;

public class Queue {

    private final List<SubQueue> subQueues;
    private final SubQueue regularQueue;
    private final Ratio<SubQueue> subQueueRatio;
    private final Cache<UUID, Integer> rememberedPlayers = CacheBuilder.newBuilder().expireAfterWrite(15, TimeUnit.MINUTES).build();
    private final RegisteredServer server;
    private final String formattedName;
    private final String name;

    private int maxPlayers;
    private Instant lastSendTime = Instant.EPOCH;

    private boolean firstIter = true;

    public Queue(RegisteredServer server) {
        this.server = server;
        this.name = server.getServerInfo().getName();
        this.formattedName = server.getServerInfo().getName().substring(0, 1).toUpperCase() + server.getServerInfo().getName().substring(1);
        refreshMaxPlayers();
        this.subQueues = InstaQueue.getInstance().newSubQueues();
        this.subQueueRatio = new Ratio<>(subQueues);
        this.regularQueue = getLastElement(subQueues);
    }

    public void sendNext() {
        if (!canSend()) return;
        if (firstIter) {
            firstIter = false;
            InstaQueue.getInstance().buildTask(this::sendNext).delay(InstaQueue.getInstance().getTimeBetweenUpdates()).schedule();
            return;
        }
        // Gets the queue to send the next player from.
        SubQueue queue = getNextSubQueue(false);
        QueuedPlayer toSend = queue.removePlayer(0);
        toSend.queue(null);
        rememberPosition(toSend, 0);
        Player player = toSend.player();

        // The player is null or the player's connection is no longer active, return
        if (player == null || !player.isActive())
            return;

        // Make sure the server the player is being sent to isn't the one they're currently on
        if (player.getCurrentServer().map(server -> server.getServerInfo().getName()).orElse("unknown").equalsIgnoreCase(this.name))
            return;

        player.sendMessage(Component.text("You are being sent to " + formattedName + "...", NamedTextColor.GREEN));

        player.createConnectionRequest(server).connect().thenAccept(result -> {
            if (result.isSuccessful()) {
                player.sendMessage(Component.text("You have been sent to " + formattedName + ".", NamedTextColor.GREEN));
                sendProgressMessages(queue);
            } else {
                player.sendMessage(Component.text("Unable to connect you to " + formattedName + ".", NamedTextColor.RED));
                Component reason = switch (result.getStatus()) {
                    case CONNECTION_IN_PROGRESS -> Component.text("You are already being connected to this server!", NamedTextColor.RED);
                    case SERVER_DISCONNECTED -> result.getReasonComponent().isPresent() ? result.getReasonComponent().get() : Component.text("The target server has refused your connection.", NamedTextColor.RED);
                    case ALREADY_CONNECTED -> Component.text("You are already connected to this server!", NamedTextColor.RED);
                    case CONNECTION_CANCELLED -> Component.text("Your connection has been cancelled unexpectedly.", NamedTextColor.RED);
                    default -> Component.text("", NamedTextColor.RED);
                };

                player.sendMessage(Component.text("Reason: ", reason.colorIfAbsent(NamedTextColor.RED).color()).append(reason));
            }
        }).exceptionally(e -> {
            e.printStackTrace();
            player.sendMessage(Component.text("Unable to connect you to " + formattedName + ".", NamedTextColor.RED));
            player.sendMessage(Component.text("Attempting to re-queue you...", NamedTextColor.RED));
            toSend.queue(this);
            queue.addPlayer(toSend, 0);
            return null;
        });

        lastSendTime = Instant.now();
    }

    public boolean canSend() {
        return lastSendTime.plusSeconds(InstaQueue.getInstance().getTimeBetweenUpdates().toSeconds()).isBefore(Instant.now())
                && server.getPlayersConnected().size() < maxPlayers
                && hasPlayers()
                && !getNextSubQueue(true).players().isEmpty();
    }

    public void sendProgressMessages(SubQueue queue) {
        if (queue.lastPositionMessageTime().plusSeconds(InstaQueue.getInstance().getTimeBetweenMessages().toSeconds()).isAfter(Instant.now())) return;

        queue.lastPositionMessageTime(Instant.now());

        for (QueuedPlayer player : queue.players()) {
            rememberPosition(player);
            String pos = InstaQueue.getInstance().getConfig().getString("position-message")
                            .replace("%queue%", formattedName)
                            .replace("%position%", String.valueOf(player.position() + 1));
            player.sendMessage(MiniMessage.miniMessage().deserialize(pos));
        }
    }

    public void rememberPosition(QueuedPlayer player) {
        rememberPosition(player, player.position());
    }

    public void rememberPosition(QueuedPlayer player, int index) {
        rememberedPlayers.put(player.uuid(), index);
    }

    public void enqueue(QueuedPlayer player) {
        if (player.queue() != null) {
            if (player.queue().equals(this)) {
                player.sendMessage(Component.text("You are already queued for this server.", NamedTextColor.RED));
                return;
            } else {
                player.sendMessage(Component.text("You have been removed from the queue for " + player.queue().getServerFormatted() + ".", NamedTextColor.RED));
                player.queue().remove(player);
            }
        }

        SubQueue subQueue = getSubQueue(player);
        player.queue(this);

        int index = insertionIndex(player, subQueue);
        if (index < 0 || index >= subQueue.players().size()) subQueue.addPlayer(player);
        else subQueue.addPlayer(player, index);

        String joining = InstaQueue.getInstance().getConfig().getString("queue-message")
                        .replace("%queue%", formattedName)
                        .replace("%size%", String.valueOf(subQueue.players().size()))
                        .replace("%position%", String.valueOf(player.position() + 1));
        player.sendMessage(MiniMessage.miniMessage().deserialize(joining));
    }

    public int insertionIndex(QueuedPlayer player, SubQueue subQueue) {
        if (subQueue.players().isEmpty())
            return 0;

        int rememberedPosition = subQueue.players().size();
        if (rememberedPlayers.getIfPresent(player.uuid()) != null)
            rememberedPosition = Math.min(rememberedPlayers.getIfPresent(player.uuid()), subQueue.players().size());

        int weight = player.priority().getWeight();
        if (weight == 0)
            return rememberedPosition;

        int slot = 0;
        for (int i = 0; i < subQueue.players().size(); i++) {
            if (weight <= subQueue.getPlayer(i).priority().getWeight())
                slot = i+1;
        }

        int priorityIndex = Math.min(slot, subQueue.players().size());

        return Math.min(rememberedPosition, priorityIndex);
    }

    public void remove(QueuedPlayer player) {
        rememberPosition(player);
        player.queue(null);

        for (SubQueue subQueue : this.subQueues)
            subQueue.removePlayer(player);
    }

    public boolean hasPlayer(QueuedPlayer player) {
        for (SubQueue subQueue : this.subQueues)
            if (subQueue.hasPlayer(player))
                return true;

        return false;
    }

    public boolean hasPlayers() {
        for (SubQueue subQueue : this.subQueues)
            if (!subQueue.players().isEmpty())
                return true;

        return false;
    }

    public void refreshMaxPlayers() {
        server.ping().thenAccept(ping -> {
            if (ping.getPlayers().isPresent())
                this.maxPlayers = ping.getPlayers().get().getMax();
        });
    }
    @Override
    public boolean equals(Object other) {
        if (this == other)
            return true;

        if (!(other instanceof Queue queue))
            return false;

        return this.server.getServerInfo().getName().equalsIgnoreCase(queue.server.getServerInfo().getName());
    }

    /**
     * @param dry If dry is set to true, the sends won't be reset.
     * @return The queue to send the next player from.
     */
    public SubQueue getNextSubQueue(boolean dry) {
        return this.subQueueRatio.next(dry, (subQueue) -> !subQueue.players().isEmpty(), regularQueue);
    }

    public SubQueue getSubQueue(QueuedPlayer player) {
        for (SubQueue subQueue : this.subQueues)
            if (player.priority().getWeight() >= subQueue.getWeight())
                return subQueue;

        // Fallback to the regular queue if none is found.
        return regularQueue;
    }

    public RegisteredServer getServer() {
        return server;
    }

    public String getServerFormatted() {
        return formattedName;
    }


    public Vector<QueuedPlayer> allPlayers() {
        Vector<QueuedPlayer> allPlayers = new Vector<>();
        for (SubQueue subQueue : subQueues)
            allPlayers.addAll(subQueue.players());

        return allPlayers;
    }

    public SubQueue getRegularQueue() {
        return this.regularQueue;
    }

    private SubQueue getLastElement(Collection<SubQueue> collection) {
        SubQueue current = null;

        for (SubQueue subQueue : collection)
            current = subQueue;

        return current;
    }

}
