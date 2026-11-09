package net.vulkanmod.vulkan.shader;

/** Read-only diagnostics for the old protected descriptor-array type. */
public final class GraphicsDescriptorEvidence {
    private GraphicsDescriptorEvidence() { }
    public static Object[] descriptorSets(Pipeline pipeline) {
        return pipeline.descriptorSets;
    }
}
