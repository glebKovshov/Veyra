package dev.veyra.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.veyra.shader.VeyraRenderer;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import static dev.veyra.vulkan.LegacyRenderHooks.RENDER_ARM_WITH_ITEM;

@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererMixin {
    @WrapMethod(method = RENDER_ARM_WITH_ITEM)
    private void veyra$renderHandItem(AbstractClientPlayer player, float partialTick, float pitch,
            InteractionHand hand, float swing, ItemStack stack, float equip, PoseStack poses,
            MultiBufferSource buffers, int light, Operation<Void> original) {
        VeyraRenderer.beginHandItemSubmission(hand == InteractionHand.MAIN_HAND);
        try { original.call(player, partialTick, pitch, hand, swing, stack, equip, poses, buffers, light); }
        finally { VeyraRenderer.endHandItemSubmission(); }
    }
}
