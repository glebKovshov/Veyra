package dev.veyra.compat;

import java.util.function.BiFunction;
import net.minecraft.resources.ResourceLocation;

/** The subset of the 1.21.5 device API that Veyra uses on 1.21.1, which has no GPU buffer objects. */
public interface GpuDevice {
    CommandEncoder createCommandEncoder();
    GpuTexture createTexture(String label, TextureFormat format, int width, int height, int mipLevels);
    CompiledRenderPipeline precompilePipeline(RenderPipeline pipeline,
                                              BiFunction<ResourceLocation, ShaderType, String> source);
    int getMaxTextureSize();
}
