package dev.veyra.mixin;

import dev.veyra.compat.LegacyDraws;
import net.vulkanmod.vulkan.pass.DefaultMainPass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = DefaultMainPass.class, remap = false)
public abstract class LegacyMainTargetBindMixin {
    @Inject(method = "rebindMainTarget", at = @At("HEAD"), cancellable = true)
    private void veyra$keepBoundTarget(CallbackInfo callback) {
        if (LegacyDraws.keepsBoundTarget()) callback.cancel();
    }
}
