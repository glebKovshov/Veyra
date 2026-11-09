package dev.veyra.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.veyra.vulkan.LegacyVertexAttributes;
import net.vulkanmod.vulkan.shader.GraphicsPipeline;
import org.lwjgl.vulkan.VkVertexInputAttributeDescription;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = GraphicsPipeline.class, remap = false)
public abstract class LegacyVertexAttributesMixin {
    @WrapMethod(method = "getAttributeDescriptions")
    private static VkVertexInputAttributeDescription.Buffer veyra$genericAttributes(VertexFormat format,
            Operation<VkVertexInputAttributeDescription.Buffer> original) {
        return LegacyVertexAttributes.describe(format, original::call);
    }
}
