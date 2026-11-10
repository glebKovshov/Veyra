package dev.veyra.mixin;

import dev.veyra.compat.ShaderSources;
import dev.veyra.shader.VeyraRenderer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.server.packs.resources.ResourceProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Minecraft 1.21.1 has no shader manager; the game renderer reloads the core shaders itself. */
@Mixin(GameRenderer.class)
public abstract class ShaderManagerMixin {
    @Inject(method = "reloadShaders(Lnet/minecraft/server/packs/resources/ResourceProvider;)V", at = @At("TAIL"))
    private void veyra$invalidatePipelines(ResourceProvider resources, CallbackInfo callback) {
        ShaderSources.reloaded();
        VeyraRenderer.invalidatePipelines();
    }
}
