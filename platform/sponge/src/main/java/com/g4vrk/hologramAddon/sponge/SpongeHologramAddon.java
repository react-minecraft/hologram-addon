package com.g4vrk.hologramAddon.sponge;

import com.g4vrk.hologramAddon.HologramSettings;
import com.g4vrk.hologramAddon.command.HologramCommand;
import com.g4vrk.react.api.addon.JavaAddon;
import com.g4vrk.react.api.event.EventPriority;
import com.g4vrk.react.api.event.bus.Subscribe;
import com.g4vrk.react.api.event.check.InferenceResultEvent;
import com.g4vrk.react.api.player.TrackedPlayer;
import com.g4vrk.react.api.platform.player.PlatformPlayer;
import com.g4vrk.schedula.task.TickSchedule;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.world.Location;
import com.github.retrooper.packetevents.util.Vector3f;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityTeleport;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.entity.MoveEntityEvent;
import org.spongepowered.api.event.network.ServerSideConnectionEvent;
import org.spongepowered.api.entity.living.player.server.ServerPlayer;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Renders per-viewer fake text displays on Sponge through PacketEvents.
 */
public final class SpongeHologramAddon extends JavaAddon {

    private static final AtomicInteger ENTITY_IDS = new AtomicInteger(1_950_000_000);
    private final Map<UUID, Display> displays = new ConcurrentHashMap<>();
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
        environment().listeners().register(new SpongeListener());

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
        this.displays.values().forEach(Display::hide);
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

    private synchronized void applyReload(final @NotNull HologramSettings settings) {

        if (this.disabled) {
            return;
        }
        this.settings = settings;
        this.command.reload(settings.main());
        this.displays.values().forEach(Display::hide);
        this.displays.clear();
        environment().players().forEach(this::update);

    }

    private void update(final @NotNull TrackedPlayer tracked) {

        if (this.disabled || !tracked.platform().online()) {
            return;
        }
        final ServerPlayer target = tracked.platform().unwrap(ServerPlayer.class);
        if (target == null) {
            return;
        }
        final List<Component> lines = this.settings.textGenerator().generate(tracked);
        if (lines.isEmpty()) {
            final Display removed = this.displays.remove(target.uniqueId());
            if (removed != null) {
                removed.hide();
            }
            return;
        }
        this.displays.computeIfAbsent(target.uniqueId(), ignored -> new Display(target)).update(lines);

    }

    private void resync(final @NotNull PlatformPlayer viewer) {

        for (final Display display : this.displays.values()) {
            display.remove(viewer.uniqueId());
            display.updateViewer(viewer);
        }
        final TrackedPlayer tracked = environment().players().get(viewer.uniqueId());
        if (tracked != null) {
            update(tracked);
        }

    }

    private final class InferenceListener {

        @Subscribe(priority = EventPriority.MONITOR)
        public void onInferenceResult(final @NotNull InferenceResultEvent event) {

            final TrackedPlayer tracked = event.check().player();
            environment().scheduler().scheduleEntity(tracked.platform(), () -> update(tracked), TickSchedule.instant());

        }

    }

    private final class SpongeListener {

        @Listener
        public void onMove(final MoveEntityEvent event) {

            if (event.entity() instanceof final ServerPlayer player) {
                final PlatformPlayer platformViewer = environment().platform().adaptPlayer(player);
                for (final Display display : displays.values()) {
                    if (display.target.uniqueId().equals(player.uniqueId())) {
                        display.move();
                    }
                    display.updateViewer(platformViewer);
                }
            }

        }

        @Listener
        public void onJoin(final ServerSideConnectionEvent.Join event) {

            final ServerPlayer player = event.player();
            final PlatformPlayer platformPlayer = environment().platform().adaptPlayer(player);
            environment().scheduler().scheduleEntity(
                    platformPlayer,
                    () -> resync(platformPlayer),
                    TickSchedule.instant()
            );

        }

        @Listener
        public void onDisconnect(final ServerSideConnectionEvent.Disconnect event) {

            event.profile().ifPresent(profile -> {
                final UUID uuid = profile.uniqueId();
                for (final Display display : displays.values()) {
                    display.visibleTo.remove(uuid);
                }
            });

        }

    }

    private final class Display {

        private final ServerPlayer target;
        private final int entityId = ENTITY_IDS.getAndDecrement();
        private final UUID entityUuid = UUID.randomUUID();
        private final Set<UUID> visibleTo = ConcurrentHashMap.newKeySet();
        private volatile List<Component> lines = List.of();

        private Display(final @NotNull ServerPlayer target) {

            this.target = target;

        }

        private void update(final @NotNull List<Component> lines) {

            this.lines = List.copyOf(lines);
            final List<EntityData<?>> metadata = this.metadata();
            for (final PlatformPlayer platformViewer : environment().platform().onlinePlayers()) {
                final ServerPlayer viewer = this.visiblePlayer(platformViewer);
                if (viewer == null) {
                    this.remove(platformViewer.uniqueId());
                } else {
                    if (this.visibleTo.add(viewer.uniqueId())) {
                        this.spawn(viewer);
                    }
                    this.sendMetadata(viewer, metadata);
                }
            }

        }

        private void updateViewer(final @NotNull PlatformPlayer platformViewer) {

            final ServerPlayer viewer = this.visiblePlayer(platformViewer);
            if (viewer == null) {
                this.remove(platformViewer.uniqueId());
                return;
            }
            if (this.visibleTo.add(viewer.uniqueId())) {
                this.spawn(viewer);
                this.sendMetadata(viewer, this.metadata());
            }

        }

        private @Nullable ServerPlayer visiblePlayer(final @NotNull PlatformPlayer platformViewer) {

            final ServerPlayer viewer = platformViewer.unwrap(ServerPlayer.class);
            if (viewer == null || !platformViewer.online() || !platformViewer.hasPermission("react.hologram.view")
                    || !viewer.world().uniqueId().equals(this.target.world().uniqueId()) || !inRange(viewer)) {
                return null;
            }
            return viewer;

        }

        private void spawn(final @NotNull ServerPlayer viewer) {

            final var pos = target.position();
            final var config = settings.modern();
            PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, new WrapperPlayServerSpawnEntity(
                    entityId,
                    entityUuid,
                    EntityTypes.TEXT_DISPLAY,
                    new Location(pos.x(), pos.y() + config.yOffset(), pos.z(), 0.0F, 0.0F),
                    0.0F,
                    0,
                    null
            ));

        }

        private @NotNull List<EntityData<?>> metadata() {

            final var config = settings.modern();
            final Component text = Component.join(JoinConfiguration.newlines(), this.lines);
            return List.of(
                    new EntityData<>(23, EntityDataTypes.ADV_COMPONENT, text),
                    new EntityData<>(
                            12,
                            EntityDataTypes.VECTOR3F,
                            new Vector3f(config.textScale(), config.textScale(), config.textScale())
                    ),
                    new EntityData<>(15, EntityDataTypes.BYTE, (byte) 1),
                    new EntityData<>(25, EntityDataTypes.INT, config.backgroundColor()),
                    new EntityData<>(11, EntityDataTypes.VECTOR3F, new Vector3f(0.0F, config.translationY(), 0.0F)),
                    new EntityData<>(27, EntityDataTypes.BYTE, config.textFlags())
            );

        }

        private void sendMetadata(
                final @NotNull ServerPlayer viewer,
                final @NotNull List<EntityData<?>> metadata
        ) {

            PacketEvents.getAPI().getPlayerManager().sendPacket(
                    viewer,
                    new WrapperPlayServerEntityMetadata(entityId, metadata)
            );

        }

        private void move() {

            final var pos = target.position();
            final Location location = new Location(pos.x(), pos.y() + settings.modern().yOffset(), pos.z(), 0.0F, 0.0F);
            environment().platform().onlinePlayers().forEach(this::updateViewer);
            for (final UUID uuid : visibleTo) {
                final PlatformPlayer viewer = environment().platform().player(uuid);
                final ServerPlayer handle = viewer == null ? null : viewer.unwrap(ServerPlayer.class);
                if (viewer == null || handle == null || !handle.world().uniqueId().equals(target.world().uniqueId()) || !inRange(handle)) {
                    remove(uuid);
                } else {
                    PacketEvents.getAPI().getPlayerManager().sendPacket(
                            handle,
                            new WrapperPlayServerEntityTeleport(entityId, location, false)
                    );
                }
            }
        }

        private boolean inRange(final @NotNull ServerPlayer viewer) {

            final var viewerPosition = viewer.position();
            final var targetPosition = target.position();
            final double range = Math.max(16.0D, viewer.viewDistance().get() * 16.0D);
            return viewerPosition.distanceSquared(targetPosition) <= range * range;

        }

        private void remove(final @NotNull UUID uuid) {

            if (!visibleTo.remove(uuid)) {
                return;
            }
            final PlatformPlayer viewer = environment().platform().player(uuid);
            final ServerPlayer handle = viewer == null ? null : viewer.unwrap(ServerPlayer.class);
            if (handle != null && viewer.online()) {
                PacketEvents.getAPI().getPlayerManager().sendPacket(handle, new WrapperPlayServerDestroyEntities(entityId));
            }

        }

        private void hide() {

            for (final UUID uuid : visibleTo.toArray(UUID[]::new)) {
                remove(uuid);
            }

        }

    }

}
