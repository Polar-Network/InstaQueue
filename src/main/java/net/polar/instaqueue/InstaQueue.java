package net.polar.instaqueue;


import com.google.inject.Inject;
import com.moandjiezana.toml.Toml;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.scheduler.Scheduler;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.polar.instaqueue.listeners.MiscListener;
import net.polar.instaqueue.player.QueuedPlayer;
import net.polar.instaqueue.types.Queue;
import net.polar.instaqueue.types.SubQueue;
import net.polar.instaqueue.utils.Priority;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;

@Plugin(
        id = "instaqueue",
        name = "InstaQueue",
        version = "1.0.0",
        description = "A velocity plugin that allows for instant gamemode queues and supports permission based queue priorities"
)
public class InstaQueue {

    private static InstaQueue instance;
    public static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private final ProxyServer server;
    private final Logger logger;
    private final Toml config;

    private final String connectTo;
    private final String toConnect;
    private final Duration timeBetweenUpdates;
    private final Duration timeBetweenMessages;

    private final List<Priority> priorities = new ArrayList<>();
    private final List<SubQueue> subQueues = new ArrayList<>();
    private final Map<UUID, QueuedPlayer> queuedPlayers = new HashMap<>();
    private Queue queue;

    @Inject
    public InstaQueue(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        this.server = server;
        this.logger = logger;
        File configFile = new File(dataDirectory.toFile(), "config.toml");
        if (!configFile.exists()) {
            try {
                boolean a = configFile.getParentFile().mkdirs();
                Files.copy(Objects.requireNonNull(InstaQueue.class.getClassLoader().getResourceAsStream("config.toml")), configFile.toPath());
            }
            catch (IOException e) {
                e.printStackTrace();
            }
        }
        this.config = new Toml().read(configFile);
        this.connectTo = config.getString("join-server");
        this.toConnect = config.getString("first-queue");
        Toml intervals  = config.getTable("intervals");
        this.timeBetweenUpdates = Duration.ofSeconds(intervals.getLong("update"));
        this.timeBetweenMessages = Duration.ofSeconds(intervals.getLong("message"));

        Toml priorities = config.getTable("priorities");
        priorities.entrySet().forEach((entry) -> {
            int weight = Integer.parseInt(entry.getKey());
            String name = entry.getValue().toString();
            this.priorities.add(new Priority(name, weight));
            logger.info("Registered priority: " + name + " with weight: " + weight);
        });
        this.priorities.forEach(priority -> {
            this.subQueues.add(new SubQueue(priority.name(), priority.getWeight()));
            logger.info("Registered subqueue: " + priority.name() + " with weight: " + priority.getWeight());
        });
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        instance = this;
        queue = new Queue(server.getServer(toConnect).get());
        registerListeners(MiscListener.INSTANCE);
        buildTask(() -> queue.sendNext()).repeat(Duration.ofMillis(100)).schedule();
        buildTask(() -> queue.refreshMaxPlayers()).repeat(Duration.ofSeconds(10)).schedule();
    }

    public Scheduler.TaskBuilder buildTask(Runnable runnable) {
        return server.getScheduler().buildTask(this, runnable);
    }

    public List<SubQueue> newSubQueues() {
        List<SubQueue> newSubQueues = new ArrayList<>();
        for (SubQueue subQueue : this.subQueues)
            newSubQueues.add(new SubQueue(subQueue.name(), subQueue.getWeight()));
        Collections.sort(newSubQueues);
        return newSubQueues;
    }

    public static InstaQueue getInstance() {return instance;}
    public ProxyServer getServer() {return server;}
    public Logger getLogger() {return logger;}
    public Toml getConfig() {return config;}
    public String getConnectTo() {return connectTo;}

    public List<Priority> getPriorities() {
        return priorities;
    }


    public Duration getTimeBetweenUpdates() {
        return timeBetweenUpdates;
    }

    public Duration getTimeBetweenMessages() {
        return timeBetweenMessages;
    }

    public Queue getQueue() {return queue;}

    public QueuedPlayer queued(Player player) {
        queuedPlayers.putIfAbsent(player.getUniqueId(), new QueuedPlayer(player));
        return queuedPlayers.get(player.getUniqueId());
    }

    public void removeQueued(Player player) {
        queuedPlayers.remove(player.getUniqueId());
    }

    private void registerListeners(Object... listeners) {
        Arrays.stream(listeners).forEach(listener -> server.getEventManager().register(this, listener));
    }

}
