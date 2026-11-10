package dev.veyra.mixin;

import net.vulkanmod.vulkan.shader.Uniforms;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Minecraft 1.21.1 has one fog range; the separate environmental, render-distance, sky and cloud
 * ranges arrived in 1.21.6. Shared shaders use the later names, which read the single range here.
 */
@Mixin(value = Uniforms.class, remap = false)
public abstract class LegacyFogUniformsMixin {
    @Inject(method = "setupDefaultUniforms", at = @At("TAIL"), remap = false)
    private static void veyra$aliasFogRange(CallbackInfo callback) {
        var start = Uniforms.vec1f_uniformMap.get("FogStart");
        var end = Uniforms.vec1f_uniformMap.get("FogEnd");
        if (start == null || end == null) throw new IllegalStateException("VulkanMod no longer supplies FogStart and FogEnd");
        for (String name : new String[]{"FogEnvironmentalStart", "FogRenderDistanceStart"}) {
            Uniforms.vec1f_uniformMap.putIfAbsent(name, start);
        }
        for (String name : new String[]{"FogEnvironmentalEnd", "FogRenderDistanceEnd", "FogSkyEnd", "FogCloudsEnd"}) {
            Uniforms.vec1f_uniformMap.putIfAbsent(name, end);
        }
    }
}
