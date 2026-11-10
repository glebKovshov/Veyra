package dev.veyra.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.veyra.shader.VeyraRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Minecraft 1.21.4 render states carry no entity type; it is taken from the entity being drawn. */
@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {
    private static final String RENDER = "render(Lnet/minecraft/world/entity/Entity;DDDF"
            + "Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I"
            + "Lnet/minecraft/client/renderer/entity/EntityRenderer;)V";
    @Unique private EntityType<?> veyra$entityType;

    @Inject(method = RENDER, at = @At("HEAD"))
    private void veyra$rememberEntityType(Entity entity, double x, double y, double z, float partialTick,
            PoseStack poses, MultiBufferSource buffers, int light, EntityRenderer<?, ?> renderer, CallbackInfo callback) {
        veyra$entityType = entity.getType();
    }

    @Redirect(method = RENDER, at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/renderer/entity/EntityRenderer;render(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;"
            + "Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"))
    private <S extends EntityRenderState> void veyra$renderEntity(EntityRenderer<?, S> renderer, S state,
            PoseStack poses, MultiBufferSource buffers, int light) {
        VeyraRenderer.beginEntitySubmission(veyra$entityType);
        try { renderer.render(state, poses, buffers, light); }
        finally { VeyraRenderer.endEntitySubmission(); }
    }
}
