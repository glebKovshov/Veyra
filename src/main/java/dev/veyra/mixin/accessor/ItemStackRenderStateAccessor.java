package dev.veyra.mixin.accessor;

import net.minecraft.world.item.ItemStack;

public interface ItemStackRenderStateAccessor {
    ItemStack veyra$getSourceStack();

    void veyra$setSourceStack(ItemStack stack);
}
