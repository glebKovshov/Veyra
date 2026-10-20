package dev.veyra.vulkan;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.resources.Identifier;
import net.vulkanmod.interfaces.shader.ExtendedRenderPipeline;
import net.vulkanmod.vulkan.shader.GraphicsPipeline;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Set;

/** Ownership of synthetic pipelines, with destruction deferred through VulkanMod's frame queue. */
public final class VeyraPipelineResources {
    private static final Set<RenderPipeline> owned = Collections.newSetFromMap(new IdentityHashMap<>());
    private VeyraPipelineResources() { }
    public static void track(RenderPipeline pipeline) { owned.add(pipeline); }

    public static void retain(Set<RenderPipeline> live) {
        Set<GraphicsPipeline> liveNative = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<GraphicsPipeline> retired = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<Identifier> shaders = new HashSet<>();
        for (RenderPipeline pipeline : live) {
            shaders.add(pipeline.getVertexShader()); shaders.add(pipeline.getFragmentShader());
            GraphicsPipeline nativePipeline = ExtendedRenderPipeline.of(pipeline).getPipeline();
            if (nativePipeline != null) liveNative.add(nativePipeline);
        }
        owned.removeIf(pipeline -> {
            if (live.contains(pipeline)) return false;
            ExtendedRenderPipeline extended = ExtendedRenderPipeline.of(pipeline);
            GraphicsPipeline nativePipeline = extended.getPipeline();
            if (nativePipeline != null && !liveNative.contains(nativePipeline)) retired.add(nativePipeline);
            extended.setPipeline(null);
            extended.setProgram(null);
            return true;
        });
        retired.forEach(pipeline -> {
            VeyraGeometryTargets.unregister(pipeline);
            pipeline.scheduleCleanUp();
        });
        if (RenderSystem.getDevice() instanceof VeyraShaderCache cache) cache.veyra$pruneShaderSources(shaders);
    }
}
