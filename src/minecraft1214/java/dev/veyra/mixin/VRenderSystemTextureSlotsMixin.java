package dev.veyra.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.veyra.vulkan.VeyraTextureSlots;
import java.util.Arrays;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Extends the native texture id slots to match Veyra's expanded descriptor slots. */
@Mixin(value = RenderSystem.class, remap = false)
public abstract class VRenderSystemTextureSlotsMixin {
    @Shadow @Final @Mutable private static int[] shaderTextures;

    @Inject(method = "<clinit>", at = @At("TAIL"), remap = false)
    private static void veyra$expandShaderTextureSlots(CallbackInfo callback) {
        shaderTextures = Arrays.copyOf(shaderTextures, VeyraTextureSlots.CAPACITY);
    }
}
