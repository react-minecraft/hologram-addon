package com.g4vrk.hologramAddon;

import com.g4vrk.functionalConfiguration.Config;
import com.g4vrk.functionalConfiguration.loader.YamlConfigLoader;
import com.g4vrk.hologramAddon.command.argument.HologramReloadArgument;
import com.g4vrk.hologramAddon.command.argument.HologramResyncArgument;
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
import com.g4vrk.react.React;
import com.g4vrk.react.api.Factory;
import com.g4vrk.react.api.addon.JavaAddon;
import com.g4vrk.react.player.ReactPlayer;
import com.g4vrk.schedula.task.TickSchedule;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.incendo.cloud.Command;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.configurate.ConfigurationNode;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ReactHologramAddon extends JavaAddon {

    public static ReactHologramAddon INSTANCE;

    private Config mainConfig;

    private HologramReloadArgument reloadArgument;
    private HologramResyncArgument resyncArgument;

    private HologramViewerManager viewerManager;

    private Map<UUID, TargetedHologram> holograms;

    private volatile HologramFactory hologramFactory;

    private volatile HologramTextGenerator<ReactPlayer> hologramTextGenerator;

    private final AtomicBoolean reloading = new AtomicBoolean();

    @Override
    public void onLoad() {
        INSTANCE = this;

        this.viewerManager = new HologramViewerManager();
        this.holograms = new ConcurrentHashMap<>();
    }

    @Override
    public void onEnable() {

        final Path mainConfigPath =
                directory()
                        .toPath()
                        .resolve("main-config.yml");

        if (!mainConfigPath.toFile().exists()) {
            super.saveResource(
                    "main-config.yml",
                    false
            );
        }

        final LoadedModules configuration =
                this.loadConfigurableModules();

        this.hologramFactory =
                configuration.hologramFactory();

        this.hologramTextGenerator =
                configuration.textGenerator();

        final PluginManager pluginManager =
                Bukkit.getPluginManager();

        final React react = React.INSTANCE;
        final Plugin plugin = react.getPlugin();

        pluginManager.registerEvents(
                new ConnectionListener(
                        this.viewerManager
                ),
                plugin
        );

        pluginManager.registerEvents(
                new HistoryEntryAddedListener(
                        this.viewerManager::receivers,
                        this::hologram,
                        () -> this.hologramTextGenerator
                ),
                plugin
        );

        pluginManager.registerEvents(
                new HologramResyncListener(
                        this::resync
                ),
                plugin
        );

        final Factory<Command.Builder<CommandSender>> builderFactory = () -> react.getCommandBuilderFactory().create().literal("hologram");
        this.reloadArgument = new HologramReloadArgument(builderFactory, react.getScheduler(), react.getActionParser());
        this.resyncArgument = new HologramResyncArgument(builderFactory, react.getActionParser(), this::resync);

        react.getCommandManager().command(reloadArgument.build());
        react.getCommandManager().command(resyncArgument.build());

        slf4jLogger().info(
                "ReactHologramAddon successfully enabled!"
        );
    }

    public void resync(
            final @NotNull Player player
    ) {

        for (final TargetedHologram hologram : holograms.values()) {

            hologram.hideFrom(player);

        }

        destroyHologram(player.getUniqueId());

    }

    public void destroyHologram(
            final @NotNull UUID uuid
    ) {

        final TargetedHologram removed = this.holograms.remove(uuid);

        if (removed != null) {
            removed.hide();
        }

    }

    private @NotNull TargetedHologram hologram(
            final @NotNull Player target
    ) {

        return this.holograms.computeIfAbsent(
                target.getUniqueId(),
                ignored -> hologramFactory.create(target)
        );

    }

    @Override
    public void onDisable() {
        this.holograms.values().forEach(TargetedHologram::hide);
        this.holograms.clear();
        this.viewerManager.receivers().clear();
        slf4jLogger().info("ReactHologramAddon successfully disabled!");
    }

    public @NotNull CompletableFuture<Void> reloadAsync() {

        if (!this.reloading.compareAndSet(false, true)) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException(
                            "Hologram addon is already reloading"
                    )
            );
        }

        return CompletableFuture
                .supplyAsync(this::loadConfigurableModules)
                .thenCompose(configuration -> {

                    final CompletableFuture<Void> future = new CompletableFuture<>();

                    reloadArgument.reload();
                    resyncArgument.reload();

                    React.INSTANCE.getScheduler().schedule(
                            () -> {
                                try {

                                    this.holograms
                                            .values()
                                            .forEach(
                                                    TargetedHologram::hide
                                            );

                                    this.holograms.clear();

                                    this.hologramFactory =
                                            configuration.hologramFactory();

                                    this.hologramTextGenerator =
                                            configuration.textGenerator();

                                    future.complete(null);

                                } catch (Throwable throwable) {
                                    future.completeExceptionally(
                                            throwable
                                    );
                                }
                            }, TickSchedule.instant()
                    );

                    return future;
                })
                .whenComplete(
                        (ignored, throwable) ->
                                this.reloading.set(false)
                );
    }

    public @NotNull Config getMainConfig() {
        return mainConfig;
    }

    private @NotNull ReactHologramAddon.LoadedModules loadConfigurableModules() {

        final YamlConfigLoader configLoader =
                new YamlConfigLoader();

        final Path mainConfigPath =
                directory()
                        .toPath()
                        .resolve("main-config.yml");

        try {
            mainConfig = configLoader.from(mainConfigPath);
        } catch (IOException exception) {
            throw new RuntimeException(
                    "Failed to load main-config.yml",
                    exception
            );
        }

        final ConfigurationNode settings =
                mainConfig.node("settings");

        final HologramFactory hologramFactory =
                new HologramFactory(
                        new ModernHologramConfiguration(
                                settings.node("text-display")
                        ),
                        new LegacyHologramConfiguration(
                                settings.node("armor-stand")
                        ),
                        settings.node("create-team").getBoolean(true)
                );

        final HologramTextGenerator<ReactPlayer> textGenerator =
                new ReactHologramTextGenerator(
                        new ReactHologramTextConfiguration(
                                settings.node("text")
                        )
                );

        return new LoadedModules(
                hologramFactory,
                textGenerator
        );
    }

    private record LoadedModules(
            @NotNull HologramFactory hologramFactory,
            @NotNull HologramTextGenerator<ReactPlayer> textGenerator
    ) {}
}