package dev.veyra.reference.mixin;

import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = MouseHandler.class, remap = false)
public abstract class ReferenceMouseMixin {
    @Inject(method = "grabMouse", at = @At("HEAD"), cancellable = true, remap = false)
    private void veyra$keepCursorFree(CallbackInfo callback) { callback.cancel(); }
}
