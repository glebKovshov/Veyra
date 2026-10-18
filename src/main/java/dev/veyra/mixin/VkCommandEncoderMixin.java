package dev.veyra.mixin;

import com.mojang.blaze3d.textures.GpuTextureView;
import dev.veyra.shader.VeyraShadowRenderer;
import net.vulkanmod.render.engine.VkCommandEncoder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(value = VkCommandEncoder.class, remap = false)
public abstract class VkCommandEncoderMixin {
    private static final String CREATE_RENDER_PASS = "createRenderPass(Ljava/util/function/Supplier;"
            + "Lcom/mojang/blaze3d/textures/GpuTextureView;Ljava/util/OptionalInt;"
            + "Lcom/mojang/blaze3d/textures/GpuTextureView;Ljava/util/OptionalDouble;)"
            + "Lcom/mojang/blaze3d/systems/RenderPass;";

    @ModifyVariable(method = CREATE_RENDER_PASS, at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private GpuTextureView veyra$bindShadowColorTarget(GpuTextureView original) {
        return VeyraShadowRenderer.isRenderingFeatures()
                ? VeyraShadowRenderer.shadowColorTextureView() : original;
    }

    @ModifyVariable(method = CREATE_RENDER_PASS, at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private GpuTextureView veyra$bindShadowDepthTarget(GpuTextureView original) {
        return VeyraShadowRenderer.isRenderingFeatures()
                ? VeyraShadowRenderer.shadowDepthTextureView() : original;
    }
}
