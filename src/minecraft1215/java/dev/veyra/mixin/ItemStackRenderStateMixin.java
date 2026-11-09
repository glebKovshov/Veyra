package dev.veyra.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.veyra.accessor.ItemStackRenderStateAccess;
import dev.veyra.shader.VeyraRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import static dev.veyra.vulkan.LegacyRenderHooks.ITEM_STACK_RENDER;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemStackRenderState.class)
public abstract class ItemStackRenderStateMixin implements ItemStackRenderStateAccess {
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

    @WrapMethod(method = ITEM_STACK_RENDER)
    private void veyra$renderItemLayers(PoseStack poseStack, MultiBufferSource buffers,
                                       int packedLight, int packedOverlay, Operation<Void> original) {
        VeyraRenderer.beginRenderedItemSubmission(veyra$sourceStack);
        try {
            original.call(poseStack, buffers, packedLight, packedOverlay);
        } finally {
            VeyraRenderer.endRenderedItemSubmission();
        }
    }
}
