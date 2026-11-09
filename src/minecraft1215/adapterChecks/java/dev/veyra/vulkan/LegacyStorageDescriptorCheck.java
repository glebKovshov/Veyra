package dev.veyra.vulkan;

import java.nio.IntBuffer;
import java.util.List;
import net.vulkanmod.vulkan.shader.descriptor.ImageDescriptor;
import net.vulkanmod.vulkan.shader.descriptor.UBO;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkDescriptorBufferInfo;
import org.lwjgl.vulkan.VkDescriptorPoolSize;
import org.lwjgl.vulkan.VkWriteDescriptorSet;
import static org.lwjgl.vulkan.VK10.*;

final class LegacyStorageDescriptorCheck {
    static void run() {
        var owner = VeyraStorageDescriptor.createBuffer("storage-check",
                VK_BUFFER_USAGE_STORAGE_BUFFER_BIT | VK_BUFFER_USAGE_TRANSFER_DST_BIT, null);
        assert VeyraStorageDescriptor.bufferName(owner).equals("storage-check");
        assert VeyraStorageDescriptor.bufferName(new net.vulkanmod.vulkan.memory.buffer.Buffer(
                VK_BUFFER_USAGE_STORAGE_BUFFER_BIT, null)).equals("Unnamed native buffer");
        owner.setBufferSize(256);
        owner.setId(0x1234);
        var storage = new VeyraStorageDescriptor("storage-check", 64, VK_SHADER_STAGE_COMPUTE_BIT, owner, 128);
        assert storage.getType() == VK_DESCRIPTOR_TYPE_STORAGE_BUFFER_DYNAMIC && storage.getSize() == 128;
        assert storage.getUniformBuffer() == null && storage.bufferId() == owner.getId();
        VeyraUniformBlocks.useGlobal(storage);
        assert storage.bufferId() == 0x1234 : "Global uniform selection replaced an owned storage buffer";
        IntBuffer offsets = IntBuffer.wrap(new int[]{111, 222});
        var second = new VeyraStorageDescriptor("second", 66, VK_SHADER_STAGE_FRAGMENT_BIT, owner, 64);
        LegacyDescriptorBuffers.updateUniforms(LegacyDescriptorBuffers.ordered(List.of(second, storage)), offsets, null);
        assert offsets.get(0) == 0 && offsets.get(1) == 0 && offsets.position() == 0;
        // A null frame allocator proves this path neither allocates nor uploads uniform data for SSBOs.
        owner.setId(0x5678);
        assert storage.bufferId() == 0x5678 && second.bufferId() == 0x5678;
        UBO ordinary = new UBO(0, VK_SHADER_STAGE_VERTEX_BIT, 16, List.of());
        var sampled = new ImageDescriptor(1, "sampler2D", "sampled", 0);
        var image = new ImageDescriptor(2, "image2D", "storageImage", 1, true);
        try (var stack = MemoryStack.stackPush()) {
            var sizes = VkDescriptorPoolSize.calloc(4, stack);
            int[] expected = {VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER_DYNAMIC, VK_DESCRIPTOR_TYPE_STORAGE_BUFFER_DYNAMIC,
                    VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER, VK_DESCRIPTOR_TYPE_STORAGE_IMAGE};
            for (int index = 0; index < sizes.capacity(); index++) {
                sizes.get(index).type(LegacyDescriptorBuffers.poolType(List.of(ordinary, storage), List.of(sampled, image), index))
                        .descriptorCount(10);
                assert sizes.get(index).type() == expected[index] && sizes.get(index).descriptorCount() == 10;
            }
            var info = VkDescriptorBufferInfo.calloc(1, stack).buffer(storage.bufferId()).range(storage.getSize());
            var write = VkWriteDescriptorSet.calloc(stack).sType$Default().dstBinding(storage.getBinding())
                    .descriptorType(storage.getType()).descriptorCount(1).pBufferInfo(info);
            assert info.buffer() == 0x5678 && info.offset() == 0 && info.range() == 128;
            assert write.descriptorType() == VK_DESCRIPTOR_TYPE_STORAGE_BUFFER_DYNAMIC && write.dstBinding() == 64;
        }
        for (int range : new int[]{-1, 0, 257}) {
            try { new VeyraStorageDescriptor("invalid", 64, VK_SHADER_STAGE_COMPUTE_BIT, owner, range); throw new AssertionError("Invalid storage range accepted"); }
            catch (IllegalArgumentException expected) { }
        }
        var uniformOnly = VeyraStorageDescriptor.createBuffer("invalid-usage", VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT, null);
        uniformOnly.setBufferSize(256);
        try { new VeyraStorageDescriptor("invalid", 64, VK_SHADER_STAGE_COMPUTE_BIT, uniformOnly, 128); throw new AssertionError("Wrong buffer usage accepted"); }
        catch (IllegalArgumentException expected) { }
        owner.setId(0);
        try { storage.bufferId(); throw new AssertionError("Unallocated buffer handle accepted"); }
        catch (IllegalStateException expected) { }
        var vector = VeyraUniformBlocks.field("ivec2", "atlasSize");
        assert vector.size == 2 && vector.align == 2;
        System.out.println("Legacy SSBO handles, ranges, zero dynamic offsets and mixed descriptor pool types passed");
    }
}
