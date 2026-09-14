package com.g4vrk.hologramAddon.hologram.impl;

import com.g4vrk.hologramAddon.hologram.TargetedHologram;
import com.g4vrk.hologramAddon.hologram.config.impl.ModernHologramConfiguration;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.manager.player.PlayerManager;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.world.Location;
import com.github.retrooper.packetevents.util.Vector3f;
import com.github.retrooper.packetevents.wrapper.play.server.*;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class ModernTargetedHologram extends TargetedHologram {

    private final ModernHologramConfiguration config;
    private final boolean createTeam;

    private final int entityId = ThreadLocalRandom.current().nextInt(1, Integer.MAX_VALUE);

    private final UUID entityUniqueId = UUID.randomUUID();

    private final Set<UUID> spawnedFor = new ObjectOpenHashSet<>();

    private final String teamName = "mh_" + Integer.toUnsignedString(this.entityId, 36);

    public ModernTargetedHologram(
            @NotNull Player target,
            @NotNull ModernHologramConfiguration config
    ) {
        this(target, config, true);
    }

    public ModernTargetedHologram(
            @NotNull Player target,
            @NotNull ModernHologramConfiguration config,
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

        final boolean firstDisplay = this.spawnedFor.add(viewerId);

        if (firstDisplay) {
            this.spawn(viewer);
        }

        this.update(viewer, lines);
    }

    private void spawn(@NotNull Player viewer) {

        final Location location = new Location(
                this.target.getLocation().getX(),
                this.target.getLocation().getY() + this.config.yOffset(),
                this.target.getLocation().getZ(),
                0F,
                0F
        );

        final PlayerManager playerManager = PacketEvents.getAPI().getPlayerManager();

        if (this.createTeam) {
            this.sendNameTagVisibility(viewer);
        }

        playerManager.sendPacket(
                viewer,
                new WrapperPlayServerSpawnEntity(
                        this.entityId,
                        this.entityUniqueId,
                        EntityTypes.TEXT_DISPLAY,
                        location,
                        0F,
                        0,
                        null
                )
        );

        this.sendSetPassengersPacket(viewer);
    }

    private void sendSetPassengersPacket(
            @NotNull Player viewer
    ) {

        PacketEvents.getAPI().getPlayerManager().sendPacket(
                viewer,
                new WrapperPlayServerSetPassengers(
                        this.target.getEntityId(),
                        new int[]{entityId}
                )
        );

    }

    private void update(
            final @NotNull Player viewer,
            final @NotNull List<Component> lines
    ) {

        final Component text = Component.join(
                JoinConfiguration.separator(Component.newline()),
                lines
        );

        final float scale = this.config.textScale();

        final List<EntityData<?>> metadata = List.of(

                new EntityData<>(
                        23,
                        EntityDataTypes.ADV_COMPONENT,
                        text
                ),

                new EntityData<>(
                        12,
                        EntityDataTypes.VECTOR3F,
                        new Vector3f(
                                scale,
                                scale,
                                scale
                        )
                ),

                new EntityData<>(
                        15,
                        EntityDataTypes.BYTE,
                        (byte) 1
                ),

                new EntityData<>(
                        25,
                        EntityDataTypes.INT,
                        this.config.backgroundColor()
                ),

                new EntityData<>(
                        11,
                        EntityDataTypes.VECTOR3F,
                        new Vector3f(
                                0.0F,
                                this.config.translationY(),
                                0.0F
                        )
                ),

                new EntityData<>(
                        27,
                        EntityDataTypes.BYTE,
                        this.config.textFlags()
                )
        );

        PacketEvents.getAPI().getPlayerManager().sendPacket(
                viewer,
                new WrapperPlayServerEntityMetadata(
                        this.entityId,
                        metadata
                )
        );
    }

    @Override
    public void hideFrom(@NotNull Player viewer) {

        if (!this.spawnedFor.remove(viewer.getUniqueId())) {
            return;
        }

        final PlayerManager playerManager = PacketEvents.getAPI().getPlayerManager();

        playerManager.sendPacket(
                viewer,
                new WrapperPlayServerDestroyEntities(this.entityId)
        );

        if (this.createTeam) {
            playerManager.sendPacket(
                    viewer,
                    new WrapperPlayServerTeams(
                            teamName,
                            WrapperPlayServerTeams.TeamMode.REMOVE,
                            (WrapperPlayServerTeams.ScoreBoardTeamInfo) null
                    )
            );
        }

    }

    private void sendNameTagVisibility(@NotNull Player viewer) {

        final PlayerManager playerManager = PacketEvents.getAPI().getPlayerManager();

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

        playerManager.sendPacket(
                viewer,
                new WrapperPlayServerTeams(
                        teamName,
                        WrapperPlayServerTeams.TeamMode.CREATE,
                        info,
                        this.target.getName()
                )
        );

    }

}