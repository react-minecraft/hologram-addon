package com.g4vrk.hologramAddon.listener;

import com.g4vrk.hologramAddon.manager.HologramViewerManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Registers and unregisters hologram viewers on join and quit.
 */
@ApiStatus.Internal
public final class ConnectionListener implements Listener {

    private final HologramViewerManager hologramViewerManager;

    public ConnectionListener(
        final @NotNull HologramViewerManager hologramViewerManager
    ) {

        this.hologramViewerManager = hologramViewerManager;

    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerJoin(
        final @NotNull PlayerJoinEvent event
    ) {

        final Player player = event.getPlayer();
        this.hologramViewerManager.add(player.getUniqueId());

    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerQuit(
        final @NotNull PlayerQuitEvent event
    ) {

        final Player player = event.getPlayer();
        final UUID uniqueId = player.getUniqueId();
        this.hologramViewerManager.remove(uniqueId);

    }

}
