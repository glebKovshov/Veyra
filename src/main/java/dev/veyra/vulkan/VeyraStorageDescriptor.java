package dev.veyra.vulkan;

import java.util.List;
import net.vulkanmod.vulkan.memory.MemoryType;
import net.vulkanmod.vulkan.memory.buffer.Buffer;
import net.vulkanmod.vulkan.shader.descriptor.UBO;
import static org.lwjgl.vulkan.VK10.*;

/** Borrows the pack's storage buffer; its resource owner retires it after submitted work. */
public class VeyraStorageDescriptor extends UBO {
    private final int rangeBytes;
    public VeyraStorageDescriptor(String name, int binding, int stages, Buffer buffer, int rangeBytes) {
        super(name, binding, stages, 16, List.of());
        if (rangeBytes <= 0 || rangeBytes > buffer.getBufferSize() || (buffer.usage & VK_BUFFER_USAGE_STORAGE_BUFFER_BIT) == 0)
            throw new IllegalArgumentException("Invalid shader storage buffer range or usage");
        this.rangeBytes = rangeBytes;
        getBufferSlice().set(buffer, 0, rangeBytes);
    }
    public static Buffer createBuffer(String name, int usage, MemoryType type) { return new Buffer(name, usage, type); }
    public static String bufferName(Buffer buffer) { return buffer.name; }
    public long bufferId() { return getBufferSlice().getBuffer().getId(); }
    @Override public int getType() { return VK_DESCRIPTOR_TYPE_STORAGE_BUFFER_DYNAMIC; }
    @Override public int getSize() { return rangeBytes; }
    @Override public void setUseGlobalBuffer(boolean useGlobalBuffer) { super.setUseGlobalBuffer(false); }
}
