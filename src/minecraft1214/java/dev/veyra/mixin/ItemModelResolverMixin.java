package dev.veyra.mixin;

import dev.veyra.accessor.ItemStackRenderStateAccess;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Minecraft 1.21.4 passes the hand side to the living and top-item updates. */
@Mixin(ItemModelResolver.class)
public abstract class ItemModelResolverMixin {
    @Inject(method = "updateForLiving", at = @At("RETURN"))
    private void veyra$rememberLivingItem(ItemStackRenderState renderState, ItemStack stack,
                                          ItemDisplayContext displayContext, boolean leftHand, LivingEntity entity,
                                          CallbackInfo ci) {
        rememberSourceStack(renderState, stack);
    }

    @Inject(method = "updateForNonLiving", at = @At("RETURN"))
    private void veyra$rememberNonLivingItem(ItemStackRenderState renderState, ItemStack stack,
                                            ItemDisplayContext displayContext, Entity entity,
                                            CallbackInfo ci) {
        rememberSourceStack(renderState, stack);
    }

    @Inject(method = "updateForTopItem", at = @At("RETURN"))
    private void veyra$rememberTopItem(ItemStackRenderState renderState, ItemStack stack,
                                       ItemDisplayContext displayContext, boolean leftHand, Level level,
                                       LivingEntity owner, int seed, CallbackInfo ci) {
        rememberSourceStack(renderState, stack);
    }

    private static void rememberSourceStack(ItemStackRenderState renderState, ItemStack stack) {
        ((ItemStackRenderStateAccess) renderState).veyra$setSourceStack(stack);
    }
}
