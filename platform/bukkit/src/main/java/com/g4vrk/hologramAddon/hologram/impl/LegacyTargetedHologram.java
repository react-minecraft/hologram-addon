package com.g4vrk.hologramAddon.hologram.impl;

import com.g4vrk.hologramAddon.hologram.TargetedHologram;
import com.g4vrk.hologramAddon.hologram.config.impl.LegacyHologramConfiguration;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.manager.player.PlayerManager;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.protocol.util.LegacyComponent;
import com.github.retrooper.packetevents.protocol.world.Location;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityTeleport;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnLivingEntity;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTeams;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Packet-backed armor-stand hologram for pre-1.19.4 Minecraft clients.
 */
@ApiStatus.Internal
public final class LegacyTargetedHologram extends TargetedHologram {

    private static final AtomicInteger ENTITY_ID_COUNTER = new AtomicInteger(1_900_000_000);
    private static final EntityData<Byte> INVISIBLE_FLAGS = new EntityData<>(0, EntityDataTypes.BYTE, (byte) 0x20);
    private static final EntityData<Byte> CUSTOM_NAME_VISIBLE_BYTE = new EntityData<>(3, EntityDataTypes.BYTE, (byte) 1);
    private static final EntityData<Boolean> CUSTOM_NAME_VISIBLE_BOOLEAN = new EntityData<>(3, EntityDataTypes.BOOLEAN, true);

    private final LegacyHologramConfiguration config;
    private final boolean createTeam;
    private final Map<UUID, List<ArmorStandLine>> spawnedFor = new HashMap<>();
    private final Set<UUID> teamCreatedFor = new ObjectOpenHashSet<>();
    private final String teamName = "lh_" + Integer.toUnsignedString(ThreadLocalRandom.current().nextInt(), 36);

    private double lastX = Double.NaN;
    private double lastY = Double.NaN;
    private double lastZ = Double.NaN;
    private boolean positionChanged;
    private Location @Nullable [] cachedMoveLocations;
    private double cachedUpdateX = Double.NaN;
    private double cachedUpdateY = Double.NaN;
    private double cachedUpdateZ = Double.NaN;
    private Location @Nullable [] cachedUpdateLocations;
    private @Nullable List<Component> cachedLines;
    private List<EntityData<?>> @Nullable [] cachedV18Metadata;
    private List<EntityData<?>> @Nullable [] cachedV19Metadata;
    private List<EntityData<?>> @Nullable [] cachedV113Metadata;

    public LegacyTargetedHologram(
        final @NotNull Player target,
        final @NotNull LegacyHologramConfiguration config,
        final boolean createTeam
    ) {

        super(target);
        this.config = config;
        this.createTeam = createTeam;

    }

    @Override
    protected void showTo(
        final @NotNull Player viewer,
        final @NotNull List<Component> lines
    ) {

        final UUID viewerId = viewer.getUniqueId();
        List<ArmorStandLine> armorStands = this.spawnedFor.get(viewerId);
        final PlayerManager playerManager = PacketEvents.getAPI().getPlayerManager();

        if (armorStands == null) {
            armorStands = new ArrayList<>(lines.size());
            this.spawnedFor.put(viewerId, armorStands);

            if (this.createTeam) {
                this.sendNameTagVisibility(viewer, playerManager);
            }
        }

        final org.bukkit.Location targetLocation = this.target.getLocation();
        final Location[] locations = this.resolveUpdateLocations(targetLocation.getX(), targetLocation.getY(), targetLocation.getZ(), lines.size());
        this.resize(viewer, playerManager, armorStands, locations);
        this.update(viewer, playerManager, armorStands, lines, locations);

    }

    private Location @NotNull [] resolveUpdateLocations(
        final double x,
        final double y,
        final double z,
        final int lineCount
    ) {

        if (this.cachedUpdateLocations != null
            && this.cachedUpdateLocations.length == lineCount
            && this.cachedUpdateX == x
            && this.cachedUpdateY == y
            && this.cachedUpdateZ == z) {
            return this.cachedUpdateLocations;
        }

        this.cachedUpdateX = x;
        this.cachedUpdateY = y;
        this.cachedUpdateZ = z;
        this.cachedUpdateLocations = this.buildLocations(x, y, z, lineCount);
        return this.cachedUpdateLocations;

    }

    private Location @NotNull [] buildLocations(
        final double x,
        final double y,
        final double z,
        final int lineCount
    ) {

        final Location[] locations = new Location[lineCount];
        final double baseY = y + this.config.yOffset();
        final double spacing = this.config.lineSpacing();

        for (int index = 0; index < lineCount; index++) {
            final double lineY = baseY + ((lineCount - 1 - index) * spacing);
            locations[index] = new Location(x, lineY, z, 0F, 0F);
        }

        return locations;

    }

    private void resize(
        final @NotNull Player viewer,
        final @NotNull PlayerManager playerManager,
        final @NotNull List<ArmorStandLine> armorStands,
        final Location @NotNull [] locations
    ) {

        final int requiredSize = locations.length;

        while (armorStands.size() < requiredSize) {
            final int index = armorStands.size();
            final ArmorStandLine line = new ArmorStandLine(ENTITY_ID_COUNTER.getAndDecrement(), UUID.randomUUID());
            armorStands.add(line);
            this.spawn(viewer, playerManager, line, locations[index]);
        }

        final int excess = armorStands.size() - requiredSize;

        if (excess > 0) {
            final int[] removedIds = new int[excess];

            for (int index = 0; index < excess; index++) {
                removedIds[index] = armorStands.remove(armorStands.size() - 1).entityId;
            }

            playerManager.sendPacket(viewer, new WrapperPlayServerDestroyEntities(removedIds));
        }

    }

    private void spawn(
        final @NotNull Player viewer,
        final @NotNull PlayerManager playerManager,
        final @NotNull ArmorStandLine line,
        final @NotNull Location location
    ) {

        playerManager.sendPacket(
            viewer,
            new WrapperPlayServerSpawnLivingEntity(
                line.entityId,
                line.uuid,
                EntityTypes.ARMOR_STAND,
                location,
                0F,
                Vector3d.zero(),
                List.of()
            )
        );

    }

    private void update(
        final @NotNull Player viewer,
        final @NotNull PlayerManager playerManager,
        final @NotNull List<ArmorStandLine> armorStands,
        final @NotNull List<Component> lines,
        final Location @NotNull [] locations
    ) {

        final ClientVersion clientVersion = playerManager.getClientVersion(viewer);
        final List<EntityData<?>>[] metadataLines = this.metadataFor(clientVersion, lines);
        final int lineCount = lines.size();

        for (int index = 0; index < lineCount; index++) {
            final ArmorStandLine armorStand = armorStands.get(index);
            playerManager.sendPacket(viewer, new WrapperPlayServerEntityTeleport(armorStand.entityId, locations[index], false));
            playerManager.sendPacket(viewer, new WrapperPlayServerEntityMetadata(armorStand.entityId, metadataLines[index]));
        }

    }

    @SuppressWarnings("unchecked")
    private List<EntityData<?>> @NotNull [] metadataFor(
        final @NotNull ClientVersion version,
        final @NotNull List<Component> lines
    ) {

        if (this.cachedLines == null || (this.cachedLines != lines && !this.cachedLines.equals(lines))) {
            this.cachedLines = List.copyOf(lines);
            this.cachedV18Metadata = null;
            this.cachedV19Metadata = null;
            this.cachedV113Metadata = null;
        }

        if (version.isOlderThan(ClientVersion.V_1_9)) {
            if (this.cachedV18Metadata == null) {
                this.cachedV18Metadata = this.buildMetadataLines(this.cachedLines, true, true);
            }

            return this.cachedV18Metadata;
        }

        if (version.isOlderThan(ClientVersion.V_1_13)) {
            if (this.cachedV19Metadata == null) {
                this.cachedV19Metadata = this.buildMetadataLines(this.cachedLines, true, false);
            }

            return this.cachedV19Metadata;
        }

        if (this.cachedV113Metadata == null) {
            this.cachedV113Metadata = this.buildMetadataLines(this.cachedLines, false, false);
        }

        return this.cachedV113Metadata;

    }

    @SuppressWarnings("unchecked")
    private List<EntityData<?>> @NotNull [] buildMetadataLines(
        final @NotNull List<Component> lines,
        final boolean legacyText,
        final boolean byteNameVisible
    ) {

        final List<EntityData<?>>[] result = new List[lines.size()];
        final EntityData<?> visibilityData = byteNameVisible ? CUSTOM_NAME_VISIBLE_BYTE : CUSTOM_NAME_VISIBLE_BOOLEAN;

        for (int index = 0; index < lines.size(); index++) {
            final Component component = lines.get(index);
            final EntityData<?> nameData = legacyText
                ? new EntityData<>(2, EntityDataTypes.STRING, new LegacyComponent(component).getLegacy())
                : new EntityData<>(2, EntityDataTypes.OPTIONAL_ADV_COMPONENT, Optional.of(component));
            result[index] = List.of(INVISIBLE_FLAGS, nameData, visibilityData);
        }

        return result;

    }

    @Override
    protected void prepareMove() {

        if (this.spawnedFor.isEmpty()) {
            this.positionChanged = false;
            return;
        }

        final org.bukkit.Location location = this.target.getLocation();
        final double x = location.getX();
        final double y = location.getY();
        final double z = location.getZ();
        this.positionChanged = this.lastX != x || this.lastY != y || this.lastZ != z;

        if (this.positionChanged) {
            this.lastX = x;
            this.lastY = y;
            this.lastZ = z;
            this.cachedMoveLocations = null;
        }

    }

    @Override
    protected void moveTo(
        final @NotNull Player viewer
    ) {

        if (!this.positionChanged) {
            return;
        }

        final List<ArmorStandLine> stands = this.spawnedFor.get(viewer.getUniqueId());

        if (stands == null || stands.isEmpty()) {
            return;
        }

        if (this.cachedMoveLocations == null || this.cachedMoveLocations.length != stands.size()) {
            this.cachedMoveLocations = this.buildLocations(this.lastX, this.lastY, this.lastZ, stands.size());
        }

        final PlayerManager playerManager = PacketEvents.getAPI().getPlayerManager();

        for (int index = 0; index < stands.size(); index++) {
            playerManager.sendPacket(viewer, new WrapperPlayServerEntityTeleport(stands.get(index).entityId, this.cachedMoveLocations[index], false));
        }

    }

    @Override
    public void hideFrom(
        final @NotNull Player viewer
    ) {

        final UUID viewerId = viewer.getUniqueId();
        final List<ArmorStandLine> armorStands = this.spawnedFor.remove(viewerId);
        final boolean hadTeam = this.teamCreatedFor.remove(viewerId);

        if (armorStands == null || !viewer.isOnline()) {
            return;
        }

        final PlayerManager playerManager = PacketEvents.getAPI().getPlayerManager();

        if (!armorStands.isEmpty()) {
            final int[] entityIds = new int[armorStands.size()];

            for (int index = 0; index < armorStands.size(); index++) {
                entityIds[index] = armorStands.get(index).entityId;
            }

            playerManager.sendPacket(viewer, new WrapperPlayServerDestroyEntities(entityIds));
        }

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

    private static final class ArmorStandLine {

        private final int entityId;
        private final UUID uuid;

        private ArmorStandLine(
            final int entityId,
            final @NotNull UUID uuid
        ) {

            this.entityId = entityId;
            this.uuid = uuid;

        }

    }

}
