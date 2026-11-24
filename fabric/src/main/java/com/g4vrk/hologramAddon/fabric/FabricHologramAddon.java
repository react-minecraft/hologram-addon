package com.g4vrk.hologramAddon.fabric;

import com.g4vrk.hologramAddon.HologramSettings;
import com.g4vrk.hologramAddon.command.HologramCommand;
import com.g4vrk.react.api.addon.JavaAddon;
import com.g4vrk.react.api.event.EventPriority;
import com.g4vrk.react.api.event.bus.Subscribe;
import com.g4vrk.react.api.event.check.InferenceResultEvent;
import com.g4vrk.react.api.player.TrackedPlayer;
import com.g4vrk.react.api.platform.player.PlatformPlayer;
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
import com.g4vrk.schedula.task.TickSchedule;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Renders inference history as per-viewer PacketEvents text display entities.
 */
public final class FabricHologramAddon extends JavaAddon {

    private static final AtomicInteger ENTITY_IDS = new AtomicInteger(1_950_000_000);
    private final Map<UUID, DisplayState> displays = new ConcurrentHashMap<>();
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
        environment().scheduler().schedule(this::tick, TickSchedule.fixedRate(5));

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
        for (final DisplayState display : this.displays.values()) {
            for (final UUID viewerId : Set.copyOf(display.viewers)) {
                final PlatformPlayer viewer = environment().platform().player(viewerId);
                if (viewer != null) {
                    destroy(viewer, display);
                }
            }
        }
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
                .thenAcceptAsync(
                        this::applyReload,
                        task -> environment().scheduler().schedule(task, TickSchedule.instant())
                );
        this.pendingReload = result;
        return result;

    }

    private synchronized void applyReload(final @NotNull HologramSettings newSettings) {

        if (this.disabled) {
            return;
        }
        this.settings = newSettings;
        this.command.reload(newSettings.main());
        clearDisplays();
        for (final TrackedPlayer player : environment().players()) {
            update(player);
        }

    }

    private void resync(final @NotNull PlatformPlayer player) {

        removeViewer(player.uniqueId());
        final TrackedPlayer trackedPlayer = environment().players().get(player.uniqueId());
        if (trackedPlayer != null) {
            update(trackedPlayer);
        }

    }

    private void tick() {

        if (this.disabled) {
            return;
        }
        for (final Map.Entry<UUID, DisplayState> entry : this.displays.entrySet()) {
            final PlatformPlayer target = environment().platform().player(entry.getKey());
            final ServerPlayer nativeTarget = target == null ? null : target.unwrap(ServerPlayer.class);
            if (target == null || nativeTarget == null || !target.online()) {
                remove(entry.getKey());
                continue;
            }
            reconcile(nativeTarget, entry.getValue());
        }

    }

    private void update(final @NotNull TrackedPlayer trackedPlayer) {

        if (this.disabled) {
            return;
        }
        final PlatformPlayer platformPlayer = trackedPlayer.platform();
        final ServerPlayer target = platformPlayer.unwrap(ServerPlayer.class);
        if (target == null || !platformPlayer.online()) {
            remove(target == null ? platformPlayer.uniqueId() : target.getUUID());
            return;
        }
        final var lines = this.settings.textGenerator().generate(trackedPlayer);
        if (lines.isEmpty()) {
            remove(target.getUUID());
            return;
        }

        final Component text = Component.join(JoinConfiguration.newlines(), lines);
        final DisplayState display = this.displays.computeIfAbsent(target.getUUID(), ignored -> new DisplayState());
        final boolean textChanged = !text.equals(display.text);
        display.text = text;
        reconcile(target, display, textChanged);

    }

    private void reconcile(
            final @NotNull ServerPlayer target,
            final @NotNull DisplayState display
    ) {

        reconcile(target, display, false);

    }

    private void reconcile(
            final @NotNull ServerPlayer target,
            final @NotNull DisplayState display,
            final boolean textChanged
    ) {

        final double x = target.getX();
        final double y = target.getY() + this.settings.modern().yOffset();
        final double z = target.getZ();
        final boolean moved = x != display.x || y != display.y || z != display.z;
        if (moved) {
            display.x = x;
            display.y = y;
            display.z = z;
            display.location = location(target);
        }
        final Set<UUID> visible = new HashSet<>();
        for (final PlatformPlayer candidate : environment().platform().onlinePlayers()) {
            final ServerPlayer viewer = candidate.unwrap(ServerPlayer.class);
            if (viewer == null || !candidate.online() || !candidate.hasPermission("react.hologram.view")
                    || viewer.level() != target.level()
                    || viewer.distanceToSqr(target) > 4096.0D) {
                continue;
            }
            visible.add(candidate.uniqueId());
            if (display.viewers.add(candidate.uniqueId())) {
                spawn(candidate, display);
            } else {
                if (textChanged) {
                    updatePacket(candidate, display);
                }
                if (moved) {
                    teleport(candidate, display);
                }
            }
        }
        for (final UUID viewerId : Set.copyOf(display.viewers)) {
            if (!visible.contains(viewerId)) {
                final PlatformPlayer viewer = environment().platform().player(viewerId);
                if (viewer != null) {
                    destroy(viewer, display);
                } else {
                    display.viewers.remove(viewerId);
                }
            }
        }

    }

    private void spawn(
            final @NotNull PlatformPlayer viewer,
            final @NotNull DisplayState display
    ) {

        final ServerPlayer nativeViewer = viewer.unwrap(ServerPlayer.class);
        if (nativeViewer == null) {
            return;
        }
        PacketEvents.getAPI().getPlayerManager().sendPacket(nativeViewer, new WrapperPlayServerSpawnEntity(
                display.entityId,
                display.entityUuid,
                EntityTypes.TEXT_DISPLAY,
                display.location,
                0.0F,
                0,
                null
        ));
        PacketEvents.getAPI().getPlayerManager().sendPacket(nativeViewer, new WrapperPlayServerEntityMetadata(
                display.entityId,
                metadata(display.text)
        ));

    }

    private void updatePacket(final @NotNull PlatformPlayer viewer, final @NotNull DisplayState display) {

        final ServerPlayer nativeViewer = viewer.unwrap(ServerPlayer.class);
        if (nativeViewer != null) {
            PacketEvents.getAPI().getPlayerManager().sendPacket(nativeViewer, new WrapperPlayServerEntityMetadata(
                    display.entityId,
                    metadata(display.text)
            ));
        }

    }

    private void teleport(final @NotNull PlatformPlayer viewer, final @NotNull DisplayState display) {

        final ServerPlayer nativeViewer = viewer.unwrap(ServerPlayer.class);
        if (nativeViewer != null) {
            PacketEvents.getAPI().getPlayerManager().sendPacket(nativeViewer, new WrapperPlayServerEntityTeleport(
                    display.entityId,
                    display.location,
                    false
            ));
        }

    }

    private @NotNull List<EntityData<?>> metadata(final @NotNull Component text) {

        final float scale = this.settings.modern().textScale();
        return List.of(
                new EntityData<>(23, EntityDataTypes.ADV_COMPONENT, text),
                new EntityData<>(12, EntityDataTypes.VECTOR3F, new Vector3f(scale, scale, scale)),
                new EntityData<>(15, EntityDataTypes.BYTE, (byte) 1),
                new EntityData<>(25, EntityDataTypes.INT, this.settings.modern().backgroundColor()),
                new EntityData<>(
                        11,
                        EntityDataTypes.VECTOR3F,
                        new Vector3f(0.0F, this.settings.modern().translationY(), 0.0F)
                ),
                new EntityData<>(27, EntityDataTypes.BYTE, this.settings.modern().textFlags())
        );

    }

    private @NotNull Location location(final @NotNull ServerPlayer target) {

        return new Location(target.getX(), target.getY() + this.settings.modern().yOffset(), target.getZ(), 0.0F, 0.0F);

    }

    private void removeViewer(final @NotNull UUID viewerId) {

        for (final DisplayState display : this.displays.values()) {
            if (display.viewers.remove(viewerId)) {
                final PlatformPlayer viewer = environment().platform().player(viewerId);
                if (viewer != null) {
                    PacketEvents.getAPI().getPlayerManager().sendPacket(
                            viewer.unwrap(ServerPlayer.class),
                            new WrapperPlayServerDestroyEntities(display.entityId)
                    );
                }
            }
        }

    }

    private void remove(final @NotNull UUID targetId) {

        final DisplayState display = this.displays.remove(targetId);
        if (display == null) {
            return;
        }
        for (final UUID viewerId : Set.copyOf(display.viewers)) {
            final PlatformPlayer viewer = environment().platform().player(viewerId);
            if (viewer != null) {
                destroy(viewer, display);
            }
        }

    }

    private void destroy(final @NotNull PlatformPlayer viewer, final @NotNull DisplayState display) {

        final ServerPlayer nativeViewer = viewer.unwrap(ServerPlayer.class);
        if (nativeViewer != null) {
            PacketEvents.getAPI().getPlayerManager().sendPacket(
                    nativeViewer,
                    new WrapperPlayServerDestroyEntities(display.entityId)
            );
        }
        display.viewers.remove(viewer.uniqueId());

    }

    private void clearDisplays() {

        for (final UUID targetId : Set.copyOf(this.displays.keySet())) {
            remove(targetId);
        }

    }

    private final class InferenceListener {

        @Subscribe(priority = EventPriority.MONITOR)
        public void onInferenceResult(final @NotNull InferenceResultEvent event) {

            final TrackedPlayer target = event.check().player();
            environment().scheduler().scheduleEntity(target.platform(), () -> update(target), TickSchedule.instant());

        }

    }

    private static final class DisplayState {

        private final int entityId = ENTITY_IDS.getAndDecrement();
        private final UUID entityUuid = UUID.randomUUID();
        private final Set<UUID> viewers = ConcurrentHashMap.newKeySet();
        private Location location = new Location(0.0D, 0.0D, 0.0D, 0.0F, 0.0F);
        private Component text = Component.empty();
        private double x = Double.NaN;
        private double y = Double.NaN;
        private double z = Double.NaN;

    }

}
