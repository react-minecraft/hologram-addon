package com.g4vrk.hologramAddon.manager;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks online player UUIDs eligible to receive hologram updates.
 */
@ApiStatus.Internal
public final class HologramViewerManager {

    private final Set<UUID> receivers = ConcurrentHashMap.newKeySet();

    public boolean add(
        final @NotNull UUID uuid
    ) {

        return this.receivers.add(uuid);

    }

    public boolean remove(
        final @NotNull UUID uuid
    ) {

        return this.receivers.remove(uuid);

    }

    public @NotNull Set<UUID> receivers() {

        return this.receivers;

    }

}
