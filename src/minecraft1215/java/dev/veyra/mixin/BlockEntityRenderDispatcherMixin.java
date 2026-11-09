package dev.veyra.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.veyra.shader.VeyraRenderer;
import dev.veyra.shader.VeyraShadowRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import static dev.veyra.vulkan.LegacyRenderHooks.BLOCK_ENTITY_RENDER;

@Mixin(BlockEntityRenderDispatcher.class)
public abstract class BlockEntityRenderDispatcherMixin {
    @Redirect(method = "setupAndRender", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/renderer/blockentity/BlockEntityRenderer;" + BLOCK_ENTITY_RENDER))
    private static <T extends BlockEntity> void veyra$renderBlockEntity(BlockEntityRenderer<T> renderer, T entity,
            float partialTick, PoseStack poses, MultiBufferSource buffers, int light, int overlay, Vec3 camera) {
        if (VeyraShadowRenderer.isRendering() && !VeyraShadowRenderer.shouldRenderBlockEntity(entity.getBlockPos())) return;
        VeyraRenderer.beginBlockEntitySubmission(entity.getBlockState());
        try { renderer.render(entity, partialTick, poses, buffers, light, overlay, camera); }
        finally { VeyraRenderer.endBlockEntitySubmission(); }
    }
}
