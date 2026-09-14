package com.g4vrk.hologramAddon.listener;

import com.g4vrk.hologramAddon.manager.HologramViewerManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class ConnectionListener implements Listener {

    private final HologramViewerManager hologramViewerManager;

    public ConnectionListener(
            @NotNull HologramViewerManager hologramViewerManager
    ) {
        this.hologramViewerManager = hologramViewerManager;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerJoin(PlayerJoinEvent event) {

        final Player player = event.getPlayer();

        if (player.hasPermission("react.hologram.view")) {
            hologramViewerManager.add(player.getUniqueId());
        }

    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerQuit(PlayerQuitEvent event) {

        final Player player = event.getPlayer();
        final UUID uniqueId = player.getUniqueId();

        hologramViewerManager.remove(uniqueId);

    }


}
