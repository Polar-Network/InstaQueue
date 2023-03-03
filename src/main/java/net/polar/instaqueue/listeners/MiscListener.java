package net.polar.instaqueue.listeners;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.proxy.Player;
import net.polar.instaqueue.InstaQueue;
import net.polar.instaqueue.player.QueuedPlayer;

public final class MiscListener {

    public static MiscListener INSTANCE = new MiscListener();
    MiscListener() {}

    @Subscribe
    public void onPlayerJoin(ServerConnectedEvent event) {
        final Player player = event.getPlayer();
        final QueuedPlayer qp = InstaQueue.getInstance().queued(player);
        if (qp.isInQueue() &&
                qp.queue().getServer().getServerInfo().getName().equalsIgnoreCase(event.getServer().getServerInfo().getName()))
        {
            qp.queue().remove(qp);
        }
    }

    @Subscribe
    public void onPostConnect(ServerPostConnectEvent event) {
        final Player player = event.getPlayer();
        if (!player.getCurrentServer().get().getServerInfo().getName().equalsIgnoreCase(InstaQueue.getInstance().getConnectTo())) return;
        InstaQueue.getInstance().getQueue().enqueue(InstaQueue.getInstance().queued(player));
    }

    @Subscribe
    public void onPlayerLeave(DisconnectEvent event) {
        QueuedPlayer player = InstaQueue.getInstance().queued(event.getPlayer());
        if (player != null) {
            if (player.isInQueue()) player.queue().remove(player);
        }
        InstaQueue.getInstance().removeQueued(event.getPlayer());
    }

}
