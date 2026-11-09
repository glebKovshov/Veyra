package dev.veyra.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.veyra.shader.VeyraRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import static dev.veyra.vulkan.LegacyRenderHooks.ENTITY_RENDER;

@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {
    @Redirect(method = "render(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;DDD"
            + "Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I"
            + "Lnet/minecraft/client/renderer/entity/EntityRenderer;)V", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/renderer/entity/EntityRenderer;" + ENTITY_RENDER))
    private <S extends EntityRenderState> void veyra$renderEntity(EntityRenderer<?, S> renderer, S state,
            PoseStack poses, MultiBufferSource buffers, int light) {
        VeyraRenderer.beginEntitySubmission(state.entityType);
        try { renderer.render(state, poses, buffers, light); }
        finally { VeyraRenderer.endEntitySubmission(); }
    }
}
