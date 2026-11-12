package com.g4vrk.hologramAddon.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

public final class HologramResyncListener implements Listener {

    private final Consumer<Player> resync;

    public HologramResyncListener(
            @NotNull Consumer<Player> resync
    ) {

        this.resync = resync;

    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {

        this.resync.accept(event.getPlayer());

    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(@NotNull PlayerJoinEvent event) {

        this.resync.accept(event.getPlayer());

    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerRespawn(@NotNull PlayerRespawnEvent event) {

        this.resync.accept(event.getPlayer());

    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerChangedWorld(@NotNull PlayerChangedWorldEvent event) {

        this.resync.accept(event.getPlayer());

    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerTeleport(PlayerTeleportEvent event) {

        this.resync.accept(event.getPlayer());

    }


}
