package dev.veyra.mixin;

import dev.veyra.vulkan.VeyraTextureSlots;
import net.vulkanmod.vulkan.texture.VTextureSelector;
import net.vulkanmod.vulkan.texture.VulkanImage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Arrays;

/** Extends VulkanMod's texture image cache for shaderpacks with more than twelve samplers. */
@Mixin(value = VTextureSelector.class, remap = false)
public abstract class VTextureSelectorMixin {
    @Shadow @Final @Mutable private static VulkanImage[] boundTextures;
    @Shadow @Final @Mutable private static int[] levels;
    @Shadow @Final @Mutable public static int SIZE;

    @Inject(method = "<clinit>", at = @At("TAIL"), remap = false)
    private static void veyra$expandTextureSlots(CallbackInfo callback) {
        boundTextures = Arrays.copyOf(boundTextures, VeyraTextureSlots.CAPACITY);
        levels = Arrays.copyOf(levels, VeyraTextureSlots.CAPACITY);
        SIZE = VeyraTextureSlots.CAPACITY;
    }

    @ModifyConstant(method = "bindTexture(ILnet/vulkanmod/vulkan/texture/VulkanImage;)V",
            constant = @Constant(intValue = 12), remap = false)
    private static int veyra$acceptExpandedTextureSlots(int original) {
        return VeyraTextureSlots.CAPACITY;
    }

    @ModifyConstant(method = "bindTexture(ILnet/vulkanmod/vulkan/texture/VulkanImage;)V",
            constant = @Constant(intValue = 11), remap = false)
    private static int veyra$reportExpandedTextureLimit(int original) {
        return VeyraTextureSlots.CAPACITY - 1;
    }

    @ModifyConstant(method = "setActiveTexture(I)V",
            constant = @Constant(intValue = 12), remap = false)
    private static int veyra$acceptExpandedActiveTextureSlots(int original) {
        return VeyraTextureSlots.CAPACITY;
    }

    @ModifyConstant(method = "setActiveTexture(I)V",
            constant = @Constant(intValue = 11), remap = false)
    private static int veyra$reportExpandedActiveTextureLimit(int original) {
        return VeyraTextureSlots.CAPACITY - 1;
    }
}
