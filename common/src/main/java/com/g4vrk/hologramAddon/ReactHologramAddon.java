package com.g4vrk.hologramAddon;

import com.g4vrk.hologramAddon.command.HologramCommand;
import com.g4vrk.hologramAddon.hologram.TargetedHologram;
import com.g4vrk.hologramAddon.hologram.config.impl.LegacyHologramConfiguration;
import com.g4vrk.hologramAddon.hologram.config.impl.ModernHologramConfiguration;
import com.g4vrk.hologramAddon.hologram.factory.HologramFactory;
import com.g4vrk.hologramAddon.hologram.text.HologramTextGenerator;
import com.g4vrk.hologramAddon.hologram.text.impl.ReactHologramTextConfiguration;
import com.g4vrk.hologramAddon.hologram.text.impl.ReactHologramTextGenerator;
import com.g4vrk.hologramAddon.listener.ConnectionListener;
import com.g4vrk.hologramAddon.listener.HistoryEntryAddedListener;
import com.g4vrk.hologramAddon.listener.HologramResyncListener;
import com.g4vrk.hologramAddon.manager.HologramViewerManager;
import com.g4vrk.react.api.addon.JavaAddon;
import com.g4vrk.react.api.extension.environment.AddonEnvironment;
import com.g4vrk.react.api.player.TrackedPlayer;
import com.g4vrk.schedula.task.TickSchedule;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurationNode;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shows each player's recent inference results in a hologram above their head.
 *
 * <p>Listeners and commands go through the addon environment, so the plugin removes them
 * when the addon is disabled.</p>
 */
public final class ReactHologramAddon extends JavaAddon {

    private static final String MAIN_CONFIG = "main-config.yml";

    private final HologramViewerManager viewerManager = new HologramViewerManager();
    private final Map<UUID, TargetedHologram> holograms = new ConcurrentHashMap<>();
    private @Nullable CompletableFuture<Void> pendingReload;
    private boolean disabled;

    private volatile HologramFactory hologramFactory;
    private volatile HologramTextGenerator<TrackedPlayer> hologramTextGenerator;
    private HologramCommand command;

    @Override
    public void onEnable() {

        final AddonEnvironment environment = environment();
        final LoadedModuleSet configuration = load();
        this.hologramFactory = configuration.hologramFactory();
        this.hologramTextGenerator = configuration.textGenerator();

        environment.listeners().register(new ConnectionListener(this.viewerManager));
        for (final Player player : Bukkit.getOnlinePlayers()) {
            environment.scheduler().<Player>mapEntity(environment.platform()::adaptPlayer).scheduleEntity(player, () -> this.registerOnlineViewer(player), TickSchedule.instant());
        }
        environment.listeners().register(new HistoryEntryAddedListener(
                this.viewerManager::receivers,
                this::hologram,
                () -> this.hologramTextGenerator,
                environment.scheduler().<Player>mapEntity(environment.platform()::adaptPlayer)
        ));
        environment.listeners().register(new HologramResyncListener(this::resync));

        this.command = new HologramCommand(
                environment.actions(),
                environment.scheduler().<Player>mapEntity(environment.platform()::adaptPlayer),
                this::reloadAsync,
                this::resync,
                configuration.main()
        );
        this.command.register(environment.commands());

    }

    private synchronized void registerOnlineViewer(final @NotNull Player player) {

        if (!this.disabled && player.isOnline()) {
            this.viewerManager.add(player.getUniqueId());
        }

    }

    @Override
    public void onDisable() {

        final CompletableFuture<Void> reload;
        synchronized (this) {
            this.disabled = true;
            reload = this.pendingReload;
            this.pendingReload = null;
        }
        if (reload != null) {
            reload.cancel(false);
        }

        this.holograms.values().forEach(TargetedHologram::hide);
        this.holograms.clear();
        this.viewerManager.receivers().clear();

    }

    /**
     * Clears stale displays after a change in this player's session or position.
     *
     * @param player player whose viewer state is cleared
     */
    public void resync(final @NotNull Player player) {

        this.removeViewer(player);
        this.destroyHologram(player.getUniqueId());

    }

    private void removeViewer(final @NotNull Player player) {

        for (final TargetedHologram hologram : this.holograms.values()) {
            hologram.removeViewer(player);
        }

    }

    public void destroyHologram(final @NotNull UUID uuid) {

        final TargetedHologram removed = this.holograms.remove(uuid);

        if (removed != null) {
            removed.hide();
        }

    }

    private synchronized @Nullable TargetedHologram hologram(final @NotNull Player target) {

        if (this.disabled) {
            return null;
        }

        return this.holograms.computeIfAbsent(target.getUniqueId(), ignored -> {
            final TargetedHologram hologram = this.hologramFactory.create(target);
            environment().scheduler().<Player>mapEntity(environment().platform()::adaptPlayer).scheduleEntity(target, task -> {
                if (!hologram.move()) {
                    task.cancel();
                }
            }, TickSchedule.fixedRate(1));
            return hologram;
        });

    }

    /**
     * Reads the config off the server thread, then swaps the holograms on it.
     *
     * @return completes once the new settings are in use
     */
    public synchronized @NotNull CompletableFuture<Void> reloadAsync() {

        if (this.disabled) {
            return CompletableFuture.failedFuture(new IllegalStateException("Hologram addon is disabled"));
        }
        if (this.pendingReload != null && !this.pendingReload.isDone()) {
            return CompletableFuture.failedFuture(new IllegalStateException("Hologram addon is already reloading"));
        }

        final CompletableFuture<Void> result = CompletableFuture.supplyAsync(this::load).thenAcceptAsync(this::applyReload, task -> environment().scheduler().<Player>mapEntity(environment().platform()::adaptPlayer).schedule(task, TickSchedule.instant()));
        this.pendingReload = result;
        result.whenComplete((ignored, failure) -> {
            if (failure != null && !result.isCancelled()) {
                logger().error("Could not reload the hologram addon", failure);
            }
        });
        return result;

    }

    private synchronized void applyReload(final @NotNull LoadedModuleSet configuration) {

        if (this.disabled) {
            return;
        }
        this.command.reload(configuration.main());
        this.holograms.values().forEach(TargetedHologram::hide);
        this.holograms.clear();
        this.hologramFactory = configuration.hologramFactory();
        this.hologramTextGenerator = configuration.textGenerator();

    }

    private @NotNull LoadedModuleSet load() {

        final CommentedConfigurationNode main;
        final String language = "en".equalsIgnoreCase(System.getProperty("user.language")) ? "en" : "ru";

        try {
            main = environment().configs().file(MAIN_CONFIG, ConfigurationNode.class)
                    .defaults(getClass().getClassLoader(), language + "/" + MAIN_CONFIG)
                    .build()
                    .loadNode();
        } catch (final IOException exception) {
            throw new IllegalStateException("Could not load " + MAIN_CONFIG, exception);
        }

        final ConfigurationNode settings = main.node("settings");

        return new LoadedModuleSet(
                main,
                new HologramFactory(
                        new ModernHologramConfiguration(settings.node("text-display")),
                        new LegacyHologramConfiguration(settings.node("armor-stand")),
                        settings.node("create-team").getBoolean(false)
                ),
                new ReactHologramTextGenerator(
                        new ReactHologramTextConfiguration(settings.node("text")),
                        environment().text()
                )
        );

    }

    private record LoadedModuleSet(
            @NotNull CommentedConfigurationNode main,
            @NotNull HologramFactory hologramFactory,
            @NotNull HologramTextGenerator<TrackedPlayer> textGenerator
    ) {
    }

}
