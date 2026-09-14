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
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

public final class LegacyTargetedHologram extends TargetedHologram {

    private final LegacyHologramConfiguration config;
    private final boolean createTeam;

    private static final AtomicInteger ENTITY_ID_COUNTER =
            new AtomicInteger(1_900_000_000);

    private final Map<UUID, List<ArmorStandLine>> spawnedFor = new ConcurrentHashMap<>();

    private final String teamName = "lh_" + Integer.toUnsignedString(ThreadLocalRandom.current().nextInt(), 36);

    public LegacyTargetedHologram(
            @NotNull Player target,
            @NotNull LegacyHologramConfiguration config
    ) {
        this(target, config, true);
    }

    public LegacyTargetedHologram(
            @NotNull Player target,
            @NotNull LegacyHologramConfiguration config,
            boolean createTeam
    ) {
        super(target);

        this.config = config;
        this.createTeam = createTeam;
    }

    @Override
    protected void showTo(
            @NotNull Player viewer,
            @NotNull List<Component> lines
    ) {

        final UUID viewerId = viewer.getUniqueId();

        List<ArmorStandLine> armorStands =
                this.spawnedFor.get(viewerId);

        if (armorStands == null) {

            armorStands = new ArrayList<>();

            this.spawnedFor.put(
                    viewerId,
                    armorStands
            );

            if (this.createTeam) {
                this.sendNameTagVisibility(viewer);
            }
        }

        this.resize(
                viewer,
                armorStands,
                lines.size()
        );

        this.update(
                viewer,
                armorStands,
                lines
        );
    }

    private void resize(
            final @NotNull Player viewer,
            final @NotNull List<ArmorStandLine> armorStands,
            final int requiredSize
    ) {

        while (armorStands.size() < requiredSize) {

            final ArmorStandLine line =
                    new ArmorStandLine(
                            ENTITY_ID_COUNTER.getAndDecrement(),
                            UUID.randomUUID()
                    );

            armorStands.add(line);

            this.spawn(
                    viewer,
                    line
            );
        }

        while (armorStands.size() > requiredSize) {

            final ArmorStandLine removed =
                    armorStands.remove(
                            armorStands.size() - 1
                    );

            PacketEvents.getAPI()
                    .getPlayerManager()
                    .sendPacket(
                            viewer,
                            new WrapperPlayServerDestroyEntities(
                                    removed.entityId
                            )
                    );
        }
    }

    private void spawn(
            final @NotNull Player viewer,
            final @NotNull ArmorStandLine line
    ) {

        final org.bukkit.Location targetLocation =
                this.target.getLocation();

        final Location location =
                new Location(
                        targetLocation.getX(),
                        targetLocation.getY() + this.config.yOffset(),
                        targetLocation.getZ(),
                        0F,
                        0F
                );

        final PlayerManager playerManager =
                PacketEvents.getAPI().getPlayerManager();

        playerManager.sendPacket(
                viewer,
                new WrapperPlayServerSpawnLivingEntity(
                        line.entityId,
                        line.uuid,
                        EntityTypes.ARMOR_STAND,
                        location,
                        0F,
                        new Vector3d(
                                0.0D,
                                0.0D,
                                0.0D
                        ),
                        List.of()
                )
        );
    }

    private void update(
            final @NotNull Player viewer,
            final @NotNull List<ArmorStandLine> armorStands,
            final @NotNull List<Component> lines
    ) {

        final PlayerManager playerManager =
                PacketEvents.getAPI().getPlayerManager();

        final ClientVersion clientVersion =
                playerManager.getClientVersion(viewer);

        final org.bukkit.Location targetLocation =
                this.target.getLocation();

        final int lineCount = lines.size();

        for (int i = 0; i < lineCount; i++) {

            final ArmorStandLine armorStand =
                    armorStands.get(i);

            final Component component =
                    lines.get(i);

            final double y = targetLocation.getY()
                    + this.config.yOffset()
                    + ((lineCount - 1 - i) * this.config.lineSpacing());

            final Location location =
                    new Location(
                            targetLocation.getX(),
                            y,
                            targetLocation.getZ(),
                            0F,
                            0F
                    );

            playerManager.sendPacket(
                    viewer,
                    new WrapperPlayServerEntityTeleport(
                            armorStand.entityId,
                            location,
                            false
                    )
            );

            playerManager.sendPacket(
                    viewer,
                    new WrapperPlayServerEntityMetadata(
                            armorStand.entityId,
                            this.createMetadata(
                                    clientVersion,
                                    component
                            )
                    )
            );
        }
    }

    private @NotNull List<EntityData<?>> createMetadata(
            final @NotNull ClientVersion version,
            final @NotNull Component component
    ) {

        final List<EntityData<?>> metadata =
                new ObjectArrayList<>();

        metadata.add(
                new EntityData<>(
                        0,
                        EntityDataTypes.BYTE,
                        (byte) 0x20
                )
        );

        if (version.isOlderThan(ClientVersion.V_1_13)) {

            final String legacy =
                    new LegacyComponent(component)
                            .getLegacy();

            metadata.add(
                    new EntityData<>(
                            2,
                            EntityDataTypes.STRING,
                            legacy
                    )
            );

        } else {

            metadata.add(
                    new EntityData<>(
                            2,
                            EntityDataTypes.OPTIONAL_ADV_COMPONENT,
                            Optional.of(component)
                    )
            );
        }

        if (version.isOlderThan(ClientVersion.V_1_9)) {

            metadata.add(
                    new EntityData<>(
                            3,
                            EntityDataTypes.BYTE,
                            (byte) 1
                    )
            );

        } else {

            metadata.add(
                    new EntityData<>(
                            3,
                            EntityDataTypes.BOOLEAN,
                            true
                    )
            );
        }

        return metadata;
    }

    @Override
    public void hideFrom(@NotNull Player viewer) {

        final List<ArmorStandLine> armorStands =
                this.spawnedFor.remove(
                        viewer.getUniqueId()
                );

        if (armorStands == null) {
            return;
        }

        final PlayerManager playerManager =
                PacketEvents.getAPI().getPlayerManager();

        if (!armorStands.isEmpty()) {

            final int[] entityIds =
                    armorStands.stream()
                            .mapToInt(line -> line.entityId)
                            .toArray();

            playerManager.sendPacket(
                    viewer,
                    new WrapperPlayServerDestroyEntities(
                            entityIds
                    )
            );
        }

        if (this.createTeam) {
            playerManager.sendPacket(
                    viewer,
                    new WrapperPlayServerTeams(
                            this.teamName,
                            WrapperPlayServerTeams.TeamMode.REMOVE,
                            (WrapperPlayServerTeams.ScoreBoardTeamInfo) null
                    )
            );
        }
    }

    private void sendNameTagVisibility(
            @NotNull Player viewer
    ) {

        final WrapperPlayServerTeams.ScoreBoardTeamInfo info =
                new WrapperPlayServerTeams.ScoreBoardTeamInfo(
                        Component.empty(),
                        Component.empty(),
                        Component.empty(),
                        WrapperPlayServerTeams.NameTagVisibility.ALWAYS,
                        WrapperPlayServerTeams.CollisionRule.ALWAYS,
                        NamedTextColor.WHITE,
                        WrapperPlayServerTeams.OptionData.NONE
                );

        PacketEvents.getAPI()
                .getPlayerManager()
                .sendPacket(
                        viewer,
                        new WrapperPlayServerTeams(
                                this.teamName,
                                WrapperPlayServerTeams.TeamMode.CREATE,
                                info,
                                this.target.getName()
                        )
                );
    }

    private static final class ArmorStandLine {

        private final int entityId;
        private final UUID uuid;

        private ArmorStandLine(
                int entityId,
                UUID uuid
        ) {
            this.entityId = entityId;
            this.uuid = uuid;
        }
    }
}