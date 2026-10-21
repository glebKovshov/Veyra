package dev.veyra.mixin;

import dev.veyra.shader.TerrainMaterialContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.vulkanmod.render.chunk.build.renderer.FluidRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = FluidRenderer.class, remap = false)
public abstract class FluidRendererMixin {
    @Inject(method = "renderLiquid", at = @At("HEAD"), remap = false)
    private void veyra$beginFluidMaterial(BlockState state, FluidState fluidState, BlockPos pos,
                                         CallbackInfo ci) {
        TerrainMaterialContext.setFluid(state);
        TerrainMaterialContext.setOrigin(pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15);
    }

    @Inject(method = "renderLiquid", at = @At("RETURN"), remap = false)
    private void veyra$endFluidMaterial(BlockState state, FluidState fluidState, BlockPos pos,
                                        CallbackInfo ci) {
        TerrainMaterialContext.clear();
    }
}
