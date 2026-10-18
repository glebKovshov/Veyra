package dev.veyra.vulkan;

import dev.veyra.shader.FragmentOutputs;
import dev.veyra.shader.VeyraRenderer;
import dev.veyra.mixin.accessor.RendererFramebufferAccessor;
import org.lwjgl.system.MemoryStack;
import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.framebuffer.Framebuffer;
import net.vulkanmod.vulkan.framebuffer.RenderPass;
import net.vulkanmod.vulkan.shader.GraphicsPipeline;
import net.vulkanmod.vulkan.texture.VulkanImage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import static org.lwjgl.vulkan.VK10.*;

/** Routes registered geometry programs without changing vanilla/offscreen pipeline targets. */
public final class VeyraGeometryTargets {
    private static final Map<GraphicsPipeline, FragmentOutputs> layouts = new IdentityHashMap<>();
    private static final Map<Key, VeyraMrtFramebuffer> framebuffers = new HashMap<>();
    private static final Map<Framebuffer, RenderPass> resumePasses = new IdentityHashMap<>();
    private static Framebuffer scene;
    private VeyraGeometryTargets() { }

    public static void register(GraphicsPipeline pipeline, FragmentOutputs layout) {
        if (layout.targets().size() > VeyraDeviceFeatures.maxColorAttachments)
            throw new IllegalStateException("This geometry program exceeds the Vulkan device's color attachment limit.");
        if (!VeyraDeviceFeatures.independentBlendEnabled && layout.variables().size() != layout.targets().size())
            throw new IllegalStateException("Sparse geometry output masks require Vulkan independentBlend support.");
        if (!layout.sceneOnly()) layouts.put(pipeline, layout);
    }
    public static FragmentOutputs layout(GraphicsPipeline pipeline) { return layouts.get(pipeline); }
    public static void unregister(GraphicsPipeline pipeline) { layouts.remove(pipeline); }

    public static void beforeBind(GraphicsPipeline pipeline) {
        Renderer renderer = Renderer.getInstance();
        FragmentOutputs layout = layouts.get(pipeline);
        Framebuffer bound = renderer.getBoundFramebuffer();
        if (layout == null) {
            if (bound instanceof VeyraMrtFramebuffer) resumeScene(renderer);
            return;
        }
        if (!(bound instanceof VeyraMrtFramebuffer)) scene = bound;
        if (scene == null) throw new IllegalStateException("Geometry color targets require an active scene render pass.");
        List<VulkanImage> colors = new ArrayList<>();
        for (int target : layout.targets()) colors.add(target == 0 ? scene.getColorAttachment() : VeyraRenderer.geometryColorAttachment(target));
        Key key = new Key(List.copyOf(colors), scene.getDepthAttachment());
        VeyraMrtFramebuffer framebuffer = framebuffers.computeIfAbsent(key,
                ignored -> new VeyraMrtFramebuffer(key.colors(), key.depth()));
        beginPreservingDynamicState(renderer, framebuffer.renderPass(), framebuffer);
    }

    private static void resumeScene(Renderer renderer) {
        if (scene == null) throw new IllegalStateException("The scene framebuffer was lost while rendering geometry MRT.");
        RenderPass pass = resumePasses.computeIfAbsent(scene, target -> {
            var builder = RenderPass.builder(target).setLoadOp(VK_ATTACHMENT_LOAD_OP_LOAD);
            if (builder.getColorAttachmentInfo() != null)
                builder.getColorAttachmentInfo().setOps(VK_ATTACHMENT_LOAD_OP_LOAD, VK_ATTACHMENT_STORE_OP_STORE);
            if (builder.getDepthAttachmentInfo() != null)
                builder.getDepthAttachmentInfo().setOps(VK_ATTACHMENT_LOAD_OP_LOAD, VK_ATTACHMENT_STORE_OP_STORE);
            return builder.build();
        });
        beginPreservingDynamicState(renderer, pass, scene);
    }

    private static void beginPreservingDynamicState(Renderer renderer, RenderPass pass, Framebuffer target) {
        if (renderer.getBoundFramebuffer() == target) return;
        // This runs after VkCommandEncoder has applied the draw's viewport/scissor. The normal
        // beginRenderPass API resets both to the full framebuffer and would erase that state.
        renderer.endRenderPass();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            target.beginRenderPass(Renderer.getCommandBuffer(), pass, stack);
        }
        ((RendererFramebufferAccessor) renderer).veyra$setBoundFramebuffer(target);
        renderer.setBoundRenderPass(pass);
    }

    public static void releaseTargets() {
        if (framebuffers.isEmpty() && resumePasses.isEmpty()) return;
        Renderer renderer = Renderer.getInstance();
        if (renderer.getBoundFramebuffer() instanceof VeyraMrtFramebuffer) resumeScene(renderer);
        framebuffers.values().forEach(VeyraMrtFramebuffer::close);
        resumePasses.values().forEach(RenderPass::cleanUp);
        framebuffers.clear(); resumePasses.clear(); scene = null;
    }

    private record Key(List<VulkanImage> colors, VulkanImage depth) { }
}
