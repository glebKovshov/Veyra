package dev.veyra.accessor;

import net.minecraft.world.item.ItemStack;

/** Source stack retained on Minecraft's reusable item render state. */
public interface ItemStackRenderStateAccess {
    ItemStack veyra$getSourceStack();

    void veyra$setSourceStack(ItemStack stack);
}
