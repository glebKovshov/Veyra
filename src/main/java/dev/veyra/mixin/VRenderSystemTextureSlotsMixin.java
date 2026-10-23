package dev.veyra.mixin;

import com.mojang.blaze3d.textures.GpuTextureView;
import dev.veyra.vulkan.VeyraTextureSlots;
import net.vulkanmod.vulkan.VRenderSystem;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Arrays;

/** Extends VulkanMod's Minecraft texture-view cache to match Veyra's expanded descriptor slots. */
@Mixin(value = VRenderSystem.class, remap = false)
public abstract class VRenderSystemTextureSlotsMixin {
    @Shadow @Final @Mutable private static GpuTextureView[] shaderTextures;

    @Inject(method = "<clinit>", at = @At("TAIL"), remap = false)
    private static void veyra$expandShaderTextureViews(CallbackInfo callback) {
        shaderTextures = Arrays.copyOf(shaderTextures, VeyraTextureSlots.CAPACITY);
    }
}
