package com.g4vrk.hologramAddon.minestom;

import com.g4vrk.hologramAddon.HologramSettings;
import com.g4vrk.hologramAddon.command.HologramCommand;
import com.g4vrk.react.api.addon.JavaAddon;
import com.g4vrk.react.api.event.EventPriority;
import com.g4vrk.react.api.event.bus.Subscribe;
import com.g4vrk.react.api.event.check.InferenceResultEvent;
import com.g4vrk.react.api.player.TrackedPlayer;
import com.g4vrk.react.api.platform.player.PlatformPlayer;
import com.g4vrk.schedula.task.TickSchedule;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.metadata.display.TextDisplayMeta;
import net.minestom.server.event.EventListener;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.minestom.server.event.player.PlayerMoveEvent;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Displays inference history with Minestom text display entities.
 */
public final class MinestomHologramAddon extends JavaAddon {

    private final Map<UUID, Entity> displays = new ConcurrentHashMap<>();
    private volatile HologramSettings settings;
    private volatile HologramCommand command;
    private volatile CompletableFuture<Void> pendingReload;
    private volatile boolean disabled;

    @Override
    public void onEnable() {

        this.settings = HologramSettings.load(environment(), getClass().getClassLoader());
        this.command = new HologramCommand(
                environment().actions(),
                environment().scheduler(),
                this::reloadAsync,
                this::resync,
                this.settings.main()
        );
        this.command.register(environment().commands());
        environment().listeners().register(new InferenceListener());
        environment().listeners().register(EventListener.builder(PlayerMoveEvent.class)
                .handler(this::move)
                .build());
        environment().listeners().register(EventListener.builder(PlayerDisconnectEvent.class)
                .handler(event -> remove(event.getPlayer().getUuid()))
                .build());

    }

    @Override
    public void onDisable() {

        synchronized (this) {
            this.disabled = true;
            if (this.pendingReload != null) {
                this.pendingReload.cancel(false);
                this.pendingReload = null;
            }
        }
        this.displays.values().forEach(Entity::remove);
        this.displays.clear();

    }

    private synchronized @NotNull CompletableFuture<Void> reloadAsync() {

        if (this.disabled) {
            return CompletableFuture.failedFuture(new IllegalStateException("Hologram addon is disabled"));
        }
        if (this.pendingReload != null && !this.pendingReload.isDone()) {
            return CompletableFuture.failedFuture(new IllegalStateException("Hologram addon is already reloading"));
        }

        final CompletableFuture<Void> result = CompletableFuture
                .supplyAsync(() -> HologramSettings.load(environment(), getClass().getClassLoader()))
                .thenAcceptAsync(this::applyReload, task -> environment().scheduler().schedule(task, TickSchedule.instant()));
        this.pendingReload = result;
        return result;

    }

    private synchronized void applyReload(final @NotNull HologramSettings newSettings) {

        if (this.disabled) {
            return;
        }
        this.settings = newSettings;
        this.command.reload(newSettings.main());
        this.displays.values().forEach(Entity::remove);
        this.displays.clear();
        environment().players().forEach(this::update);

    }

    private void resync(final @NotNull PlatformPlayer player) {

        remove(player.uniqueId());
        final TrackedPlayer trackedPlayer = environment().players().get(player.uniqueId());
        if (trackedPlayer != null) {
            update(trackedPlayer);
        }

    }

    private void update(final @NotNull TrackedPlayer trackedPlayer) {

        if (this.disabled) {
            return;
        }

        final PlatformPlayer platformPlayer = trackedPlayer.platform();
        final Player target = platformPlayer.unwrap(Player.class);
        if (target == null || !platformPlayer.online()) {
            return;
        }
        final var lines = this.settings.textGenerator().generate(trackedPlayer);
        if (lines.isEmpty()) {
            remove(target.getUuid());
            return;
        }

        final Component text = Component.join(JoinConfiguration.newlines(), lines);
        final Entity display = this.displays.computeIfAbsent(target.getUuid(), ignored -> create(target, text));
        final TextDisplayMeta metadata = (TextDisplayMeta) display.getEntityMeta();
        metadata.setText(text);
        metadata.setShadow(this.settings.modern().shadow());
        metadata.setBackgroundColor(this.settings.modern().backgroundColor());
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
        metadata.setShadow(this.settings.modern().shadow());
        metadata.setBackgroundColor(this.settings.modern().backgroundColor());
        display.setInstance(target.getInstance(), position(target));
        return display;

    }

    private void move(final @NotNull PlayerMoveEvent event) {

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

        return playerPosition.add(0.0D, this.settings.modern().yOffset(), 0.0D);

    }

    private void remove(final @NotNull UUID uuid) {

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
            final PlatformPlayer platformViewer = environment().platform().player(viewer.getUuid());
            if (platformViewer == null || !platformViewer.hasPermission("react.hologram.view")
                    || viewer.getInstance() != target.getInstance()) {
                display.removeViewer(viewer);
            }
        }

        for (final PlatformPlayer platformViewer : environment().platform().onlinePlayers()) {
            final Player viewer = platformViewer.unwrap(Player.class);
            if (viewer != null && platformViewer.online()
                    && platformViewer.hasPermission("react.hologram.view")
                    && viewer.getInstance() == target.getInstance()) {
                display.addViewer(viewer);
            }
        }

    }

    private final class InferenceListener {

        @Subscribe(priority = EventPriority.MONITOR)
        public void onInferenceResult(final @NotNull InferenceResultEvent event) {

            final TrackedPlayer target = event.check().player();
            final PlatformPlayer platformPlayer = target.platform();
            environment().scheduler().scheduleEntity(
                    platformPlayer,
                    () -> update(target),
                    TickSchedule.instant()
            );

        }

    }

}
