package com.g4vrk.hologramAddon.command;

import com.g4vrk.functionalActions.list.ExecutableActionList;
import com.g4vrk.functionalActions.parser.ActionParser;
import com.g4vrk.react.api.extension.command.CommandRegistrar;
import com.g4vrk.react.api.platform.player.PlatformPlayer;
import com.g4vrk.react.api.platform.sender.PlatformSender;
import com.g4vrk.schedula.task.TickSchedule;
import com.g4vrk.schedula.task.scheduler.TaskScheduler;
import io.leangen.geantyref.TypeToken;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.incendo.cloud.key.CloudKey;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
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
@ApiStatus.Internal
public final class HologramCommand {

    private static final String ROOT = "react";
    private static final String LITERAL = "hologram";
    private static final CloudKey<Collection<PlatformPlayer>> TARGETS = CloudKey.of("targets", new TypeToken<Collection<PlatformPlayer>>() {});
    private static final Component CONSOLE_TARGETS_REQUIRED = Component.text("Specify target player(s): /react hologram resync <players>.", NamedTextColor.RED);
    private static final Component NO_TARGETS_FOUND = Component.text("No matching online players found.", NamedTextColor.RED);

    private final ActionParser<Audience> parser;
    private final TaskScheduler<PlatformPlayer> scheduler;
    private final Supplier<CompletableFuture<Void>> reload;
    private final Consumer<PlatformPlayer> resync;

    private volatile MessageSet messages;

    public HologramCommand(
        final @NotNull ActionParser<Audience> parser,
        final @NotNull TaskScheduler<PlatformPlayer> scheduler,
        final @NotNull Supplier<CompletableFuture<Void>> reload,
        final @NotNull Consumer<PlatformPlayer> resync,
        final @NotNull ConfigurationNode config
    ) {

        this.parser = parser;
        this.scheduler = scheduler;
        this.reload = reload;
        this.resync = resync;
        this.reload(config);

    }

    public void reload(
        final @NotNull ConfigurationNode config
    ) {

        this.messages = new MessageSet(
            actions(config, "reload", "on-start"),
            actions(config, "reload", "on-finish"),
            actions(config, "reload", "on-failure"),
            actions(config, "synchronization", "on-resync")
        );

    }

    public void register(
        final @NotNull CommandRegistrar commands
    ) {

        commands.register(
            ROOT,
            commands.command(ROOT)
                .literal(LITERAL)
                .literal("reload")
                .permission("react.hologram.reload")
                .handler(context -> this.reload(context.sender()))
        );

        commands.register(
            ROOT,
            commands.command(ROOT)
                .literal(LITERAL)
                .literal("resync")
                .permission("react.hologram.resync")
                .argument(commands.multiplePlayersArgument(TARGETS, false))
                .handler(context -> this.resync(context.sender(), context.optional(TARGETS).orElse(null)))
        );

    }

    private void reload(
        final @NotNull PlatformSender sender
    ) {

        this.messages.reloadStart().run(sender);
        this.reload.get().whenComplete((ignored, failure) -> {
            final ExecutableActionList<? super Audience> response = failure == null ? this.messages.reloadFinish() : this.messages.reloadFailure();
            final Runnable notify = () -> response.run(sender);
            final PlatformPlayer player = sender.asPlayer();

            if (player != null) {
                this.scheduler.scheduleEntity(player, notify, TickSchedule.instant());
            } else {
                this.scheduler.schedule(notify, TickSchedule.instant());
            }
        });

    }

    private void resync(
        final @NotNull PlatformSender sender,
        final @Nullable Collection<PlatformPlayer> selector
    ) {

        final Collection<PlatformPlayer> targets;
        final PlatformPlayer senderPlayer = sender.asPlayer();

        if (selector != null) {
            targets = selector;
        } else if (senderPlayer != null) {
            targets = List.of(senderPlayer);
        } else {
            sender.sendMessage(CONSOLE_TARGETS_REQUIRED);
            return;
        }

        if (targets.isEmpty()) {
            sender.sendMessage(NO_TARGETS_FOUND);
            return;
        }

        final List<String> names = new ArrayList<>(targets.size());

        for (final PlatformPlayer target : targets) {
            this.scheduler.scheduleEntity(
                target,
                () -> this.resync.accept(target),
                TickSchedule.instant()
            );
            names.add(target.name());
        }

        final String joined = String.join(", ", names);
        this.messages.resynced().run(sender, value -> value.replace("{synced-for}", joined));

    }

    private @NotNull ExecutableActionList<? super Audience> actions(
        final @NotNull ConfigurationNode config,
        final @NotNull String... path
    ) {

        try {
            return this.parser.parseAll(config.node((Object[]) path).getList(String.class, List.of()));
        } catch (final SerializationException exception) {
            throw new IllegalStateException("Could not read the actions at " + String.join(".", path), exception);
        }

    }

    private record MessageSet(
        @NotNull ExecutableActionList<? super Audience> reloadStart,
        @NotNull ExecutableActionList<? super Audience> reloadFinish,
        @NotNull ExecutableActionList<? super Audience> reloadFailure,
        @NotNull ExecutableActionList<? super Audience> resynced
    ) {
    }

}
