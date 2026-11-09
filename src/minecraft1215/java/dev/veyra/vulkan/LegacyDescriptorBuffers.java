package dev.veyra.vulkan;

import java.nio.IntBuffer;
import java.util.Comparator;
import java.util.List;
import net.vulkanmod.vulkan.memory.buffer.UniformBuffer;
import net.vulkanmod.vulkan.shader.descriptor.ImageDescriptor;
import net.vulkanmod.vulkan.shader.descriptor.UBO;

/** Keeps SSBO handles/ranges separate from the frame uniform allocator. */
public final class LegacyDescriptorBuffers {
    private LegacyDescriptorBuffers() { }
    public static int poolType(List<UBO> buffers, List<ImageDescriptor> images, int index) {
        return index < buffers.size() ? buffers.get(index).getType() : images.get(index - buffers.size()).getType();
    }
    public static List<UBO> ordered(List<UBO> buffers) {
        return buffers.stream().sorted(Comparator.comparingInt(UBO::getBinding)).toList();
    }
    public static void updateUniforms(List<UBO> ordered, IntBuffer offsets, UniformBuffer global) {
        for (int index = 0; index < ordered.size(); index++) {
            UBO block = ordered.get(index);
            if (block instanceof VeyraStorageDescriptor) {
                offsets.put(index, 0);
                continue;
            }
            boolean owned = block.getUniformBuffer() != null;
            UniformBuffer buffer = owned ? block.getUniformBuffer() : global;
            if (buffer == null) throw new IllegalStateException("Frame uniform buffer is unavailable");
            int size = UniformBuffer.getAlignedSize(block.getSize());
            buffer.checkCapacity(size);
            // Resizing can reset usedBytes; descriptors must use the offset of the actual upload.
            offsets.put(index, (int) buffer.getUsedBytes());
            if (!owned) {
                block.update(buffer.getPointer());
                buffer.updateOffset(size);
            }
        }
    }
}
