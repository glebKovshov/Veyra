package dev.veyra.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.veyra.mixin.accessor.ItemLayerRenderStateAccessor;
import dev.veyra.mixin.accessor.ItemStackRenderStateAccessor;
import dev.veyra.shader.VeyraRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemStackRenderState.class)
public abstract class ItemStackRenderStateMixin implements ItemStackRenderStateAccessor {
    @Unique
    private ItemStack veyra$sourceStack = ItemStack.EMPTY;

    @Override
    public ItemStack veyra$getSourceStack() {
        return veyra$sourceStack;
    }

    @Override
    public void veyra$setSourceStack(ItemStack stack) {
        veyra$sourceStack = stack == null ? ItemStack.EMPTY : stack;
    }

    @Inject(method = "clear", at = @At("RETURN"))
    private void veyra$clearSourceStack(CallbackInfo ci) {
        veyra$sourceStack = ItemStack.EMPTY;
    }

    @Redirect(method = "submit", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/renderer/item/ItemStackRenderState$LayerRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V"))
    private void veyra$submitItemLayer(ItemStackRenderState.LayerRenderState layer,
                                       PoseStack poseStack, SubmitNodeCollector collector,
                                       int packedLight, int packedOverlay, int outlineColor) {
        VeyraRenderer.beginRenderedItemSubmission(veyra$sourceStack);
        try {
            ((ItemLayerRenderStateAccessor) (Object) layer)
                    .veyra$submit(poseStack, collector, packedLight, packedOverlay, outlineColor);
        } finally {
            VeyraRenderer.endRenderedItemSubmission();
        }
    }
}
