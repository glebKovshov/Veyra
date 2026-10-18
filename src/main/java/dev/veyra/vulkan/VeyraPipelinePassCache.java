package dev.veyra.vulkan;

import net.vulkanmod.vulkan.framebuffer.RenderPass;
import java.util.Set;

public interface VeyraPipelinePassCache {
    /** Called only by VulkanMod's completed-frame cleanup queue. */
    void veyra$retireRenderPasses(Set<RenderPass> passes);
}
