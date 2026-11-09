package dev.veyra.mixin;

import com.mojang.blaze3d.textures.GpuTexture;
import dev.veyra.vulkan.VeyraTextureSlots;
import com.mojang.blaze3d.systems.RenderSystem;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Arrays;

/** Extends the native texture cache to match Veyra's expanded descriptor slots. */
@Mixin(value = RenderSystem.class, remap = false)
public abstract class VRenderSystemTextureSlotsMixin {
    @Shadow @Final @Mutable private static GpuTexture[] shaderTextures;

    @Inject(method = "<clinit>", at = @At("TAIL"), remap = false)
    private static void veyra$expandShaderTextureViews(CallbackInfo callback) {
        shaderTextures = Arrays.copyOf(shaderTextures, VeyraTextureSlots.CAPACITY);
    }
}
