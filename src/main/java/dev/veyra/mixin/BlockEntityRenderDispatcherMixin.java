package dev.veyra.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.veyra.shader.VeyraRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(BlockEntityRenderDispatcher.class)
public abstract class BlockEntityRenderDispatcherMixin {
    @Redirect(method = "submit", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/renderer/blockentity/BlockEntityRenderer;submit(Lnet/minecraft/client/renderer/blockentity/state/BlockEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/CameraRenderState;)V"))
    private <S extends BlockEntityRenderState> void veyra$submitBlockEntityNodes(
            BlockEntityRenderer<?, S> renderer, S state, PoseStack poseStack,
            SubmitNodeCollector collector, CameraRenderState cameraRenderState) {
        VeyraRenderer.beginBlockEntitySubmission();
        try {
            renderer.submit(state, poseStack, collector, cameraRenderState);
        } finally {
            VeyraRenderer.endBlockEntitySubmission();
        }
    }
}
