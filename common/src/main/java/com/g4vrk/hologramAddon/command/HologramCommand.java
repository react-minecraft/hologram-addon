package com.g4vrk.hologramAddon.command;

import com.g4vrk.functionalActions.list.ExecutableActionList;
import com.g4vrk.functionalActions.parser.ActionParser;
import com.g4vrk.react.api.extension.command.CommandRegistrar;
import com.g4vrk.schedula.task.TickSchedule;
import com.g4vrk.schedula.task.scheduler.Scheduler;
import net.kyori.adventure.audience.Audience;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.bukkit.data.MultiplePlayerSelector;
import org.incendo.cloud.bukkit.parser.selector.MultiplePlayerSelectorParser;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * {@code /react hologram reload} and {@code /react hologram resync [players]}.
 *
 * <p>Plain Cloud commands under the plugin's {@code /react} root; the plugin removes them with
 * the addon.</p>
 */
public final class HologramCommand {

    private static final String ROOT = "react";
    private static final String LITERAL = "hologram";
    private static final String TARGETS = "targets";

    private final ActionParser<Audience> parser;
    private final Scheduler scheduler;
    private final Supplier<CompletableFuture<Void>> reload;
    private final Consumer<Player> resync;

    private volatile Messages messages;

    public HologramCommand(
            final @NotNull ActionParser<Audience> parser,
            final @NotNull Scheduler scheduler,
            final @NotNull Supplier<CompletableFuture<Void>> reload,
            final @NotNull Consumer<Player> resync,
            final @NotNull ConfigurationNode config
    ) {
        this.parser = parser;
        this.scheduler = scheduler;
        this.reload = reload;
        this.resync = resync;
        this.reload(config);
    }

    public void reload(final @NotNull ConfigurationNode config) {
        this.messages = new Messages(
                actions(config, "reload", "on-start"),
                actions(config, "reload", "on-finish"),
                actions(config, "synchronization", "on-resync")
        );
    }

    public void register(final @NotNull CommandRegistrar commands) {
        commands.register(ROOT, commands.command(ROOT)
                .literal(LITERAL)
                .literal("reload")
                .permission("react.hologram.reload")
                .handler(context -> reload(context.sender())));

        commands.register(ROOT, commands.command(ROOT)
                .literal(LITERAL)
                .literal("resync")
                .permission("react.hologram.resync")
                .optional(TARGETS, MultiplePlayerSelectorParser.multiplePlayerSelectorParser())
                .handler(context -> resync(
                        context.sender(),
                        context.<MultiplePlayerSelector>optional(TARGETS).orElse(null)
                )));
    }

    private void reload(final @NotNull CommandSender sender) {
        this.messages.reloadStart().run(sender);
        this.reload.get().thenRun(() -> this.scheduler.schedule(
                () -> this.messages.reloadFinish().run(sender),
                TickSchedule.instant()
        ));
    }

    private void resync(final @NotNull CommandSender sender, final MultiplePlayerSelector selector) {
        final Collection<Player> targets;

        if (selector != null) {
            targets = selector.values();
        } else if (sender instanceof Player player) {
            targets = List.of(player);
        } else {
            return;
        }

        if (targets.isEmpty()) {
            return;
        }

        final List<String> names = new ArrayList<>(targets.size());

        for (final Player target : targets) {
            this.resync.accept(target);
            names.add(target.getName());
        }

        final String joined = String.join(", ", names);
        this.messages.resynced().run(sender, value -> value.replace("{synced-for}", joined));
    }

    private ExecutableActionList<? super Audience> actions(
            final @NotNull ConfigurationNode config,
            final @NotNull String... path
    ) {
        try {
            return this.parser.parseAll(config.node((Object[]) path).getList(String.class, List.of()));
        } catch (final SerializationException exception) {
            throw new IllegalStateException("Could not read the actions at " + String.join(".", path), exception);
        }
    }

    private record Messages(
            @NotNull ExecutableActionList<? super Audience> reloadStart,
            @NotNull ExecutableActionList<? super Audience> reloadFinish,
            @NotNull ExecutableActionList<? super Audience> resynced
    ) {
    }

}
