package dev.veyra.shader;

import net.minecraft.client.Minecraft;
import java.util.Map;

final class VeyraRenderOptions {
    private VeyraRenderOptions() { }
    static void publish(Map<String, Double> scalars, Minecraft minecraft) {
        scalars.put("chunkFadeTimeInv", 1.0 / (minecraft.options.chunkSectionFadeInTime().get() * 1000.0));
        var filtering = minecraft.options.textureFiltering().get();
        scalars.put("textureFilteringMode", switch (filtering) {
            case NONE -> 0.0;
            case RGSS -> 1.0;
            case ANISOTROPIC -> 2.0;
        });
        scalars.put("anisotropicFiltering", filtering == net.minecraft.client.TextureFilteringMethod.ANISOTROPIC
                ? (double) minecraft.options.maxAnisotropyValue() : 0.0);
    }
}
