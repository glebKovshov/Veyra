package dev.veyra.smoke.mixin;

import dev.veyra.vulkan.VeyraNoiseTexture;
import net.vulkanmod.render.engine.VkGpuTexture;
import net.vulkanmod.vulkan.shader.Pipeline;
import net.vulkanmod.vulkan.texture.VTextureSelector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Verify terrain's native cache copy cannot replace noisetex with another pipeline's attachment. */
@Mixin(value = VTextureSelector.class, remap = false)
public abstract class GraphicsTerrainTexturesMixin {
    @Inject(method = "bindShaderTextures", at = @At("RETURN"))
    private static void veyra$verifyNoiseBinding(Pipeline pipeline, CallbackInfo ci) {
        var descriptor = pipeline.getImageDescriptor(VeyraNoiseTexture.SAMPLER_NAME);
        if (descriptor != null && descriptor.getImage()
                != ((VkGpuTexture) VeyraNoiseTexture.view().texture()).getVulkanImage())
            throw new IllegalStateException("Native terrain binding replaced noisetex in " + pipeline.name);
    }
}
