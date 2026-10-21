package dev.veyra.vulkan;

import dev.veyra.shader.FragmentOutputs;
import dev.veyra.shader.GeometryBlendSettings;
import dev.veyra.shader.VeyraRenderer;
import dev.veyra.shader.VeyraShadowRenderer;
import dev.veyra.mixin.accessor.RendererFramebufferAccessor;
import dev.veyra.mixin.accessor.NativePipelineRegistryAccessor;
import org.lwjgl.system.MemoryStack;
import net.vulkanmod.vulkan.memory.MemoryManager;
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
import java.util.Set;
import java.util.Collections;

import static org.lwjgl.vulkan.VK10.*;

/** Routes registered geometry programs without changing vanilla/offscreen pipeline targets. */
public final class VeyraGeometryTargets {
    private static final Map<GraphicsPipeline, FragmentOutputs> layouts = new IdentityHashMap<>();
    private static final Map<GraphicsPipeline, GeometryBlendSettings> blends = new IdentityHashMap<>();
    private static final Set<GraphicsPipeline> terrainInputs = Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Set<GraphicsPipeline> specularInputs = Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Set<GraphicsPipeline> shadowPipelines = Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Map<Key, VeyraMrtFramebuffer> framebuffers = new HashMap<>();
    private static final Map<Framebuffer, RenderPass> resumePasses = new IdentityHashMap<>();
    private static Framebuffer scene;
    private static boolean shadowRouteActive;
    private VeyraGeometryTargets() { }

    public static void register(GraphicsPipeline pipeline, FragmentOutputs layout, GeometryBlendSettings blend) {
        register(pipeline, layout, blend, true);
    }

    public static void register(GraphicsPipeline pipeline, FragmentOutputs layout, GeometryBlendSettings blend,
                                boolean terrainPipeline) {
        if (layout.targets().size() > VeyraDeviceFeatures.maxColorAttachments)
            throw new IllegalStateException("This geometry program exceeds the Vulkan device's color attachment limit.");
        if (!VeyraDeviceFeatures.independentBlendEnabled && java.util.stream.IntStream.range(0, layout.targets().size())
                .filter(layout::writes).count() != layout.targets().size())
            throw new IllegalStateException("Sparse geometry output masks require Vulkan independentBlend support.");
        if (!VeyraDeviceFeatures.independentBlendEnabled && layout.targets().size() > 1 && !blend.targets().isEmpty())
            throw new IllegalStateException("Per-buffer geometry blend properties require Vulkan independentBlend support.");
        layouts.put(pipeline, layout);
        blends.put(pipeline, blend);
        shadowPipelines.remove(pipeline);
        if (terrainPipeline) terrainInputs.add(pipeline);
        else terrainInputs.remove(pipeline);
    }
    public static void registerShadow(GraphicsPipeline pipeline, FragmentOutputs layout, GeometryBlendSettings blend) {
        register(pipeline, layout, blend, false);
        shadowPipelines.add(pipeline);
    }
    public static FragmentOutputs layout(GraphicsPipeline pipeline) { return layouts.get(pipeline); }
    public static GeometryBlendSettings blend(GraphicsPipeline pipeline) { return blends.get(pipeline); }
    public static void registerSpecularInput(GraphicsPipeline pipeline) { specularInputs.add(pipeline); }
    public static void unregister(GraphicsPipeline pipeline) {
        layouts.remove(pipeline); blends.remove(pipeline); terrainInputs.remove(pipeline);
        specularInputs.remove(pipeline); shadowPipelines.remove(pipeline);
    }

    public static void beforeBind(GraphicsPipeline pipeline) {
        FragmentOutputs layout = layouts.get(pipeline);
        boolean terrain = terrainInputs.contains(pipeline);
        VeyraShadowRenderer.bindTerrainShadowColor(pipeline);
        VeyraShadowRenderer.bindTerrainOpaqueDepth(pipeline);
        VeyraNoiseTexture.bind(pipeline);
        if (terrain || specularInputs.contains(pipeline) || pipeline.name.startsWith("veyra_shadow_"))
            VeyraSpecularTextures.bindTerrain(pipeline);
        if (terrain) VeyraRenderer.bindTerrainOpaqueDepth(pipeline);
        Renderer renderer = Renderer.getInstance();
        Framebuffer bound = renderer.getBoundFramebuffer();
        if (shadowPipelines.contains(pipeline)) {
            if (VeyraShadowRenderer.isRendering() && layout != null) routeShadow(renderer, layout);
            return;
        }
        if (VeyraShadowRenderer.isRendering()) {
            if (bound instanceof VeyraMrtFramebuffer) resumeShadow(renderer);
            return;
        }
        if (layout == null) {
            if (bound instanceof VeyraMrtFramebuffer) resumeScene(renderer);
            return;
        }
        // Minecraft 1.21.11 renders GUI item icons into a small atlas framebuffer.
        // Shaderpack geometry outputs belong to the world scene and must not borrow
        // its colortex attachments while a pipeline is drawing into that atlas.
        if (!(bound instanceof VeyraMrtFramebuffer)
                && bound != renderer.getMainPass().getMainFramebuffer()) return;
        if (!(bound instanceof VeyraMrtFramebuffer)) scene = bound;
        if (scene == null) throw new IllegalStateException("Geometry color targets require an active scene render pass.");
        boolean pipelineChanged = renderer.getBoundPipeline() != pipeline;
        boolean endedForSampling = VeyraRenderer.prepareGeometryColorSamplers(pipeline, layout, pipelineChanged);
        if (layout.sceneOnly()) {
            if (bound instanceof VeyraMrtFramebuffer || endedForSampling) resumeScene(renderer);
            return;
        }
        List<VulkanImage> colors = new ArrayList<>();
        for (int target : layout.targets()) colors.add(target == 0 ? scene.getColorAttachment() : VeyraRenderer.geometryColorAttachment(target));
        Key key = new Key(List.copyOf(colors), scene.getDepthAttachment());
        VeyraMrtFramebuffer framebuffer = framebuffers.computeIfAbsent(key,
                ignored -> new VeyraMrtFramebuffer(key.colors(), key.depth()));
        beginPreservingDynamicState(renderer, framebuffer.renderPass(), framebuffer);
        shadowRouteActive = false;
    }

    private static void routeShadow(Renderer renderer, FragmentOutputs layout) {
        Framebuffer shadow = VeyraShadowRenderer.shadowFramebuffer();
        if (shadow == null) throw new IllegalStateException("The shaderpack shadow framebuffer is unavailable.");
        List<VulkanImage> colors = new ArrayList<>(layout.targets().size());
        for (int target : layout.targets()) colors.add(VeyraShadowRenderer.shadowColorAttachment(target));
        Key key = new Key(List.copyOf(colors), shadow.getDepthAttachment());
        VeyraMrtFramebuffer framebuffer = framebuffers.computeIfAbsent(key,
                ignored -> new VeyraMrtFramebuffer(key.colors(), key.depth()));
        beginPreservingDynamicState(renderer, framebuffer.renderPass(), framebuffer);
        shadowRouteActive = true;
    }

    private static void resumeShadow(Renderer renderer) {
        Framebuffer shadow = VeyraShadowRenderer.shadowFramebuffer();
        RenderPass pass = VeyraShadowRenderer.shadowResumePass();
        if (shadow == null || pass == null) throw new IllegalStateException("The shaderpack shadow pass was lost while routing shadow MRT.");
        beginPreservingDynamicState(renderer, pass, shadow);
        shadowRouteActive = false;
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
        shadowRouteActive = false;
    }

    private static void beginPreservingDynamicState(Renderer renderer, RenderPass pass, Framebuffer target) {
        if (renderer.getBoundFramebuffer() == target && renderer.getBoundRenderPass() == pass) return;
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
        if (framebuffers.isEmpty() && resumePasses.isEmpty()) {
            scene = null;
            shadowRouteActive = false;
            return;
        }
        Renderer renderer = Renderer.getInstance();
        if (renderer.getBoundFramebuffer() instanceof VeyraMrtFramebuffer) {
            if (VeyraShadowRenderer.isRendering() || shadowRouteActive) resumeShadow(renderer);
            else resumeScene(renderer);
        }
        Set<RenderPass> retired = Collections.newSetFromMap(new IdentityHashMap<>());
        framebuffers.values().forEach(target -> retired.add(target.renderPass()));
        retired.addAll(resumePasses.values());
        framebuffers.values().forEach(VeyraMrtFramebuffer::close);
        resumePasses.values().forEach(RenderPass::cleanUp);
        // Later draws in this same frame can still cache a variant for a retiring resume pass.
        // Prune at its completion fence, after every submission using that pass has finished.
        MemoryManager.getInstance().addFrameOp(() -> List.copyOf(NativePipelineRegistryAccessor.veyra$getPipelines())
                .forEach(pipeline -> {
                    if (pipeline instanceof VeyraPipelinePassCache cache) cache.veyra$retireRenderPasses(retired);
                }));
        framebuffers.clear(); resumePasses.clear(); scene = null; shadowRouteActive = false;
    }

    private record Key(List<VulkanImage> colors, VulkanImage depth) { }
}
