package dev.veyra.mixin.accessor;

import net.minecraft.client.renderer.CachedOrthoProjectionMatrixBuffer;
import net.minecraft.client.renderer.ShaderManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ShaderManager.class)
public interface ShaderManagerAccessor {
    @Accessor("postChainProjectionMatrixBuffer")
    CachedOrthoProjectionMatrixBuffer veyra$getPostChainProjectionMatrixBuffer();
}
