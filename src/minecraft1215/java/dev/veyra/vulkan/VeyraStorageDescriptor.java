package dev.veyra.vulkan;

import java.util.List;
import net.vulkanmod.vulkan.memory.MemoryType;
import net.vulkanmod.vulkan.memory.buffer.Buffer;
import net.vulkanmod.vulkan.shader.descriptor.UBO;
import static org.lwjgl.vulkan.VK10.*;

/** The descriptor borrows a real storage buffer, without allocating a surrogate uniform buffer. */
public class VeyraStorageDescriptor extends UBO {
    private final Buffer buffer;
    private final int rangeBytes;
    public VeyraStorageDescriptor(String name, int binding, int stages, Buffer buffer, int rangeBytes) {
        super(binding, stages, 16, List.of());
        if (rangeBytes <= 0 || rangeBytes > buffer.getBufferSize() || (buffer.usage & VK_BUFFER_USAGE_STORAGE_BUFFER_BIT) == 0)
            throw new IllegalArgumentException("Invalid shader storage buffer range or usage");
        this.buffer = buffer;
        this.rangeBytes = rangeBytes;
    }
    public static Buffer createBuffer(String name, int usage, MemoryType type) { return new NamedBuffer(name, usage, type); }
    public static String bufferName(Buffer buffer) {
        return buffer instanceof NamedBuffer named ? named.name : "Unnamed native buffer";
    }
    private static final class NamedBuffer extends Buffer {
        private final String name;
        private NamedBuffer(String name, int usage, MemoryType type) {
            super(usage, type);
            this.name = name;
        }
    }
    public long bufferId() {
        long id = buffer.getId();
        if (id == VK_NULL_HANDLE) throw new IllegalStateException("Shader storage buffer is unavailable");
        return id;
    }
    @Override public int getType() { return VK_DESCRIPTOR_TYPE_STORAGE_BUFFER_DYNAMIC; }
    @Override public int getSize() { return rangeBytes; }
}
