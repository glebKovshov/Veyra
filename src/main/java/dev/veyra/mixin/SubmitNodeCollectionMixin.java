package dev.veyra.mixin;

import dev.veyra.shader.VeyraRenderer;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(SubmitNodeCollection.class)
public abstract class SubmitNodeCollectionMixin {
    @ModifyVariable(method = "submitModel", at = @At("HEAD"), argsOnly = true)
    private RenderType veyra$routeBlockModel(RenderType renderType) {
        return VeyraRenderer.routeBlockEntityRenderType(renderType);
    }

    @ModifyVariable(method = "submitModelPart", at = @At("HEAD"), argsOnly = true)
    private RenderType veyra$routeBlockModelPart(RenderType renderType) {
        return VeyraRenderer.routeBlockEntityRenderType(renderType);
    }

    @ModifyVariable(method = "submitBlockModel", at = @At("HEAD"), argsOnly = true)
    private RenderType veyra$routeBlockStateModel(RenderType renderType) {
        return VeyraRenderer.routeBlockEntityRenderType(renderType);
    }

    @ModifyVariable(method = "submitItem", at = @At("HEAD"), argsOnly = true)
    private RenderType veyra$routeBlockItem(RenderType renderType) {
        return VeyraRenderer.routeBlockEntityRenderType(renderType);
    }

    @ModifyVariable(method = "submitCustomGeometry", at = @At("HEAD"), argsOnly = true)
    private RenderType veyra$routeBlockCustomGeometry(RenderType renderType) {
        return VeyraRenderer.routeBlockEntityRenderType(renderType);
    }
}
