package com.g4vrk.hologramAddon.command.argument;

import com.g4vrk.functionalActions.list.ExecutableActionList;
import com.g4vrk.functionalActions.parser.ActionParser;
import com.g4vrk.functionalConfiguration.Config;
import com.g4vrk.hologramAddon.ReactHologramAddon;
import com.g4vrk.react.api.Factory;
import com.g4vrk.react.api.ReloadObserver;
import com.g4vrk.react.command.argument.LocalArgument;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.kyori.adventure.audience.Audience;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.Command;
import org.incendo.cloud.bukkit.data.MultiplePlayerSelector;
import org.incendo.cloud.bukkit.parser.selector.MultiplePlayerSelectorParser;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.configurate.serialize.SerializationException;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

public final class HologramResyncArgument extends LocalArgument implements ReloadObserver {

    private final Factory<Command.Builder<CommandSender>> builderFactory;

    private final ActionParser<Audience> actionParser;

    private final Consumer<Player> syncFunction;

    private ExecutableActionList<? super Audience> syncActions;

    public HologramResyncArgument(
            @NotNull Factory<Command.Builder<CommandSender>> builderFactory,
            @NotNull ActionParser<Audience> actionParser,
            @NotNull Consumer<Player> syncFunction
    ) {
        this.builderFactory = builderFactory;
        this.actionParser = actionParser;
        this.syncFunction = syncFunction;

        this.reload();
    }

    @Override
    public @NotNull Command.Builder<CommandSender> build() {
        return builderFactory.create()
                .literal("resync")
                .optional("targets", MultiplePlayerSelectorParser.multiplePlayerSelectorParser())
                .handler(context -> {
                    final CommandSender sender = context.sender();

                    final MultiplePlayerSelector selector = context.getOrDefault("targets", null);

                    final Collection<Player> targets;

                    if (selector == null) {

                        if (!(sender instanceof Player player)) return;
                        targets = Collections.singletonList(player);

                    } else {

                        targets = selector.values();

                    }

                    if (!targets.isEmpty()) {

                        final List<String> names = new ObjectArrayList<>();

                        for (final Player target : targets) {
                            syncFunction.accept(target);
                            names.add(target.getName());
                        }

                        syncActions.run(sender, s -> s.replace("{synced-for}", String.join(", ", names)));

                    }

                });
    }

    public void reload() {

        final Config config = ReactHologramAddon.INSTANCE.getMainConfig();

        this.onReload(config);

    }

    @Override
    public void onReload(@NotNull Config config) {

        try {
            this.syncActions = this.actionParser.parseAll(
                    config.node("synchronization", "on-resync").getList(String.class, Collections.emptyList())
            );
        } catch (final SerializationException ex) {
            throw new RuntimeException("Failed to load configuration values for alerts argument", ex);
        }

    }
}
