package dev.veyra.shader;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.ResourceHandle;
import dev.veyra.mixin.accessor.MinecraftAccessor;
import dev.veyra.mixin.accessor.ShaderManagerAccessor;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.CachedOrthoProjectionMatrixBuffer;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.resources.Identifier;

final class VeyraPostProjection {
    private static CachedOrthoProjectionMatrixBuffer buffer;
    private VeyraPostProjection() { }

    static GpuBufferSlice frameInfo(int width, int height) {
        if (buffer == null) {
            var manager = ((MinecraftAccessor) Minecraft.getInstance()).veyra$getShaderManager();
            buffer = ((ShaderManagerAccessor) manager).veyra$getPostChainProjectionMatrixBuffer();
        }
        return buffer.getBuffer(width, height);
    }

    static void addToFrame(PostPass pass, FrameGraphBuilder graph,
                           Map<Identifier, ResourceHandle<RenderTarget>> targets, GpuBufferSlice frameInfo) {
        pass.addToFrame(graph, targets, frameInfo);
    }

    static void reset() { buffer = null; }
}
