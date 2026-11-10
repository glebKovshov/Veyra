package dev.veyra.compat;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.veyra.shader.VeyraRenderer;
import dev.veyra.shader.VeyraShadowRenderer;
import dev.veyra.vulkan.LegacySampler;
import dev.veyra.vulkan.LegacyVkTextureView;
import dev.veyra.vulkan.VeyraNoiseTexture;
import dev.veyra.vulkan.VeyraNormalTextures;
import dev.veyra.vulkan.VeyraSpecularTextures;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.CompiledShaderProgram;
import net.minecraft.client.renderer.RenderType;
import net.vulkanmod.gl.VkGlFramebuffer;
import net.vulkanmod.interfaces.shader.ShaderMixed;
import net.vulkanmod.vulkan.shader.GraphicsPipeline;

/**
 * Routes Minecraft 1.21.4 draws through the shader pack. Later versions do this in VulkanMod's
 * render pass; here a draw is a render type applying global state followed by a program bind, so
 * the same decisions hang on those two points.
 */
public final class LegacyDraws {
    private static final int GL_FRAMEBUFFER = 0x8D40;
    private static final int GL_COLOR_ATTACHMENT0 = 0x8CE0;
    private static final int GL_DEPTH_ATTACHMENT = 0x8D00;
    private static final int GL_TEXTURE_2D = 0x0DE1;
    private static final VkRenderPass PASS = new VkRenderPass();
    private static final Map<CompiledShaderProgram, RenderPipeline> BY_PROGRAM = new IdentityHashMap<>();
    private static final Map<Long, Integer> FRAMEBUFFERS = new HashMap<>();

    private static int keepBoundTarget;

    private LegacyDraws() { }

    /** Runs {@code action} without letting Minecraft's main-target binds replace the bound target. */
    public static void keepBoundTarget(Runnable action) {
        keepBoundTarget++;
        try { action.run(); }
        finally { keepBoundTarget--; }
    }

    public static boolean keepsBoundTarget() { return keepBoundTarget > 0; }

    static void register(RenderPipeline pipeline) { BY_PROGRAM.put(pipeline.getProgram().program(), pipeline); }

    static void unregister(EGlProgram program) { BY_PROGRAM.remove(program.program()); }

    /**
     * Applies the fixed-function state a pipeline declares. VulkanMod 0.5.6 for 1.21.4 builds the
     * native pipeline state from global render state, so draws outside a render type set it here.
     */
    public static void applyPipelineState(RenderPipeline pipeline) {
        if (pipeline.getBlendFunction().isPresent()) {
            RenderSystem.enableBlend();
            RenderSystem.blendFuncSeparate(com.mojang.blaze3d.platform.GlStateManager.SourceFactor.SRC_ALPHA,
                    com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                    com.mojang.blaze3d.platform.GlStateManager.SourceFactor.ONE,
                    com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        } else {
            RenderSystem.disableBlend();
        }
        RenderSystem.depthFunc(switch (pipeline.getDepthTestFunction()) {
            case NO_DEPTH_TEST -> 0x0207;
            case EQUAL_DEPTH_TEST -> 0x0202;
            case LESS_DEPTH_TEST -> 0x0201;
            case GREATER_DEPTH_TEST -> 0x0204;
            default -> 0x0203;
        });
        RenderSystem.depthMask(pipeline.isWriteDepth());
        RenderSystem.colorMask(pipeline.isWriteColor(), pipeline.isWriteColor(), pipeline.isWriteColor(), pipeline.isWriteAlpha());
        if (pipeline.isCull()) RenderSystem.enableCull();
        else RenderSystem.disableCull();
    }

    /** Returns global render state to Minecraft's defaults after a draw that applied a pipeline's state. */
    public static void restorePipelineState() {
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthFunc(0x0203);
        RenderSystem.depthMask(true);
        RenderSystem.colorMask(true, true, true, true);
        RenderSystem.enableCull();
    }

    /** The render pass bound at some point, to bind again after work that must run outside it. */
    public record Binding(net.vulkanmod.vulkan.framebuffer.Framebuffer framebuffer,
                          net.vulkanmod.vulkan.framebuffer.RenderPass renderPass, VkGlFramebuffer glFramebuffer) { }

    public static Binding binding() {
        var renderer = net.vulkanmod.vulkan.Renderer.getInstance();
        return new Binding(renderer.getBoundFramebuffer(), renderer.getBoundRenderPass(), VkGlFramebuffer.getBoundFramebuffer());
    }

    /** Binds {@code binding} again, resuming its attachments' contents; nothing bound ends the current pass. */
    public static void restore(Binding binding) {
        var renderer = net.vulkanmod.vulkan.Renderer.getInstance();
        if (renderer.getBoundFramebuffer() == binding.framebuffer() && renderer.getBoundRenderPass() == binding.renderPass()) return;
        renderer.endRenderPass();
        if (binding.framebuffer() == null || binding.renderPass() == null) return;
        if (binding.glFramebuffer() != null) {
            VkGlFramebuffer.beginRendering(binding.glFramebuffer());
        } else if (binding.framebuffer() == renderer.getSwapChain()) {
            // The frame's first swapchain pass discards color; resume with the main target's loading pass.
            renderer.getMainPass().rebindMainTarget();
        } else {
            renderer.beginRendering(binding.renderPass(), binding.framebuffer());
        }
        // Beginning a pass resets the viewport; draws keep the one Minecraft last set.
        com.mojang.blaze3d.platform.GlStateManager._viewport(com.mojang.blaze3d.platform.GlStateManager.Viewport.x(),
                com.mojang.blaze3d.platform.GlStateManager.Viewport.y(), com.mojang.blaze3d.platform.GlStateManager.Viewport.width(),
                com.mojang.blaze3d.platform.GlStateManager.Viewport.height());
    }

    private static boolean bindingInputs;
    private static Binding suspended;

    /** Ends the render pass for a layout barrier while Veyra binds a draw's inputs. */
    public static void suspendForBarrier() {
        if (!bindingInputs || suspended != null) return;
        var renderer = net.vulkanmod.vulkan.Renderer.getInstance();
        if (renderer.getBoundRenderPass() == null) return;
        suspended = binding();
        renderer.endRenderPass();
    }

    /** The Veyra pipeline compiled into {@code program}, or null for other programs. */
    public static RenderPipeline pipeline(CompiledShaderProgram program) { return BY_PROGRAM.get(program); }

    /** The pass object for binding inputs of {@code program} before it is drawn. */
    public static VkRenderPass pass(CompiledShaderProgram program) {
        PASS.setPipeline(BY_PROGRAM.get(program));
        return PASS;
    }

    /** Runs after a render type applied its state and before its mesh is drawn. */
    public static void beginRenderType(RenderType type) {
        RenderPipeline forced = VanillaPipelines.forced(type);
        if (forced == null && !VeyraRenderer.isRenderingEnabled()) return;
        RenderPipeline original = forced != null ? forced : VanillaPipelines.of(type);
        if (original == null) return;
        RenderPipeline active = forced != null ? forced : route(original);
        if (active != null && active.getProgram() != null) RenderSystem.setShader(active.getProgram().program());
        if (VeyraShadowRenderer.isRenderingFeatures()) {
            bindFramebuffer((VkGpuTexture) VeyraShadowRenderer.shadowColorTextureView().textureForBinding(),
                    (VkGpuTexture) VeyraShadowRenderer.shadowDepthTextureView().textureForBinding());
        }
    }

    /** Routes a draw that binds a vanilla shader program without a render type. */
    public static void beginProgramDraw(RenderPipeline original) {
        if (original == null || !VeyraRenderer.isRenderingEnabled()) return;
        RenderPipeline active = route(original);
        if (active != null && active.getProgram() != null) RenderSystem.setShader(active.getProgram().program());
    }

    // The order matches the pipeline routing of the later render pass hook.
    private static RenderPipeline route(RenderPipeline original) {
        if (VeyraRenderer.isGuiRendering() || !VeyraRenderer.shaderPackTexturesReady()) return null;
        RenderPipeline replacement = VeyraRenderer.getHandPipeline(original);
        if (replacement == null) replacement = VeyraRenderer.getSignTextPipeline(original);
        if (replacement == null) replacement = VeyraRenderer.getEntityPipeline(original);
        if (replacement == null) replacement = VeyraRenderer.getBeaconBeamPipeline(original);
        if (replacement == null) replacement = VeyraRenderer.getParticlePipeline(original);
        if (replacement == null) replacement = VeyraRenderer.getWeatherPipeline(original);
        if (replacement == null) replacement = VeyraRenderer.getLightningPipeline(original);
        if (replacement == null) replacement = VeyraRenderer.getBasicPipeline(original);
        if (replacement == null) replacement = VeyraRenderer.getLinePipeline(original);
        if (replacement == null) replacement = VeyraRenderer.getGlintPipeline(original);
        if (replacement == null) replacement = VeyraRenderer.getSkyBasicPipeline(original);
        if (replacement == null) replacement = VeyraRenderer.getSkyTexturedPipeline(original);
        if (replacement == null) replacement = VeyraRenderer.getWorldBorderPipeline(original);
        if (replacement == null) replacement = VeyraRenderer.getDamagedBlockPipeline(original);
        return replacement;
    }

    /** Runs before VulkanMod binds the native pipeline of {@code program} and its textures. */
    public static void beforeBind(CompiledShaderProgram program) {
        bindingInputs = true;
        try {
            bindInputs(program);
        } finally {
            bindingInputs = false;
            Binding resume = suspended;
            suspended = null;
            if (resume != null) restore(resume);
        }
    }

    private static void bindInputs(CompiledShaderProgram program) {
        RenderPipeline active = BY_PROGRAM.get(program);
        if (active == null || !active.getFragmentShader().getNamespace().equals("veyra")) return;
        GraphicsPipeline pipeline = active.getPipeline();
        if (pipeline == null) return;
        PASS.setPipeline(active);
        // VulkanMod draws the lightmap from its own texture, which turning on the light layer selects.
        if (active.getSamplers().contains("Sampler2")) Minecraft.getInstance().gameRenderer.lightTexture().turnOnLightLayer();
        VeyraRenderer.bindCloudSceneColor(PASS, pipeline);
        VeyraShadowRenderer.bindEntityShadowColor(PASS, pipeline);
        VeyraShadowRenderer.bindEntityOpaqueDepth(PASS, pipeline);
        if (pipeline.getImageDescriptor(VeyraNoiseTexture.SAMPLER_NAME) != null) VeyraNoiseTexture.bind(PASS);
        if (active.getSamplers().contains("Sampler3")) VeyraShadowRenderer.bindEntityShadowSampler(PASS, pipeline);

        VkGpuTexture albedo = VkGpuTexture.of(RenderSystem.getShaderTexture(0));
        if (albedo != null && active.getSamplers().contains("Sampler0")) {
            var view = LegacyVkTextureView.create(albedo);
            VeyraSpecularTextures.bindEntity(PASS, view);
            VeyraNormalTextures.bindEntity(PASS, view, LegacySampler.from(albedo));
        }
        VeyraRenderer.bindShaderPackTextures(PASS, pipeline, active.getFragmentShader().getPath());
    }

    /** Binds a framebuffer made of two registered textures, as the later render pass creation does. */
    public static void bindFramebuffer(VkGpuTexture color, VkGpuTexture depth) {
        long key = (long) color.glId() << 32 | (depth == null ? 0 : depth.glId());
        Integer id = FRAMEBUFFERS.get(key);
        if (id == null) {
            id = VkGlFramebuffer.genFramebufferId();
            VkGlFramebuffer.bindFramebuffer(GL_FRAMEBUFFER, id);
            VkGlFramebuffer.framebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, color.glId(), 0);
            if (depth != null) {
                VkGlFramebuffer.framebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_TEXTURE_2D, depth.glId(), 0);
            }
            FRAMEBUFFERS.put(key, id);
            return;
        }
        VkGlFramebuffer.bindFramebuffer(GL_FRAMEBUFFER, id);
    }

    /** Drops framebuffers that referenced a texture id being deleted. */
    static void forget(int textureId) {
        FRAMEBUFFERS.entrySet().removeIf(entry -> {
            long key = entry.getKey();
            if ((int) (key >>> 32) != textureId && (int) key != textureId) return false;
            VkGlFramebuffer.deleteFramebuffer(entry.getValue());
            return true;
        });
    }
}
