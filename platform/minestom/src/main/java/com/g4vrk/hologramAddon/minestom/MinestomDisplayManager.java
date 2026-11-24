package com.g4vrk.hologramAddon.minestom;

import com.g4vrk.hologramAddon.HologramConfiguration;
import com.g4vrk.react.api.extension.environment.AddonEnvironment;
import com.g4vrk.react.api.player.TrackedPlayer;
import com.g4vrk.react.api.platform.player.PlatformPlayer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.metadata.display.TextDisplayMeta;
import net.minestom.server.event.player.PlayerMoveEvent;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Manages Minestom display entities and their viewers.
 */
final class MinestomDisplayManager {

    private final AddonEnvironment environment;
    private final Supplier<HologramConfiguration> settings;
    private final BooleanSupplier disabled;
    private final Map<UUID, Entity> displays = new ConcurrentHashMap<>();

    MinestomDisplayManager(
            final @NotNull AddonEnvironment environment,
            final @NotNull Supplier<HologramConfiguration> settings,
            final @NotNull BooleanSupplier disabled
    ) {

        this.environment = environment;
        this.settings = settings;
        this.disabled = disabled;

    }

    void clear() {

        this.displays.values().forEach(Entity::remove);
        this.displays.clear();

    }

    void update(final @NotNull TrackedPlayer trackedPlayer) {

        if (this.disabled.getAsBoolean()) {
            return;
        }

        final PlatformPlayer platformPlayer = trackedPlayer.platform();
        final Player target = platformPlayer.unwrap(Player.class);
        if (target == null || !platformPlayer.online()) {
            return;
        }
        final var lines = this.settings.get().textGenerator().generate(trackedPlayer);
        if (lines.isEmpty()) {
            remove(target.getUuid());
            return;
        }

        final Component text = Component.join(JoinConfiguration.newlines(), lines);
        final Entity display = this.displays.computeIfAbsent(target.getUuid(), ignored -> create(target, text));
        final TextDisplayMeta metadata = (TextDisplayMeta) display.getEntityMeta();
        metadata.setText(text);
        metadata.setShadow(this.settings.get().modern().shadow());
        metadata.setBackgroundColor(this.settings.get().modern().backgroundColor());
        display.teleport(position(target));
        updateViewers(display, target);

    }

    private @NotNull Entity create(
            final @NotNull Player target,
            final @NotNull Component text
    ) {

        final Entity display = new Entity(EntityType.TEXT_DISPLAY);
        display.setNoGravity(true);
        display.setAutoViewable(false);
        final TextDisplayMeta metadata = (TextDisplayMeta) display.getEntityMeta();
        metadata.setText(text);
        metadata.setShadow(this.settings.get().modern().shadow());
        metadata.setBackgroundColor(this.settings.get().modern().backgroundColor());
        display.setInstance(target.getInstance(), position(target));
        return display;

    }

    void move(final @NotNull PlayerMoveEvent event) {

        final Entity display = this.displays.get(event.getPlayer().getUuid());
        if (display != null) {
            display.teleport(position(event.getPlayer(), event.getNewPosition()));
            updateViewers(display, event.getPlayer());
        }

    }

    private @NotNull Pos position(final @NotNull Player player) {

        return position(player, player.getPosition());

    }

    private @NotNull Pos position(
            final @NotNull Player player,
            final @NotNull Pos playerPosition
    ) {

        return playerPosition.add(0.0D, this.settings.get().modern().yOffset(), 0.0D);

    }

    void remove(final @NotNull UUID uuid) {

        final Entity display = this.displays.remove(uuid);
        if (display != null) {
            display.remove();
        }

    }

    private void updateViewers(
            final @NotNull Entity display,
            final @NotNull Player target
    ) {

        for (final Player viewer : display.getViewers()) {
            final PlatformPlayer platformViewer = this.environment.platform().player(viewer.getUuid());
            if (platformViewer == null || !platformViewer.hasPermission("react.hologram.view")
                    || viewer.getInstance() != target.getInstance()) {
                display.removeViewer(viewer);
            }
        }

        for (final PlatformPlayer platformViewer : this.environment.platform().onlinePlayers()) {
            final Player viewer = platformViewer.unwrap(Player.class);
            if (viewer != null && platformViewer.online()
                    && platformViewer.hasPermission("react.hologram.view")
                    && viewer.getInstance() == target.getInstance()) {
                display.addViewer(viewer);
            }
        }

    }

}
