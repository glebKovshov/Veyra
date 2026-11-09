package dev.veyra.vulkan;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.List;
import net.vulkanmod.vulkan.device.DeviceManager;
import net.vulkanmod.vulkan.memory.buffer.UniformBuffer;
import net.vulkanmod.vulkan.util.MappedBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VkPhysicalDeviceLimits;
import org.lwjgl.vulkan.VkPhysicalDeviceProperties;
import static org.lwjgl.vulkan.VK10.*;

final class LegacyUniformUploadCheck {
    static void run() {
        var previous = DeviceManager.deviceProperties;
        // Unit-only device alignment; this JVM never creates a Vulkan device.
        try (var properties = VkPhysicalDeviceProperties.calloc(); var stack = MemoryStack.stackPush()) {
            MemoryUtil.memPutLong(properties.limits().address() + VkPhysicalDeviceLimits.MINUNIFORMBUFFEROFFSETALIGNMENT, 256);
            DeviceManager.deviceProperties = properties;
            var globalBytes = stack.calloc(4096);
            var ownedBytes = stack.calloc(1024);
            var global = new CpuUniformBuffer(globalBytes, 256, 256);
            var owned = new CpuUniformBuffer(ownedBytes, 1024, 256);
            var first = VeyraUniformBlocks.create("first", 0, VK_SHADER_STAGE_VERTEX_BIT,
                    List.of(VeyraUniformBlocks.field("float", "first")));
            var second = VeyraUniformBlocks.create("second", 3, VK_SHADER_STAGE_FRAGMENT_BIT,
                    List.of(VeyraUniformBlocks.field("float", "second")));
            var borrowed = VeyraUniformBlocks.create("owned", 2, VK_SHADER_STAGE_VERTEX_BIT,
                    List.of(VeyraUniformBlocks.field("float", "owned")));
            borrowed.setUniformBuffer(owned);
            var sourceA = MappedBuffer.createFromBuffer(stack.calloc(4).putFloat(0, 1.5F));
            var sourceB = MappedBuffer.createFromBuffer(stack.calloc(4).putFloat(0, 2.5F));
            first.getUniforms().getFirst().setSupplier(() -> sourceA);
            second.getUniforms().getFirst().setSupplier(() -> sourceB);
            ownedBytes.putFloat(256, 9.5F);
            var storageBuffer = VeyraStorageDescriptor.createBuffer("storage", VK_BUFFER_USAGE_STORAGE_BUFFER_BIT, null);
            storageBuffer.setBufferSize(128); storageBuffer.setId(0x55);
            var storage = new VeyraStorageDescriptor("storage", 64, VK_SHADER_STAGE_COMPUTE_BIT, storageBuffer, 128);
            IntBuffer offsets = IntBuffer.allocate(4);
            LegacyDescriptorBuffers.updateUniforms(LegacyDescriptorBuffers.ordered(List.of(storage, second, borrowed, first)), offsets, global);
            assert offsets.get(0) == 0 : "Resize retained an offset from the previous allocation";
            assert offsets.get(1) == 256 && offsets.get(2) == 256 && offsets.get(3) == 0;
            assert global.resizes == 1 && global.getUsedBytes() == 512;
            assert globalBytes.getFloat(0) == 1.5F && globalBytes.getFloat(256) == 2.5F;
            assert owned.getUsedBytes() == 256 && ownedBytes.getFloat(256) == 9.5F;
            assert storage.bufferId() == 0x55;
        } finally { DeviceManager.deviceProperties = previous; }
        System.out.println("Legacy mixed UBO/SSBO upload preserves binding order, resize offsets and owned data");
    }

    private static final class CpuUniformBuffer extends UniformBuffer {
        private int resizes;
        CpuUniformBuffer(ByteBuffer data, long capacity, long used) {
            super(0, null);
            dataPtr = MemoryUtil.memAddress(data);
            bufferSize = capacity;
            usedBytes = used;
        }
        @Override public void createBuffer(long size) { bufferSize = size; id = 0x11; }
        @Override public void resizeBuffer(long size) { bufferSize = size; usedBytes = 0; id++; resizes++; }
    }
}
