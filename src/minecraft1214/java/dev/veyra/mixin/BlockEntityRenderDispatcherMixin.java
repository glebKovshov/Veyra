package dev.veyra.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.veyra.shader.VeyraRenderer;
import dev.veyra.shader.VeyraShadowRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(BlockEntityRenderDispatcher.class)
public abstract class BlockEntityRenderDispatcherMixin {
    @Redirect(method = "setupAndRender", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/renderer/blockentity/BlockEntityRenderer;render(Lnet/minecraft/world/level/block/entity/BlockEntity;F"
            + "Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V"))
    private static <T extends BlockEntity> void veyra$renderBlockEntity(BlockEntityRenderer<T> renderer, T entity,
            float partialTick, PoseStack poses, MultiBufferSource buffers, int light, int overlay) {
        if (VeyraShadowRenderer.isRendering() && !VeyraShadowRenderer.shouldRenderBlockEntity(entity.getBlockPos())) return;
        VeyraRenderer.beginBlockEntitySubmission(entity.getBlockState());
        try { renderer.render(entity, partialTick, poses, buffers, light, overlay); }
        finally { VeyraRenderer.endBlockEntitySubmission(); }
    }
}
