package dev.veyra.mixin;

import dev.veyra.mixin.accessor.ItemStackRenderStateAccessor;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemModelResolver.class)
public abstract class ItemModelResolverMixin {
    @Inject(method = "updateForLiving", at = @At("RETURN"))
    private void veyra$rememberLivingItem(ItemStackRenderState renderState, ItemStack stack,
                                          ItemDisplayContext displayContext, LivingEntity entity,
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
                                       ItemDisplayContext displayContext, Level level,
                                       ItemOwner owner, int seed, CallbackInfo ci) {
        rememberSourceStack(renderState, stack);
    }

    private static void rememberSourceStack(ItemStackRenderState renderState, ItemStack stack) {
        ((ItemStackRenderStateAccessor) renderState).veyra$setSourceStack(stack);
    }
}
