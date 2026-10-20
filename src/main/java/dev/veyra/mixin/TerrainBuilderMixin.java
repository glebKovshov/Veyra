package dev.veyra.mixin;

import dev.veyra.shader.TerrainMaterialContext;
import net.minecraft.world.level.block.state.BlockState;
import net.vulkanmod.render.vertex.TerrainBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = TerrainBuilder.class, remap = false)
public abstract class TerrainBuilderMixin {
    @Inject(method = "setBlockAttributes", at = @At("HEAD"), remap = false)
    private void veyra$setBlockMaterial(BlockState state, CallbackInfo ci) {
        TerrainMaterialContext.setBlock(state);
    }
}
