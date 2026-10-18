package dev.veyra.smoke.mixin;

import dev.veyra.smoke.GraphicsSmoke;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Named development runtime only; this test mod is not remapped or packaged for release. */
@Mixin(value = Minecraft.class, remap = false)
public abstract class GraphicsSmokeMixin {
    @Inject(method = "tick", at = @At("TAIL"), remap = false)
    private void veyra$graphicsSmoke(CallbackInfo ci) { GraphicsSmoke.tick(Minecraft.getInstance()); }
}
