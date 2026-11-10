package dev.veyra.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.veyra.shader.VeyraRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;

/** Minecraft 1.21.3 renders items directly from their stack; later versions go through render states. */
@Mixin(ItemRenderer.class)
public abstract class LegacyItemRendererMixin {
    @WrapMethod(method = "render(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Z"
            + "Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II"
            + "Lnet/minecraft/client/resources/model/BakedModel;)V")
    private void veyra$renderItem(ItemStack stack, ItemDisplayContext context, boolean leftHand, PoseStack poses,
            MultiBufferSource buffers, int light, int overlay, BakedModel model, Operation<Void> original) {
        VeyraRenderer.beginRenderedItemSubmission(stack);
        try {
            original.call(stack, context, leftHand, poses, buffers, light, overlay, model);
        } finally {
            VeyraRenderer.endRenderedItemSubmission();
        }
    }
}
