package dev.veyra.shader;

import net.minecraft.client.Minecraft;
import java.util.Map;

final class VeyraRenderOptions {
    private VeyraRenderOptions() { }
    static void publish(Map<String, Double> scalars, Minecraft minecraft) {
        // No section fade, RGSS, or anisotropic option exists in this Minecraft version.
        scalars.put("chunkFadeTimeInv", 1.0);
        scalars.put("textureFilteringMode", 0.0);
        scalars.put("anisotropicFiltering", 0.0);
    }
}
