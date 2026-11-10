package dev.veyra.compat;

import com.mojang.blaze3d.buffers.BufferType;
import com.mojang.blaze3d.buffers.BufferUsage;
import com.mojang.blaze3d.buffers.GpuBuffer;
import java.nio.ByteBuffer;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import net.minecraft.resources.ResourceLocation;

/** The subset of the 1.21.5 device API that Veyra uses. */
public interface GpuDevice {
    CommandEncoder createCommandEncoder();
    GpuTexture createTexture(String label, TextureFormat format, int width, int height, int mipLevels);
    GpuBuffer createBuffer(Supplier<String> label, BufferType type, BufferUsage usage, ByteBuffer data);
    CompiledRenderPipeline precompilePipeline(RenderPipeline pipeline,
                                              BiFunction<ResourceLocation, ShaderType, String> source);
    int getMaxTextureSize();
}
