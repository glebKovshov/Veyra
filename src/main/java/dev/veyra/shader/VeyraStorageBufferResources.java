package dev.veyra.shader;

import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.device.DeviceManager;
import net.vulkanmod.vulkan.memory.MemoryTypes;
import net.vulkanmod.vulkan.memory.buffer.Buffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkPhysicalDeviceProperties;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import static org.lwjgl.vulkan.VK10.*;

/** Owns the Vulkan buffers and parser metadata for one selected shader-pack configuration. */
public final class VeyraStorageBufferResources implements AutoCloseable {
    private static final int DEVICE_LOCAL_BUDGET_PERCENT = 60;
    private static final AtomicLong NEXT_ID = new AtomicLong(1);
    private static volatile VeyraStorageBufferResources active;

    private final long identity = NEXT_ID.getAndIncrement();
    private final ShaderPackStorageBufferProperties properties;
    private final int viewWidth;
    private final int viewHeight;
    private final Map<Integer, StorageBuffer> buffers = new ConcurrentHashMap<>();
    private final Map<String, ShaderPackStorageBuffers.StorageBlock> blocks = new ConcurrentHashMap<>();
    private long allocatedBytes;
    private boolean closed;

    private VeyraStorageBufferResources(ShaderPackStorageBufferProperties properties, int viewWidth, int viewHeight) {
        this.properties = properties;
        this.viewWidth = viewWidth;
        this.viewHeight = viewHeight;
    }

    public static VeyraStorageBufferResources create(ShaderPackStorageBufferProperties properties,
                                                      int viewWidth, int viewHeight) {
        return new VeyraStorageBufferResources(properties, viewWidth, viewHeight);
    }

    public static VeyraStorageBufferResources active() {
        return active;
    }

    /** Installs the candidate during pipeline compilation and returns the previous active owner. */
    public static VeyraStorageBufferResources activate(VeyraStorageBufferResources next) {
        VeyraStorageBufferResources previous = active;
        active = next;
        return previous;
    }

    public long identity() {
        return identity;
    }

    public int viewWidth() {
        return viewWidth;
    }

    public int viewHeight() {
        return viewHeight;
    }

    public ShaderPackStorageBufferProperties properties() {
        return properties;
    }

    public boolean usesRelativeSizes() {
        return properties.buffers().values().stream().anyMatch(ShaderPackStorageBufferProperties.BufferObject::relative);
    }

    public void registerBlock(ShaderPackStorageBuffers.StorageBlock block) {
        ensureOpen();
        ShaderPackStorageBuffers.StorageBlock previous = blocks.putIfAbsent(block.placeholder(), block);
        if (previous != null && !previous.equals(block)) {
            throw new IllegalStateException("Conflicting shader-pack storage block metadata for " + block.placeholder() + ".");
        }
    }

    public ShaderPackStorageBuffers.StorageBlock block(String placeholder) {
        return blocks.get(placeholder);
    }

    public synchronized StorageBuffer storageBuffer(int binding) throws IOException {
        ensureOpen();
        StorageBuffer existing = buffers.get(binding);
        if (existing != null) return existing;

        ShaderPackStorageBufferProperties.BufferObject property = properties.buffers().get(binding);
        if (property == null) {
            throw new IOException("No bufferObject." + binding + " allocation exists in the selected shader-pack profile.");
        }
        long rangeBytes = property.resolveSize(viewWidth, viewHeight);
        long maxRange = maxStorageBufferRange();
        if (maxRange == 0) {
            throw new IOException("The Vulkan device does not expose a usable maxStorageBufferRange.");
        }
        if (rangeBytes > maxRange) {
            throw new IOException("bufferObject." + binding + " requires " + rangeBytes
                    + " bytes, above this Vulkan device's maxStorageBufferRange of " + maxRange + " bytes.");
        }
        if (MemoryTypes.GPU_MEM == null) {
            throw new IOException("VulkanMod's device-local memory type is unavailable for shader-pack storage buffers.");
        }

        long allocationBytes = (rangeBytes + 3L) & ~3L;
        long budget = deviceLocalBudget();
        if (allocatedBytes + allocationBytes > budget) {
            throw new IOException("Shader-pack storage buffers require " + mebibytes(allocatedBytes + allocationBytes)
                    + " MiB, above Veyra's conservative " + mebibytes(budget) + " MiB device-local buffer budget.");
        }

        Buffer buffer = new Buffer("Veyra bufferObject." + binding,
                VK_BUFFER_USAGE_STORAGE_BUFFER_BIT | VK_BUFFER_USAGE_TRANSFER_DST_BIT, MemoryTypes.GPU_MEM);
        try {
            buffer.createBuffer(allocationBytes);
        } catch (RuntimeException exception) {
            if (buffer.getId() != VK_NULL_HANDLE || buffer.getAllocation() != VK_NULL_HANDLE) buffer.scheduleFree();
            throw new IOException("Could not allocate Vulkan storage buffer for bufferObject." + binding + " ("
                    + mebibytes(rangeBytes) + " MiB).", exception);
        }
        StorageBuffer created = new StorageBuffer(binding, buffer, (int) rangeBytes, allocationBytes);
        buffers.put(binding, created);
        allocatedBytes += allocationBytes;
        return created;
    }

    /** Clears pack SSBOs before shadow writers run and makes the transfer writes visible to shader stages. */
    public void clearForShadowPass() {
        ensureOpen();
        if (buffers.isEmpty()) return;
        VkCommandBuffer commandBuffer = Renderer.getCommandBuffer();
        if (!Renderer.isRecording() || commandBuffer == null) {
            throw new IllegalStateException("Shader-pack storage buffers can only be cleared while VulkanMod records a frame.");
        }
        for (StorageBuffer storageBuffer : buffers.values()) {
            vkCmdFillBuffer(commandBuffer, storageBuffer.buffer().getId(), 0, storageBuffer.allocationBytes(), 0);
        }
        VeyraComputePipeline.memoryBarrier(commandBuffer,
                VK_PIPELINE_STAGE_TRANSFER_BIT,
                VK_PIPELINE_STAGE_ALL_GRAPHICS_BIT | VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT,
                VK_ACCESS_TRANSFER_WRITE_BIT,
                VK_ACCESS_SHADER_READ_BIT | VK_ACCESS_SHADER_WRITE_BIT);
    }

    /** Orders shadow-stage writes before shadow compute and later graphics/post-processing readers. */
    public void barrierAfterShadowPass() {
        ensureOpen();
        if (buffers.isEmpty()) return;
        VkCommandBuffer commandBuffer = Renderer.getCommandBuffer();
        if (!Renderer.isRecording() || commandBuffer == null) {
            throw new IllegalStateException("Shader-pack storage barriers require an active Vulkan frame recording.");
        }
        VeyraComputePipeline.memoryBarrier(commandBuffer,
                VK_PIPELINE_STAGE_ALL_GRAPHICS_BIT,
                VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT | VK_PIPELINE_STAGE_ALL_GRAPHICS_BIT,
                VK_ACCESS_SHADER_WRITE_BIT,
                VK_ACCESS_SHADER_READ_BIT | VK_ACCESS_SHADER_WRITE_BIT);
    }

    public long allocatedBytes() {
        return allocatedBytes;
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;
        if (active == this) active = null;
        buffers.values().forEach(storageBuffer -> storageBuffer.buffer().scheduleFree());
        buffers.clear();
        blocks.clear();
        allocatedBytes = 0;
    }

    private void ensureOpen() {
        if (closed) throw new IllegalStateException("Shader-pack storage-buffer resources have already been released.");
    }

    private static long deviceLocalBudget() throws IOException {
        if (DeviceManager.memoryProperties == null) {
            throw new IOException("Vulkan device memory properties are unavailable for storage-buffer allocation.");
        }
        long largestDeviceLocalHeap = 0;
        var heaps = DeviceManager.memoryProperties.memoryHeaps();
        for (int index = 0; index < DeviceManager.memoryProperties.memoryHeapCount(); index++) {
            var heap = heaps.get(index);
            if ((heap.flags() & VK_MEMORY_HEAP_DEVICE_LOCAL_BIT) != 0) {
                largestDeviceLocalHeap = Math.max(largestDeviceLocalHeap, heap.size());
            }
        }
        if (largestDeviceLocalHeap <= 0) {
            throw new IOException("The selected Vulkan device reports no device-local memory heap.");
        }
        return largestDeviceLocalHeap * DEVICE_LOCAL_BUDGET_PERCENT / 100;
    }

    private static long maxStorageBufferRange() throws IOException {
        if (DeviceManager.physicalDevice == null) {
            throw new IOException("VulkanMod's selected physical device is unavailable for storage-buffer allocation.");
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkPhysicalDeviceProperties deviceProperties = VkPhysicalDeviceProperties.calloc(stack);
            vkGetPhysicalDeviceProperties(DeviceManager.physicalDevice, deviceProperties);
            // Vulkan reports this uint32 limit; VulkanMod's descriptor API itself caps ranges at signed-int size.
            return Integer.toUnsignedLong(deviceProperties.limits().maxStorageBufferRange());
        }
    }

    private static long mebibytes(long bytes) {
        return (bytes + 1024L * 1024L - 1) / (1024L * 1024L);
    }

    public record StorageBuffer(int binding, Buffer buffer, int rangeBytes, long allocationBytes) { }
}
