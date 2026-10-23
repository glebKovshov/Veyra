package dev.veyra.mixin;

import dev.veyra.shader.TerrainMaterialContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.vulkanmod.render.chunk.build.renderer.BlockRenderer;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = BlockRenderer.class, remap = false)
public abstract class TerrainBlockRendererMixin {
    @Inject(method = "renderBlock", at = @At("HEAD"), remap = false)
    private void veyra$beginBlock(BlockState state, BlockPos pos, Vector3f localOrigin, CallbackInfo ci) {
        // Capture before VulkanMod adds model offsets to this mutable vector.
        TerrainMaterialContext.setOrigin(localOrigin.x, localOrigin.y, localOrigin.z);
    }

    @Inject(method = "renderBlock", at = @At("RETURN"), remap = false)
    private void veyra$endBlock(BlockState state, BlockPos pos, Vector3f localOrigin, CallbackInfo ci) {
        TerrainMaterialContext.clear();
    }
}
