package dev.veyra.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.veyra.shader.VeyraRenderer;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererMixin {
    @Inject(method = "renderArmWithItem", at = @At("HEAD"))
    private void veyra$beginHandItemSubmission(AbstractClientPlayer player, float partialTick, float pitch,
                                               InteractionHand hand, float swingProgress, ItemStack stack,
                                               float equipProgress, PoseStack poseStack,
                                               MultiBufferSource bufferSource, int packedLight,
                                               CallbackInfo callback) {
        VeyraRenderer.beginHandItemSubmission(hand == InteractionHand.MAIN_HAND);
    }

    @Inject(method = "renderArmWithItem", at = @At("RETURN"))
    private void veyra$endHandItemSubmission(AbstractClientPlayer player, float partialTick, float pitch,
                                             InteractionHand hand, float swingProgress, ItemStack stack,
                                             float equipProgress, PoseStack poseStack,
                                             MultiBufferSource bufferSource, int packedLight,
                                             CallbackInfo callback) {
        VeyraRenderer.endHandItemSubmission();
    }
}
