package dev.veyra.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.veyra.shader.VeyraRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {
    @Redirect(method = "submit", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/renderer/entity/EntityRenderer;submit(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/CameraRenderState;)V"))
    private <S extends EntityRenderState> void veyra$submitEntityNodes(EntityRenderer<?, S> renderer, S state,
                                        PoseStack poseStack, SubmitNodeCollector collector,
                                        CameraRenderState cameraRenderState) {
        VeyraRenderer.beginEntitySubmission(state.entityType);
        try {
            renderer.submit(state, poseStack, collector, cameraRenderState);
        } finally {
            VeyraRenderer.endEntitySubmission();
        }
    }
}
