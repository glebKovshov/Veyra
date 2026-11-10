package dev.veyra.compat;

import net.vulkanmod.vulkan.shader.GraphicsPipeline;

/** The native pipeline and CPU uniform storage compiled for a pipeline description. */
public interface ExtendedRenderPipeline {
    static ExtendedRenderPipeline of(RenderPipeline pipeline) { return pipeline; }
    GraphicsPipeline getPipeline();
    void setPipeline(GraphicsPipeline pipeline);
    EGlProgram getProgram();
    void setProgram(EGlProgram program);
}
