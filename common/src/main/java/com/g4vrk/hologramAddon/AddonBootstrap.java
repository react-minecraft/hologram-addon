package com.g4vrk.hologramAddon;

import com.g4vrk.react.api.extension.bootstrap.ExtensionBootstrap;
import com.g4vrk.react.api.extension.bootstrap.ExtensionBootstrapContext;
import com.g4vrk.react.api.extension.meta.ExtensionId;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Declares the stable identifier used by other React extensions.
 */
@ApiStatus.Internal
public final class AddonBootstrap implements ExtensionBootstrap {

    @Override
    public void bootstrap(
        final @NotNull ExtensionBootstrapContext context
    ) {

        context.identify(ExtensionId.addon("hologram"), 1);

    }

}
