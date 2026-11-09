package dev.veyra;

import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
import org.lwjgl.system.Configuration;

/** LWJGL 3.3.3 enumerates device extensions on its thread-local stack. */
public final class VeyraLegacyBootstrap implements PreLaunchEntrypoint {
    @Override public void onPreLaunch() {
        Integer configured = Configuration.STACK_SIZE.get();
        if (configured == null || configured < 256) Configuration.STACK_SIZE.set(256);
    }
}
