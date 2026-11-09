package dev.veyra.vulkan;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.GpuDevice;
import java.nio.ByteBuffer;
import java.util.function.Supplier;

public final class VeyraVertexBuffers {
    private VeyraVertexBuffers() { }
    public static GpuBuffer create(GpuDevice device, Supplier<String> label, ByteBuffer vertices) {
        return device.createBuffer(label, GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST, vertices);
    }
}
