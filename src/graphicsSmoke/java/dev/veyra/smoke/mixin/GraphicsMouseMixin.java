package dev.veyra.smoke.mixin;

import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Unattended validation must leave the desktop cursor free. */
@Mixin(value = MouseHandler.class, remap = false)
public abstract class GraphicsMouseMixin {
    @Inject(method = "grabMouse", at = @At("HEAD"), cancellable = true, remap = false)
    private void veyra$keepCursorFree(CallbackInfo callback) { callback.cancel(); }
}
