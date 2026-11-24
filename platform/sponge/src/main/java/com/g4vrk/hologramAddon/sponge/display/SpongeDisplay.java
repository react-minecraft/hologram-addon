package com.g4vrk.hologramAddon.sponge.display;

import com.g4vrk.hologramAddon.HologramConfiguration;
import com.g4vrk.react.api.extension.environment.AddonEnvironment;
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
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.api.entity.living.player.server.ServerPlayer;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Manages packets and viewer visibility for one Sponge text display.
 */
public final class SpongeDisplay {

    private static final AtomicInteger ENTITY_IDS = new AtomicInteger(1_950_000_000);
    private final AddonEnvironment environment;
    private final Supplier<HologramConfiguration> settings;
    private final ServerPlayer target;
    private final int entityId = ENTITY_IDS.getAndDecrement();
    private final UUID entityUuid = UUID.randomUUID();
    private final Set<UUID> visibleTo = ConcurrentHashMap.newKeySet();
    private volatile List<Component> lines = List.of();

    public SpongeDisplay(
            final @NotNull AddonEnvironment environment,
            final @NotNull Supplier<HologramConfiguration> settings,
            final @NotNull ServerPlayer target
    ) {

        this.environment = environment;
        this.settings = settings;
        this.target = target;

    }

    public void update(final @NotNull List<Component> lines) {

        this.lines = List.copyOf(lines);
        final List<EntityData<?>> metadata = this.metadata();
        for (final PlatformPlayer platformViewer : this.environment.platform().onlinePlayers()) {
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

    public void updateViewer(final @NotNull PlatformPlayer platformViewer) {

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
        final var config = this.settings.get().modern();
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

        final var config = this.settings.get().modern();
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

    public void move() {

        final var pos = target.position();
        final Location location = new Location(pos.x(), pos.y() + this.settings.get().modern().yOffset(), pos.z(), 0.0F, 0.0F);
        this.environment.platform().onlinePlayers().forEach(this::updateViewer);
        for (final UUID uuid : visibleTo) {
            final PlatformPlayer viewer = this.environment.platform().player(uuid);
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

    public void remove(final @NotNull UUID uuid) {

        if (!visibleTo.remove(uuid)) {
            return;
        }
        final PlatformPlayer viewer = this.environment.platform().player(uuid);
        final ServerPlayer handle = viewer == null ? null : viewer.unwrap(ServerPlayer.class);
        if (handle != null && viewer.online()) {
            PacketEvents.getAPI().getPlayerManager().sendPacket(handle, new WrapperPlayServerDestroyEntities(entityId));
        }

    }

    public boolean targets(final @NotNull UUID uuid) {

        return this.target.uniqueId().equals(uuid);

    }

    public void forget(final @NotNull UUID uuid) {

        this.visibleTo.remove(uuid);

    }

    public void hide() {

        for (final UUID uuid : visibleTo.toArray(UUID[]::new)) {
            remove(uuid);
        }

    }

}
