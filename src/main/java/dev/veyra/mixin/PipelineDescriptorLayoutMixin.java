package dev.veyra.mixin;

import dev.veyra.shader.VeyraStorageBufferUBO;
import net.vulkanmod.vulkan.Vulkan;
import net.vulkanmod.vulkan.shader.Pipeline;
import net.vulkanmod.vulkan.shader.descriptor.ImageDescriptor;
import net.vulkanmod.vulkan.shader.descriptor.UBO;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkDescriptorSetLayoutBinding;
import org.lwjgl.vulkan.VkDescriptorSetLayoutCreateInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.lwjgl.vulkan.VK10.VK_SUCCESS;
import static org.lwjgl.vulkan.VK10.vkCreateDescriptorSetLayout;

/** Builds a sparse Vulkan descriptor layout for Veyra's reserved shader-pack SSBO bindings. */
@Mixin(value = Pipeline.class, remap = false)
public abstract class PipelineDescriptorLayoutMixin {
    @Shadow protected List<UBO> buffers;
    @Shadow protected List<ImageDescriptor> imageDescriptors;
    @Shadow protected long descriptorSetLayout;

    @Inject(method = "createDescriptorSetLayout", at = @At("HEAD"), cancellable = true, remap = false)
    private void veyra$createSparseStorageBufferLayout(CallbackInfo callback) {
        if (buffers.stream().noneMatch(VeyraStorageBufferUBO.class::isInstance)) return;

        // VulkanMod emits dynamic offsets in buffer-list order. Keep that order equal to binding order.
        buffers = new ArrayList<>(buffers);
        buffers.sort(Comparator.comparingInt(UBO::getBinding));
        Map<Integer, DescriptorBinding> bindings = new TreeMap<>();
        for (UBO buffer : buffers) {
            addBinding(bindings, buffer.getBinding(), buffer.getType(), buffer.getStages(), buffer.name);
        }
        for (ImageDescriptor image : imageDescriptors) {
            addBinding(bindings, image.getBinding(), image.getType(), image.getStages(), image.name);
        }

        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkDescriptorSetLayoutBinding.Buffer layoutBindings =
                    VkDescriptorSetLayoutBinding.calloc(bindings.size(), stack);
            int index = 0;
            for (var entry : bindings.entrySet()) {
                DescriptorBinding descriptor = entry.getValue();
                layoutBindings.get(index++)
                        .binding(entry.getKey())
                        .descriptorCount(1)
                        .descriptorType(descriptor.type())
                        .pImmutableSamplers(null)
                        .stageFlags(descriptor.stages());
            }
            VkDescriptorSetLayoutCreateInfo layoutInfo = VkDescriptorSetLayoutCreateInfo.calloc(stack)
                    .sType$Default()
                    .pBindings(layoutBindings);
            LongBuffer layout = stack.mallocLong(1);
            int result = vkCreateDescriptorSetLayout(Vulkan.getVkDevice(), layoutInfo, null, layout);
            if (result != VK_SUCCESS) {
                throw new IllegalStateException("Could not create VulkanMod descriptor layout for shader-pack storage buffers: "
                        + result);
            }
            descriptorSetLayout = layout.get(0);
        }
        callback.cancel();
    }

    private static void addBinding(Map<Integer, DescriptorBinding> bindings, int binding, int type, int stages, String name) {
        DescriptorBinding previous = bindings.putIfAbsent(binding, new DescriptorBinding(type, stages));
        if (previous != null) {
            throw new IllegalStateException("Vulkan descriptor binding " + binding
                    + " is declared more than once (including '" + name + "').");
        }
    }

    private record DescriptorBinding(int type, int stages) { }
}
