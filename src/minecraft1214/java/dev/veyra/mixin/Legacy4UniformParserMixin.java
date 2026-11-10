package dev.veyra.mixin;

import dev.veyra.compat.VkGpuDevice;
import java.util.ArrayList;
import java.util.List;
import net.vulkanmod.vulkan.shader.converter.UniformParser;
import net.vulkanmod.vulkan.shader.descriptor.ImageDescriptor;
import net.vulkanmod.vulkan.shader.descriptor.UBO;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Gives Veyra's programs the descriptor layout of the VulkanMod build for Minecraft 1.21.5: no
 * empty loose uniform block, and the vanilla texture slots for Sampler0-7. Vanilla programs keep
 * the 1.21.4 layout their sampler remapping expects.
 */
@Mixin(value = UniformParser.class, remap = false)
public abstract class Legacy4UniformParserMixin {
    @Shadow List<UniformParser.Uniform> globalUniforms;

    @Inject(method = "createUniformsCode", at = @At("HEAD"), cancellable = true)
    private void veyra$omitEmptyBlock(CallbackInfoReturnable<String> callback) {
        if (VkGpuDevice.isCompiling() && globalUniforms.isEmpty()) callback.setReturnValue("");
    }

    @Inject(method = "createUBO", at = @At("HEAD"), cancellable = true)
    private void veyra$noEmptyBlock(CallbackInfoReturnable<UBO> callback) {
        if (VkGpuDevice.isCompiling() && globalUniforms.isEmpty()) callback.setReturnValue(null);
    }

    @Inject(method = "createSamplerList", at = @At("RETURN"), cancellable = true)
    private void veyra$vanillaTextureSlots(CallbackInfoReturnable<List<ImageDescriptor>> callback) {
        if (!VkGpuDevice.isCompiling()) return;
        int offset = globalUniforms.isEmpty() ? 0 : 1;
        List<ImageDescriptor> parsed = callback.getReturnValue();
        List<ImageDescriptor> slotted = new ArrayList<>(parsed.size());
        for (int index = 0; index < parsed.size(); index++) {
            ImageDescriptor descriptor = parsed.get(index);
            int imageIdx = vanillaSlot(descriptor.name);
            slotted.add(new ImageDescriptor(index + offset, descriptor.qualifier, descriptor.name,
                    imageIdx < 0 ? index : imageIdx));
        }
        callback.setReturnValue(slotted);
    }

    private static int vanillaSlot(String name) {
        return switch (name) {
            case "Sampler0", "DiffuseSampler" -> 0;
            case "Sampler1" -> 1;
            case "Sampler2" -> 2;
            case "Sampler3" -> 3;
            case "Sampler4" -> 4;
            case "Sampler5" -> 5;
            case "Sampler6" -> 6;
            case "Sampler7" -> 7;
            default -> -1;
        };
    }
}
