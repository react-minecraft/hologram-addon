package com.g4vrk.hologramAddon.hologram.impl;

import com.g4vrk.hologramAddon.hologram.TargetedHologram;
import com.g4vrk.hologramAddon.hologram.config.impl.ModernHologramConfiguration;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.manager.player.PlayerManager;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.protocol.world.Location;
import com.github.retrooper.packetevents.util.Vector3f;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetPassengers;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTeams;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Packet-backed {@code TextDisplay} hologram attached to a target player as a passenger.
 */
@ApiStatus.Internal
public final class ModernTargetedHologram extends TargetedHologram {

    private static final AtomicInteger ENTITY_ID_COUNTER = new AtomicInteger(1_950_000_000);
    private static final JoinConfiguration NEWLINE_JOIN = JoinConfiguration.separator(Component.newline());

    private final ModernHologramConfiguration config;
    private final boolean createTeam;
    private final Vector3f scaleVector;
    private final Vector3f translationVector;
    private final int entityId = ENTITY_ID_COUNTER.getAndDecrement();
    private final UUID entityUniqueId = UUID.randomUUID();
    private final Set<UUID> spawnedFor = new ObjectOpenHashSet<>();
    private final Set<UUID> teamCreatedFor = new ObjectOpenHashSet<>();
    private final String teamName = "mh_" + Integer.toUnsignedString(this.entityId, 36);

    private int[] lastPassengers = new int[0];
    private boolean passengersChanged;
    private @Nullable List<Component> cachedLines;
    private @Nullable Component cachedText;
    private @Nullable List<EntityData<?>> cachedModernMetadata;
    private @Nullable List<EntityData<?>> cachedLegacyDisplayMetadata;

    public ModernTargetedHologram(
        final @NotNull Player target,
        final @NotNull ModernHologramConfiguration config,
        final boolean createTeam
    ) {

        super(target);
        this.config = config;
        this.createTeam = createTeam;
        final float scale = config.textScale();
        this.scaleVector = new Vector3f(scale, scale, scale);
        this.translationVector = new Vector3f(0.0F, config.translationY(), 0.0F);

    }

    boolean hasViewer(
        final @NotNull UUID viewerId
    ) {

        return this.spawnedFor.contains(viewerId);

    }

    @Override
    protected void showTo(
        final @NotNull Player viewer,
        final @NotNull List<Component> lines
    ) {

        final UUID viewerId = viewer.getUniqueId();
        final boolean firstDisplay = this.spawnedFor.add(viewerId);

        if (firstDisplay) {
            this.spawn(viewer);
        }

        this.update(viewer, lines);

    }

    private void spawn(
        final @NotNull Player viewer
    ) {

        final org.bukkit.Location targetLocation = this.target.getLocation();
        final Location location = new Location(targetLocation.getX(), targetLocation.getY() + this.config.yOffset(), targetLocation.getZ(), 0F, 0F);
        final PlayerManager playerManager = PacketEvents.getAPI().getPlayerManager();

        if (this.createTeam) {
            this.sendNameTagVisibility(viewer, playerManager);
        }

        playerManager.sendPacket(
            viewer,
            new WrapperPlayServerSpawnEntity(this.entityId, this.entityUniqueId, EntityTypes.TEXT_DISPLAY, location, 0F, 0, null)
        );

        final int[] passengers = this.passengers();
        this.lastPassengers = passengers;
        playerManager.sendPacket(viewer, new WrapperPlayServerSetPassengers(this.target.getEntityId(), passengers));

    }

    private int @NotNull [] passengers() {

        final List<Entity> existingPassengers = this.target.getPassengers();
        final int[] passengers = new int[existingPassengers.size() + 1];

        for (int index = 0; index < existingPassengers.size(); index++) {
            passengers[index] = existingPassengers.get(index).getEntityId();
        }

        passengers[existingPassengers.size()] = this.entityId;
        return passengers;

    }

    @Override
    protected void prepareMove() {

        if (this.spawnedFor.isEmpty()) {
            this.passengersChanged = false;
            return;
        }

        final int[] passengers = this.passengers();
        this.passengersChanged = !Arrays.equals(this.lastPassengers, passengers);
        this.lastPassengers = passengers;

    }

    @Override
    protected void moveTo(
        final @NotNull Player viewer
    ) {

        if (this.passengersChanged && this.spawnedFor.contains(viewer.getUniqueId())) {
            PacketEvents.getAPI().getPlayerManager().sendPacket(
                viewer,
                new WrapperPlayServerSetPassengers(this.target.getEntityId(), this.lastPassengers)
            );
        }

    }

    private void update(
        final @NotNull Player viewer,
        final @NotNull List<Component> lines
    ) {

        final PlayerManager playerManager = PacketEvents.getAPI().getPlayerManager();
        final ClientVersion version = playerManager.getClientVersion(viewer);
        final List<EntityData<?>> metadata = this.metadataFor(version, lines);
        playerManager.sendPacket(viewer, new WrapperPlayServerEntityMetadata(this.entityId, metadata));

    }

    private @NotNull List<EntityData<?>> metadataFor(
        final @NotNull ClientVersion version,
        final @NotNull List<Component> lines
    ) {

        if (this.cachedLines == null || (this.cachedLines != lines && !this.cachedLines.equals(lines))) {
            this.cachedLines = List.copyOf(lines);
            this.cachedText = Component.join(NEWLINE_JOIN, this.cachedLines);
            this.cachedModernMetadata = null;
            this.cachedLegacyDisplayMetadata = null;
        }

        if (version.isOlderThan(ClientVersion.V_1_20_2)) {
            if (this.cachedLegacyDisplayMetadata == null) {
                this.cachedLegacyDisplayMetadata = this.buildMetadata(true, this.cachedText);
            }

            return this.cachedLegacyDisplayMetadata;
        }

        if (this.cachedModernMetadata == null) {
            this.cachedModernMetadata = this.buildMetadata(false, this.cachedText);
        }

        return this.cachedModernMetadata;

    }

    private @NotNull List<EntityData<?>> buildMetadata(
        final boolean pre1202,
        final @NotNull Component text
    ) {

        if (pre1202) {
            return List.of(
                new EntityData<>(22, EntityDataTypes.ADV_COMPONENT, text),
                new EntityData<>(11, EntityDataTypes.VECTOR3F, this.scaleVector),
                new EntityData<>(14, EntityDataTypes.BYTE, (byte) 1),
                new EntityData<>(24, EntityDataTypes.INT, this.config.backgroundColor()),
                new EntityData<>(10, EntityDataTypes.VECTOR3F, this.translationVector),
                new EntityData<>(26, EntityDataTypes.BYTE, this.config.textFlags())
            );
        }

        return List.of(
            new EntityData<>(23, EntityDataTypes.ADV_COMPONENT, text),
            new EntityData<>(12, EntityDataTypes.VECTOR3F, this.scaleVector),
            new EntityData<>(15, EntityDataTypes.BYTE, (byte) 1),
            new EntityData<>(25, EntityDataTypes.INT, this.config.backgroundColor()),
            new EntityData<>(11, EntityDataTypes.VECTOR3F, this.translationVector),
            new EntityData<>(27, EntityDataTypes.BYTE, this.config.textFlags())
        );

    }

    @Override
    public void hideFrom(
        final @NotNull Player viewer
    ) {

        final UUID viewerId = viewer.getUniqueId();
        final boolean wasSpawned = this.spawnedFor.remove(viewerId);
        final boolean hadTeam = this.teamCreatedFor.remove(viewerId);

        if (!wasSpawned || !viewer.isOnline()) {
            return;
        }

        final PlayerManager playerManager = PacketEvents.getAPI().getPlayerManager();
        playerManager.sendPacket(viewer, new WrapperPlayServerDestroyEntities(this.entityId));

        if (hadTeam) {
            playerManager.sendPacket(
                viewer,
                new WrapperPlayServerTeams(this.teamName, WrapperPlayServerTeams.TeamMode.REMOVE, (WrapperPlayServerTeams.ScoreBoardTeamInfo) null)
            );
        }

    }

    private void sendNameTagVisibility(
        final @NotNull Player viewer,
        final @NotNull PlayerManager playerManager
    ) {

        if (viewer.getScoreboard().getEntryTeam(this.target.getName()) != null) {
            return;
        }

        final WrapperPlayServerTeams.ScoreBoardTeamInfo info = new WrapperPlayServerTeams.ScoreBoardTeamInfo(
            Component.empty(),
            Component.empty(),
            Component.empty(),
            WrapperPlayServerTeams.NameTagVisibility.ALWAYS,
            WrapperPlayServerTeams.CollisionRule.ALWAYS,
            NamedTextColor.WHITE,
            WrapperPlayServerTeams.OptionData.NONE
        );

        this.teamCreatedFor.add(viewer.getUniqueId());
        playerManager.sendPacket(
            viewer,
            new WrapperPlayServerTeams(this.teamName, WrapperPlayServerTeams.TeamMode.CREATE, info, this.target.getName())
        );

    }

}
