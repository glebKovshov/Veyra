package dev.veyra.shader;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.veyra.VeyraClient;
import dev.veyra.mixin.accessor.CameraAccessor;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.state.LevelRenderState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.vulkanmod.render.chunk.WorldRenderer;
import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.VRenderSystem;
import net.vulkanmod.vulkan.framebuffer.Framebuffer;
import net.vulkanmod.vulkan.framebuffer.RenderPass;
import net.vulkanmod.vulkan.shader.GraphicsPipeline;
import net.vulkanmod.vulkan.shader.Uniforms;
import net.vulkanmod.vulkan.texture.VTextureSelector;
import net.vulkanmod.vulkan.util.MappedBuffer;
import net.vulkanmod.render.engine.VkGpuDevice;
import net.vulkanmod.render.engine.VkGpuTexture;
import net.vulkanmod.render.engine.VkRenderPass;
import net.vulkanmod.render.engine.VkTextureView;
import net.vulkanmod.vulkan.texture.VulkanImage;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector3f;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.TreeSet;

import static org.lwjgl.vulkan.VK10.VK_ATTACHMENT_LOAD_OP_CLEAR;
import static org.lwjgl.vulkan.VK10.VK_ATTACHMENT_LOAD_OP_LOAD;
import static org.lwjgl.vulkan.VK10.VK_ATTACHMENT_STORE_OP_STORE;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;

/** Renders VulkanMod terrain, entity, and block entity geometry into an Iris-compatible shadow map. */
public final class VeyraShadowRenderer {
    private static final MappedBuffer SHADOW_MODEL_VIEW_BUFFER = new MappedBuffer(16 * Float.BYTES);
    private static final MappedBuffer SHADOW_MODEL_VIEW_INVERSE_BUFFER = new MappedBuffer(16 * Float.BYTES);
    private static final MappedBuffer SHADOW_PROJECTION_BUFFER = new MappedBuffer(16 * Float.BYTES);
    private static final MappedBuffer SHADOW_PROJECTION_INVERSE_BUFFER = new MappedBuffer(16 * Float.BYTES);
    private static final Matrix4f shadowModelView = new Matrix4f();
    private static final Matrix4f shadowModelViewInverse = new Matrix4f();
    private static final Matrix4f shadowProjection = new Matrix4f();
    private static final Matrix4f shadowProjectionInverse = new Matrix4f();

    private static TextureTarget target;
    private static final Map<Integer, TextureTarget> additionalColorTargets = new HashMap<>();
    private static Set<Integer> shadowColorTargetIndices = Set.of(0);
    private static Framebuffer framebuffer;
    private static RenderPass renderPass;
    private static RenderPass translucentRenderPass;
    private static final dev.veyra.vulkan.VeyraDepthTextureSnapshot opaqueDepth = new dev.veyra.vulkan.VeyraDepthTextureSnapshot("shadowtex1");
    private static GpuTextureView opaqueBinding;
    private static GpuTextureView depthTextureView;
    /** Indexed texture-table bindings currently pointing at one of the owned shadow color views. */
    private static final Map<Integer, GpuTextureView> colorBindings = new HashMap<>();
    private static GpuSampler shadowSampler;
    private static VkGpuTexture fallbackTexture;
    private static VkTextureView fallbackTextureView;
    private static GpuSampler fallbackSampler;
    private static int opaqueBindingSlot = -1;
    private static RenderBuffers shadowRenderBuffers;
    private static SubmitNodeStorage shadowSubmitNodeStorage;
    private static FeatureRenderDispatcher shadowFeatureRenderDispatcher;
    private static boolean rendering;
    private static boolean renderingFeatures;
    private static boolean shadowMapCompleted;
    private static boolean warnedAboutNonRecordingFrame;
    private static boolean warnedAboutUnavailableShadowMap;
    private static ShadowSettings shadowSettings = ShadowSettings.DEFAULT;
    private static Vec3 shadowCameraPosition = Vec3.ZERO;

    static {
        Uniforms.mat4f_uniformMap.put("shadowModelView", () -> SHADOW_MODEL_VIEW_BUFFER);
        Uniforms.mat4f_uniformMap.put("shadowModelViewInverse", () -> SHADOW_MODEL_VIEW_INVERSE_BUFFER);
        Uniforms.mat4f_uniformMap.put("shadowProjection", () -> SHADOW_PROJECTION_BUFFER);
        Uniforms.mat4f_uniformMap.put("shadowProjectionInverse", () -> SHADOW_PROJECTION_INVERSE_BUFFER);
        updateShadowMatrixUniformBuffers();
    }

    private VeyraShadowRenderer() {
    }

    public static void initialize() {
        // Trigger the static uniform registrations before VulkanMod compiles Veyra's terrain pipelines.
    }

    /** Prepare a neutral input for the one transition frame before a new shadow map is rendered. */
    public static synchronized void prepareFallbackTexture() {
        if (fallbackTexture != null && !fallbackTexture.isClosed()
                && fallbackTextureView != null && !fallbackTextureView.isClosed()) return;
        if (!(RenderSystem.getDevice() instanceof VkGpuDevice device)) {
            throw new IllegalStateException("Veyra shadow fallbacks require VulkanMod's Vulkan device.");
        }
        VulkanImage image = null;
        VkGpuTexture texture = null;
        VkTextureView view = null;
        GpuSampler sampler = null;
        try {
            image = VulkanImage.builder(1, 1)
                    .setName("Veyra unavailable-shadow fallback")
                    .setFormat(org.lwjgl.vulkan.VK10.VK_FORMAT_R8G8B8A8_UNORM)
                    .setLinearFiltering(true)
                    .setClamp(true)
                    .createVulkanImage();
            texture = device.gpuTextureFromVulkanImage(image);
            image = null;
            view = (VkTextureView) device.createTextureView(texture);
            sampler = device.createSampler(AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE,
                    FilterMode.LINEAR, FilterMode.LINEAR, 1, OptionalDouble.empty());
            try (NativeImage pixels = new NativeImage(NativeImage.Format.RGBA, 1, 1, false)) {
                pixels.setPixel(0, 0, -1);
                device.createCommandEncoder().writeToTexture(texture, pixels);
            }
            fallbackTexture = texture;
            fallbackTextureView = view;
            fallbackSampler = sampler;
        } catch (RuntimeException | Error failure) {
            closeAfterFailure(view, failure);
            closeAfterFailure(texture, failure);
            closeAfterFailure(sampler, failure);
            if (image != null) {
                try {
                    image.free();
                } catch (RuntimeException | Error cleanupFailure) {
                    failure.addSuppressed(cleanupFailure);
                }
            }
            throw failure;
        }
    }

    public static synchronized void closeFallbackTexture() {
        if (fallbackTextureView != null && !fallbackTextureView.isClosed()) fallbackTextureView.close();
        if (fallbackTexture != null && !fallbackTexture.isClosed()) fallbackTexture.close();
        if (fallbackSampler != null) fallbackSampler.close();
        fallbackTextureView = null;
        fallbackTexture = null;
        fallbackSampler = null;
    }

    public static boolean hasCompletedShadowMap() {
        return shadowMapCompleted && target != null && !target.getColorTexture().isClosed();
    }

    public static boolean isRendering() {
        return rendering;
    }

    public static boolean isRenderingFeatures() {
        return renderingFeatures;
    }

    public static GpuTextureView shadowColorTextureView() {
        return shadowColorTextureView(0);
    }

    public static GpuTextureView shadowColorTextureView(int index) {
        if (index == 0) return target == null ? null : target.getColorTextureView();
        TextureTarget colorTarget = additionalColorTargets.get(index);
        return colorTarget == null ? null : colorTarget.getColorTextureView();
    }

    public static net.vulkanmod.vulkan.texture.VulkanImage shadowColorAttachment(int index) {
        GpuTextureView view = shadowColorTextureView(index);
        if (view == null || view.isClosed()) throw new IllegalStateException("shadowcolor" + index + " is not allocated.");
        return ((net.vulkanmod.render.engine.VkGpuTexture) view.texture()).getVulkanImage();
    }

    public static Framebuffer shadowFramebuffer() { return framebuffer; }
    public static RenderPass shadowResumePass() { return translucentRenderPass; }

    public static Set<Integer> shadowColorTargetIndices() {
        return shadowColorTargetIndices;
    }

    public static GpuTextureView shadowDepthTextureView() {
        return target == null ? null : target.getDepthTextureView();
    }

    /** Neutral post-processing input for shader-pack programs whose dimension disables its shadow pass. */
    public static GpuTextureView neutralFallbackTextureView() {
        prepareFallbackTexture();
        return fallbackTextureView;
    }

    public static GpuTextureView opaqueShadowDepthTextureView() {
        return opaqueDepth.sampledView();
    }

    public static void bindTerrainShadowColor(GraphicsPipeline pipeline) {
        for (int index = 0; index <= ShadowColorSampler.MAX_INDEX; index++) {
            String sampler = ShadowColorSampler.samplerName(index);
            var descriptor = pipeline.getImageDescriptor(sampler);
            if (descriptor == null) continue;
            var view = shadowMapCompleted ? shadowColorTextureView(index) : null;
            if (view == null || view.isClosed()) {
                bindTerrainFallback(descriptor.imageIdx);
                warnUnavailableShadowMap();
                continue;
            }
            descriptor.setLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
            descriptor.isReadOnlyLayout = false; // The producing render pass owns the transition.
            var image = ((net.vulkanmod.render.engine.VkGpuTexture) view.texture()).getVulkanImage();
            image.setSampler(net.vulkanmod.vulkan.texture.SamplerManager.getSampler(true, true, 0));
            int slot = descriptor.imageIdx;
            VRenderSystem.setShaderTexture(slot, view);
            VTextureSelector.bindTexture(slot, image);
            colorBindings.put(slot, view);
        }
    }

    public static void bindEntityShadowColor(VkRenderPass pass, GraphicsPipeline pipeline) {
        for (int index = 0; index <= ShadowColorSampler.MAX_INDEX; index++) {
            String sampler = ShadowColorSampler.samplerName(index);
            var descriptor = pipeline.getImageDescriptor(sampler);
            if (descriptor == null) continue;
            var view = shadowMapCompleted ? shadowColorTextureView(index) : null;
            if (view == null || view.isClosed()) {
                prepareFallbackTexture();
                descriptor.setLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
                descriptor.isReadOnlyLayout = false;
                int slot = descriptor.imageIdx;
                colorBindings.put(slot, fallbackTextureView);
                pass.bindTexture(sampler, fallbackTextureView, fallbackSampler);
                VTextureSelector.bindTexture(slot, fallbackTexture.getVulkanImage());
                warnUnavailableShadowMap();
                continue;
            }
            descriptor.setLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
            descriptor.isReadOnlyLayout = false;
            int slot = descriptor.imageIdx;
            colorBindings.put(slot, view);
            pass.bindTexture(sampler, view, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
            VTextureSelector.bindTexture(slot,
                    ((net.vulkanmod.render.engine.VkGpuTexture) view.texture()).getVulkanImage());
        }
    }

    public static void bindTerrainOpaqueDepth(GraphicsPipeline pipeline) {
        var descriptor = pipeline.getImageDescriptor(ShadowDepthSampler.NAME);
        if (descriptor == null) return;
        if (!shadowMapCompleted) {
            bindTerrainFallback(descriptor.imageIdx);
            warnUnavailableShadowMap();
            return;
        }
        var view = opaqueDepth.sampledView();
        descriptor.setLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
        descriptor.isReadOnlyLayout = false;
        opaqueBinding = view;
        opaqueBindingSlot = descriptor.imageIdx;
        var image = ((net.vulkanmod.render.engine.VkGpuTexture) view.texture()).getVulkanImage();
        image.setSampler(net.vulkanmod.vulkan.texture.SamplerManager.getSampler(true, false, 0));
        VRenderSystem.setShaderTexture(descriptor.imageIdx, view);
        VTextureSelector.bindTexture(descriptor.imageIdx, image);
    }

    public static void bindEntityOpaqueDepth(VkRenderPass pass, GraphicsPipeline pipeline) {
        var descriptor = pipeline.getImageDescriptor(ShadowDepthSampler.NAME);
        if (descriptor == null) return;
        if (!shadowMapCompleted) {
            prepareFallbackTexture();
            descriptor.setLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
            descriptor.isReadOnlyLayout = false;
            pass.bindTexture(ShadowDepthSampler.NAME, fallbackTextureView, fallbackSampler);
            VTextureSelector.bindTexture(descriptor.imageIdx, fallbackTexture.getVulkanImage());
            warnUnavailableShadowMap();
            return;
        }
        ensureShadowSampler();
        var view = opaqueDepth.sampledView();
        descriptor.setLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
        descriptor.isReadOnlyLayout = false;
        pass.bindTexture(ShadowDepthSampler.NAME, view, shadowSampler);
        VTextureSelector.bindTexture(descriptor.imageIdx,
                ((net.vulkanmod.render.engine.VkGpuTexture) view.texture()).getVulkanImage());
    }

    /** Binds the generated raw depth map to Iris entity programs using VulkanMod's reserved sampler slot 3. */
    public static void bindEntityShadowSampler(VkRenderPass renderPass, GraphicsPipeline pipeline) {
        var descriptor = pipeline.getImageDescriptor("Sampler3");
        GpuTextureView depthView = shadowDepthTextureView();
        if (descriptor == null) {
            return;
        }
        if (!shadowMapCompleted || depthView == null || depthView.isClosed()) {
            prepareFallbackTexture();
            descriptor.setLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
            descriptor.isReadOnlyLayout = false;
            renderPass.bindTexture("Sampler3", fallbackTextureView, fallbackSampler);
            VTextureSelector.bindTexture(descriptor.imageIdx, fallbackTexture.getVulkanImage());
            warnUnavailableShadowMap();
            return;
        }
        ensureShadowSampler();
        descriptor.setLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
        // The shadow render pass owns the transition; do not let this secondary sampler view
        // transition the same image from a stale per-wrapper layout value.
        descriptor.isReadOnlyLayout = false;
        renderPass.bindTexture("Sampler3", depthView, shadowSampler);
    }

    public static Matrix4f shadowModelView() {
        return new Matrix4f(shadowModelView);
    }

    public static Matrix4f shadowProjection() {
        return new Matrix4f(shadowProjection);
    }

    /** The pack's sunPathRotation directive, shared with the celestial uniforms. */
    public static float sunPathRotation() {
        return shadowSettings.sunPathRotation();
    }

    public static void configure(ShadowSettings settings) {
        configure(settings, Set.of(0));
    }

    public static void configure(ShadowSettings settings, Set<Integer> colorTargets) {
        Set<Integer> requestedColors = Set.copyOf(new TreeSet<>(colorTargets));
        if (shadowSettings.resolution() != settings.resolution()
                || !shadowColorTargetIndices.equals(requestedColors)) {
            release();
        }
        if (!shadowSettings.equals(settings)) {
            VeyraClient.LOGGER.info("Using Iris shadow settings: map={}x{}, distance={}, near={}, far={}, interval={}, sunPathRotation={}, shadowDistanceRenderMul={}, entityShadowDistanceMul={}, shadowTranslucent={}.",
                    settings.resolution(), settings.resolution(), settings.distance(), settings.nearPlane(),
                    settings.farPlane(), settings.intervalSize(), settings.sunPathRotation(),
                    settings.distanceRenderMultiplier(), settings.entityShadowDistanceMultiplier(),
                    settings.shadowTranslucent());
        }
        shadowSettings = settings;
        shadowColorTargetIndices = requestedColors;
    }

    public static boolean shouldRenderBlockEntity(BlockPos blockPos) {
        if (!rendering || blockPos == null || !shadowSettings.hasDistanceRenderLimit()) {
            return true;
        }
        return isWithinShadowRenderDistance(blockPos.getX() + 0.5, blockPos.getY() + 0.5, blockPos.getZ() + 0.5);
    }

    public static void render(Camera camera, Matrix4f mainModelView, Matrix4f mainProjection,
                              Matrix4f mainCullProjection, DeltaTracker deltaTracker, LevelRenderState levelRenderState) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || !VeyraRenderer.isRenderingEnabled() || !VeyraRenderer.hasShadowPass()) {
            shadowModelView.identity();
            shadowProjection.identity();
            updateShadowMatrixUniformBuffers();
            return;
        }
        if (!Renderer.isRecording() || Renderer.getCommandBuffer() == null) {
            if (!warnedAboutNonRecordingFrame) {
                VeyraClient.LOGGER.warn("Skipping one shadow pass because Vulkan is not recording this world-transition frame.");
                warnedAboutNonRecordingFrame = true;
            }
            shadowModelView.identity();
            shadowProjection.identity();
            updateShadowMatrixUniformBuffers();
            return;
        }
        warnedAboutNonRecordingFrame = false;
        prepareFallbackTexture();
        shadowMapCompleted = false;

        Vec3 cameraPosition = camera.position();
        shadowCameraPosition = cameraPosition;
        float originalYaw = camera.yRot();
        float originalPitch = camera.xRot();
        boolean cameraRotationChanged = false;
        boolean shadowRenderPassStarted = false;
        boolean spectator = minecraft.player.isSpectator();
        WorldRenderer worldRenderer = WorldRenderer.getInstance();
        Renderer renderer = Renderer.getInstance();
        // LevelRenderer uses its third matrix argument for culling. The render projection can
        // have Vulkan's depth/Y convention and makes VFrustum's camera-cube expansion diverge.
        Frustum mainFrustum = new Frustum(mainModelView, mainCullProjection);
        mainFrustum.prepare(cameraPosition.x, cameraPosition.y, cameraPosition.z);
        try {
            ensureResources();
            float partialTick = deltaTracker.getGameTimeDeltaPartialTick(false);
            float sunAttribute = camera.attributeProbe()
                    .getValue(net.minecraft.world.attribute.EnvironmentAttributes.SUN_ANGLE, partialTick);
            float moonAttribute = camera.attributeProbe()
                    .getValue(net.minecraft.world.attribute.EnvironmentAttributes.MOON_ANGLE, partialTick);
            float sunAngle = VeyraRenderer.irisShadowAngle(sunAttribute, moonAttribute)
                    * (float) (Math.PI * 2.0);
            ShadowView shadowView = createShadowView(sunAngle, cameraPosition);
            Matrix4f shadowModelView = shadowView.modelView();
            float shadowDistance = shadowSettings.distance();
            Matrix4f shaderPackShadowProjection = IrisShadowMatrices.createProjection(
                    shadowDistance, shadowSettings.nearPlane(), shadowSettings.farPlane());
            Matrix4f shadowCullProjection = IrisShadowMatrices.createProjection(
                    shadowDistance, 0.1F, shadowDistance * 2.0F);
            Matrix4f shadowProjection = IrisShadowMatrices.createVulkanProjection(
                    shadowDistance, shadowSettings.nearPlane(), shadowSettings.farPlane());
            VeyraShadowRenderer.shadowModelView.set(shadowModelView);
            VeyraShadowRenderer.shadowProjection.set(shaderPackShadowProjection);
            updateShadowMatrixUniformBuffers();
            // Resources must exist and be cleared before the first shadow draw, including a lazy pack load.
            VeyraRenderer.prepareShadowTerrainPipelines();
            clearAdditionalShadowColors();

            // VulkanMod expands the culling frustum in vanilla clip space; reserve ZO depth for drawing.
            Frustum shadowFrustum = shadowSettings.hasDistanceRenderLimit()
                    ? new DistanceLimitedFrustum(shadowModelView, shadowCullProjection, cameraPosition,
                            shadowSettings.distanceRenderLimit())
                    : new Frustum(shadowModelView, shadowCullProjection);
            shadowFrustum.prepare(cameraPosition.x, cameraPosition.y, cameraPosition.z);
            Frustum entityShadowFrustum = createEntityShadowFrustum(
                    shadowFrustum, shadowModelView, shadowCullProjection, cameraPosition);
            entityShadowFrustum.prepare(cameraPosition.x, cameraPosition.y, cameraPosition.z);
            ((CameraAccessor) (Object) camera).veyra$setRotation(shadowView.yaw(), shadowView.pitch());
            cameraRotationChanged = true;
            worldRenderer.setupRenderer(camera, shadowFrustum, false, spectator);
            VeyraRenderer.clearShadowCustomImages();
            renderer.beginRenderPass(renderPass, framebuffer);
            shadowRenderPassStarted = true;
            VRenderSystem.setShaderTexture(3, null);
            VTextureSelector.bindTexture(3, VTextureSelector.getWhiteTexture());
            VRenderSystem.applyMVP(shadowModelView, shadowProjection);
            rendering = true;
            worldRenderer.renderSectionLayer(net.vulkanmod.render.vertex.TerrainRenderType.SOLID,
                    cameraPosition.x, cameraPosition.y, cameraPosition.z, shadowModelView, shadowProjection);
            worldRenderer.renderSectionLayer(net.vulkanmod.render.vertex.TerrainRenderType.CUTOUT,
                    cameraPosition.x, cameraPosition.y, cameraPosition.z, shadowModelView, shadowProjection);
            worldRenderer.renderSectionLayer(net.vulkanmod.render.vertex.TerrainRenderType.TRIPWIRE,
                    cameraPosition.x, cameraPosition.y, cameraPosition.z, shadowModelView, shadowProjection);
            renderer.endRenderPass();
            shadowRenderPassStarted = false;
            renderEntityAndBlockEntityGeometry(camera, deltaTracker, levelRenderState,
                    entityShadowFrustum, cameraPosition, shadowModelView, shadowProjection,
                    mainModelView, mainProjection, worldRenderer);
            renderer.endRenderPass();
            opaqueDepth.capture(((net.vulkanmod.render.engine.VkGpuTexture) target.getDepthTexture()).getVulkanImage());
            if (shadowSettings.shadowTranslucent()) {
                renderer.beginRenderPass(translucentRenderPass, framebuffer);
                shadowRenderPassStarted = true;
                VRenderSystem.applyMVP(shadowModelView, shadowProjection);
                worldRenderer.renderSectionLayer(net.vulkanmod.render.vertex.TerrainRenderType.TRANSLUCENT,
                        cameraPosition.x, cameraPosition.y, cameraPosition.z, shadowModelView, shadowProjection);
                renderer.endRenderPass();
                shadowRenderPassStarted = false;
            }
            // Entity feature passes can leave depth in attachment layout when translucency
            // is disabled. Complete the producer transitions on this graphics command buffer.
            try (org.lwjgl.system.MemoryStack stack = org.lwjgl.system.MemoryStack.stackPush()) {
                ((net.vulkanmod.render.engine.VkGpuTexture) target.getDepthTexture()).getVulkanImage()
                        .transitionImageLayout(stack, Renderer.getCommandBuffer(), VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
                for (int index : shadowColorTargetIndices) shadowColorAttachment(index)
                        .transitionImageLayout(stack, Renderer.getCommandBuffer(), VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
            }
            VeyraRenderer.dispatchShadowCompute();
            shadowMapCompleted = true;
            warnedAboutUnavailableShadowMap = false;
        } catch (Exception exception) {
            shadowMapCompleted = false;
            VeyraClient.LOGGER.error("Could not render the shader pack shadow map.", exception);
            VeyraRenderer.shadowInputFailed(exception);
        } finally {
            renderingFeatures = false;
            rendering = false;
            if (shadowRenderPassStarted) {
                renderer.endRenderPass();
            }
            VRenderSystem.applyMVP(mainModelView, mainProjection);
            if (cameraRotationChanged) {
                ((CameraAccessor) (Object) camera).veyra$setRotation(originalYaw, originalPitch);
                try {
                    worldRenderer.setupRenderer(camera, mainFrustum, false, spectator);
                } catch (Exception exception) {
                    VeyraClient.LOGGER.error("Could not restore VulkanMod's main chunk frustum after shadow rendering.", exception);
                }
            }
            if (depthTextureView != null) {
                VRenderSystem.setShaderTexture(3, depthTextureView);
            }
        }
    }

    public static void release() {
        shadowMapCompleted = false;
        warnedAboutUnavailableShadowMap = false;
        dev.veyra.vulkan.VeyraGeometryTargets.releaseTargets();
        if (rendering) {
            Renderer.getInstance().endRenderPass();
            rendering = false;
        }
        VRenderSystem.setShaderTexture(3, null);
        for (Map.Entry<Integer, GpuTextureView> entry : colorBindings.entrySet()) {
            int slot = entry.getKey();
            GpuTextureView binding = entry.getValue();
            if (VRenderSystem.getShaderTexture(slot) == binding) VRenderSystem.setShaderTexture(slot, null);
            if (binding != null && !binding.isClosed()) {
                var image = ((net.vulkanmod.render.engine.VkGpuTexture) binding.texture()).getVulkanImage();
                if (VTextureSelector.getImage(slot) == image)
                    VTextureSelector.bindTexture(slot, VTextureSelector.getWhiteTexture());
            }
        }
        colorBindings.clear();
        if (opaqueBinding != null && opaqueBindingSlot >= 0) {
            if (VRenderSystem.getShaderTexture(opaqueBindingSlot) == opaqueBinding)
                VRenderSystem.setShaderTexture(opaqueBindingSlot, null);
            if (opaqueBinding != null && !opaqueBinding.isClosed()) {
                var image = ((net.vulkanmod.render.engine.VkGpuTexture) opaqueBinding.texture()).getVulkanImage();
                if (VTextureSelector.getImage(opaqueBindingSlot) == image)
                    VTextureSelector.bindTexture(opaqueBindingSlot, VTextureSelector.getWhiteTexture());
            }
            opaqueBinding = null;
            opaqueBindingSlot = -1;
        }
        depthTextureView = null;
        if (shadowSampler != null) {
            shadowSampler.close();
            shadowSampler = null;
        }
        if (framebuffer != null) {
            framebuffer.cleanUp(false);
            framebuffer = null;
        }
        opaqueDepth.close();
        Set<RenderPass> retiredPasses = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        if (renderPass != null) retiredPasses.add(renderPass);
        if (translucentRenderPass != null) retiredPasses.add(translucentRenderPass);
        if (!retiredPasses.isEmpty()) net.vulkanmod.vulkan.memory.MemoryManager.getInstance().addFrameOp(() ->
                java.util.List.copyOf(dev.veyra.mixin.accessor.NativePipelineRegistryAccessor.veyra$getPipelines()).forEach(pipeline -> {
                    if (pipeline instanceof dev.veyra.vulkan.VeyraPipelinePassCache cache) cache.veyra$retireRenderPasses(retiredPasses);
                }));
        if (translucentRenderPass != null) { translucentRenderPass.cleanUp(); translucentRenderPass = null; }
        if (renderPass != null) {
            renderPass.cleanUp();
            renderPass = null;
        }
        if (target != null) {
            target.destroyBuffers();
            target = null;
        }
        additionalColorTargets.values().forEach(TextureTarget::destroyBuffers);
        additionalColorTargets.clear();
        releaseFeatureDispatcher();
        shadowModelView.identity();
        shadowProjection.identity();
        updateShadowMatrixUniformBuffers();
    }

    public static void clearBinding() {
        VRenderSystem.setShaderTexture(3, null);
    }

    private static void ensureResources() {
        if (renderPass != null) {
            return;
        }
        target = new TextureTarget("Veyra shadow map", shadowSettings.resolution(),
                shadowSettings.resolution(), true);
        var colorTexture = (net.vulkanmod.render.engine.VkGpuTexture) target.getColorTexture();
        var depthTexture = (net.vulkanmod.render.engine.VkGpuTexture) target.getDepthTexture();
        framebuffer = Framebuffer.builder(colorTexture.getVulkanImage(), depthTexture.getVulkanImage()).build();
        RenderPass.Builder passBuilder = RenderPass.builder(framebuffer);
        passBuilder.getColorAttachmentInfo().setFinalLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
        passBuilder.getDepthAttachmentInfo()
                .setOps(VK_ATTACHMENT_LOAD_OP_CLEAR, VK_ATTACHMENT_STORE_OP_STORE)
                .setFinalLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
        renderPass = passBuilder.build();
        var resumeBuilder = RenderPass.builder(framebuffer);
        resumeBuilder.getColorAttachmentInfo().setOps(org.lwjgl.vulkan.VK10.VK_ATTACHMENT_LOAD_OP_LOAD, VK_ATTACHMENT_STORE_OP_STORE)
                .setFinalLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
        resumeBuilder.getDepthAttachmentInfo().setOps(org.lwjgl.vulkan.VK10.VK_ATTACHMENT_LOAD_OP_LOAD, VK_ATTACHMENT_STORE_OP_STORE)
                .setFinalLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
        translucentRenderPass = resumeBuilder.build();
        for (int index : shadowColorTargetIndices) {
            if (index == 0) continue;
            additionalColorTargets.put(index, new TextureTarget("Veyra shadowcolor" + index,
                    shadowSettings.resolution(), shadowSettings.resolution(), false));
        }
        // The target owns this view. Sampling its native depth view avoids creating a second
        // Vulkan view object for the same image, which can upset VulkanMod's descriptor binding.
        depthTextureView = target.getDepthTextureView();
    }

    private static void clearAdditionalShadowColors() {
        if (shadowColorTargetIndices.stream().noneMatch(index -> index > 0)) return;
        if (!Renderer.isRecording() || Renderer.getCommandBuffer() == null) {
            throw new IllegalStateException("Shadow color attachments must be cleared on a recording frame.");
        }
        for (int index : shadowColorTargetIndices) {
            if (index == 0) continue; // The base shadow render pass clears color0 and depth.
            TextureTarget colorTarget = additionalColorTargets.get(index);
            if (colorTarget == null) throw new IllegalStateException("shadowcolor" + index + " was not allocated.");
            VeyraRenderer.clearColorTarget(colorTarget, 0x00000000);
        }
    }

    private static void ensureShadowSampler() {
        if (shadowSampler != null) {
            return;
        }
        if (!(RenderSystem.getDevice() instanceof VkGpuDevice device)) {
            throw new IllegalStateException("The Iris shadow sampler requires VulkanMod's Vulkan device.");
        }
        shadowSampler = device.createSampler(AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE,
                FilterMode.NEAREST, FilterMode.NEAREST, 1, OptionalDouble.empty());
    }

    private static void bindTerrainFallback(int slot) {
        prepareFallbackTexture();
        var descriptorImage = fallbackTexture.getVulkanImage();
        descriptorImage.setSampler(net.vulkanmod.vulkan.texture.SamplerManager.getSampler(true, true, 0));
        VRenderSystem.setShaderTexture(slot, fallbackTextureView);
        VTextureSelector.bindTexture(slot, descriptorImage);
    }

    private static void warnUnavailableShadowMap() {
        if (warnedAboutUnavailableShadowMap) return;
        warnedAboutUnavailableShadowMap = true;
        VeyraClient.LOGGER.warn("Using a neutral shadow input until Vulkan records a completed shader-pack shadow pass.");
    }

    private static void closeAfterFailure(AutoCloseable resource, Throwable failure) {
        if (resource == null) return;
        try {
            resource.close();
        } catch (Exception cleanupFailure) {
            failure.addSuppressed(cleanupFailure);
        }
    }

    private static void renderEntityAndBlockEntityGeometry(
            Camera camera, DeltaTracker deltaTracker, LevelRenderState levelRenderState,
            Frustum entityShadowFrustum, Vec3 cameraPosition, Matrix4f modelView, Matrix4f projection,
            Matrix4f mainModelView, Matrix4f mainProjection, WorldRenderer worldRenderer) {
        Minecraft minecraft = Minecraft.getInstance();
        RenderBuffers buffers = minecraft.renderBuffers();
        ensureFeatureDispatcher(minecraft, buffers);

        Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
        Matrix4f savedModelView = new Matrix4f(modelViewStack);
        PoseStack poseStack = new PoseStack();
        SubmitNodeStorage submits = shadowSubmitNodeStorage;
        Set<Object> submittedEntities = new HashSet<>();
        try {
            modelViewStack.identity().mul(modelView);
            VRenderSystem.applyMVP(modelView, projection);
            renderingFeatures = true;

            float tickDelta = deltaTracker.getGameTimeDeltaPartialTick(false);
            var entityDispatcher = minecraft.getEntityRenderDispatcher();
            entityDispatcher.prepare(camera, minecraft.crosshairPickEntity);
            for (var entity : shadowSettings.shadowEntities()
                    ? minecraft.level.entitiesForRendering() : java.util.List.<net.minecraft.world.entity.Entity>of()) {
                if (!entityDispatcher.shouldRender(entity, entityShadowFrustum,
                        cameraPosition.x, cameraPosition.y, cameraPosition.z)) {
                    continue;
                }
                EntityRenderState state = entityDispatcher.extractEntity(entity, tickDelta);
                entityDispatcher.submit(state, levelRenderState.cameraRenderState,
                        state.x - cameraPosition.x, state.y - cameraPosition.y, state.z - cameraPosition.z,
                        poseStack, submits);
                submittedEntities.add(entity);
            }

            var player = minecraft.player;
            if (shadowSettings.shadowEntities() && player != null && submittedEntities.add(player)
                    && entityDispatcher.shouldRender(player, entityShadowFrustum,
                    cameraPosition.x, cameraPosition.y, cameraPosition.z)) {
                EntityRenderState state = entityDispatcher.extractEntity(player, tickDelta);
                entityDispatcher.submit(state, levelRenderState.cameraRenderState,
                        state.x - cameraPosition.x, state.y - cameraPosition.y, state.z - cameraPosition.z,
                        poseStack, submits);
            }

            if (shadowSettings.shadowBlockEntities()) {
                worldRenderer.renderBlockEntities(poseStack, levelRenderState, submits,
                        new it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<>());
            }
            shadowFeatureRenderDispatcher.renderAllFeatures();
            buffers.bufferSource().endBatch();
            shadowFeatureRenderDispatcher.endFrame();
        } finally {
            submits.clear();
            renderingFeatures = false;
            modelViewStack.set(savedModelView);
            VRenderSystem.applyMVP(mainModelView, mainProjection);
        }
    }

    private static void ensureFeatureDispatcher(Minecraft minecraft, RenderBuffers buffers) {
        if (shadowFeatureRenderDispatcher != null && shadowRenderBuffers == buffers) {
            return;
        }
        releaseFeatureDispatcher();
        shadowRenderBuffers = buffers;
        shadowSubmitNodeStorage = new SubmitNodeStorage();
        shadowFeatureRenderDispatcher = new FeatureRenderDispatcher(shadowSubmitNodeStorage,
                minecraft.getBlockRenderer(), buffers.bufferSource(), minecraft.getAtlasManager(),
                buffers.outlineBufferSource(), buffers.crumblingBufferSource(), minecraft.font);
    }

    private static void releaseFeatureDispatcher() {
        if (shadowFeatureRenderDispatcher != null) {
            shadowFeatureRenderDispatcher.close();
            shadowFeatureRenderDispatcher = null;
        }
        if (shadowSubmitNodeStorage != null) {
            shadowSubmitNodeStorage.clear();
            shadowSubmitNodeStorage = null;
        }
        shadowRenderBuffers = null;
    }

    private static ShadowView createShadowView(float sunAngle, Vec3 cameraPosition) {
        Vector3f lightDirection = new Vector3f((float) Math.cos(sunAngle), (float) Math.sin(sunAngle), 0.25F);
        lightDirection.rotateX((float) Math.toRadians(shadowSettings.sunPathRotation()));
        if (lightDirection.y < 0.0F) lightDirection.negate();
        lightDirection.normalize();
        Matrix4f modelView = new Matrix4f().setLookAt(
                lightDirection.x * shadowSettings.distance(), lightDirection.y * shadowSettings.distance(),
                lightDirection.z * shadowSettings.distance(), 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F);
        float intervalSize = shadowSettings.intervalSize();
        if (intervalSize > 0.0F) {
            float halfInterval = intervalSize / 2.0F;
            modelView.translate((float) cameraPosition.x % intervalSize - halfInterval,
                    (float) cameraPosition.y % intervalSize - halfInterval,
                    (float) cameraPosition.z % intervalSize - halfInterval);
        }
        Vector3f viewDirection = lightDirection.negate(new Vector3f());
        float yaw = (float) Math.toDegrees(Math.atan2(-viewDirection.x, viewDirection.z));
        float pitch = (float) Math.toDegrees(-Math.asin(viewDirection.y));
        return new ShadowView(modelView, yaw, pitch);
    }

    private static boolean isWithinShadowRenderDistance(double x, double y, double z) {
        double dx = x - shadowCameraPosition.x;
        double dy = y - shadowCameraPosition.y;
        double dz = z - shadowCameraPosition.z;
        double limit = shadowSettings.distanceRenderLimit();
        return dx * dx + dy * dy + dz * dz <= limit * limit;
    }

    private static Frustum createEntityShadowFrustum(Frustum terrainFrustum, Matrix4f modelView,
                                                      Matrix4f projection, Vec3 cameraPosition) {
        if (shadowSettings.usesTerrainEntityShadowFrustum()) {
            return terrainFrustum;
        }
        if (shadowSettings.hasEntityDistanceRenderLimit()) {
            return new DistanceLimitedFrustum(modelView, projection, cameraPosition,
                    shadowSettings.entityDistanceRenderLimit());
        }
        return new Frustum(modelView, projection);
    }

    private static final class DistanceLimitedFrustum extends Frustum {
        private final Vec3 cameraPosition;
        private final double maxDistanceSquared;

        private DistanceLimitedFrustum(Matrix4f modelView, Matrix4f projection,
                                       Vec3 cameraPosition, double maxDistance) {
            super(modelView, projection);
            this.cameraPosition = cameraPosition;
            this.maxDistanceSquared = maxDistance * maxDistance;
        }

        @Override
        public boolean isVisible(AABB box) {
            if (!super.isVisible(box)) {
                return false;
            }
            double nearestX = clamp(cameraPosition.x, box.minX, box.maxX);
            double nearestY = clamp(cameraPosition.y, box.minY, box.maxY);
            double nearestZ = clamp(cameraPosition.z, box.minZ, box.maxZ);
            double dx = nearestX - cameraPosition.x;
            double dy = nearestY - cameraPosition.y;
            double dz = nearestZ - cameraPosition.z;
            return dx * dx + dy * dy + dz * dz <= maxDistanceSquared;
        }

        private static double clamp(double value, double min, double max) {
            return Math.max(min, Math.min(value, max));
        }
    }

    private static void writeMatrix(Matrix4f matrix, ByteBuffer buffer) {
        matrix.get(buffer.asFloatBuffer());
    }

    private static void updateShadowMatrixUniformBuffers() {
        shadowModelViewInverse.set(shadowModelView).invert();
        shadowProjectionInverse.set(shadowProjection).invert();
        writeMatrix(shadowModelView, SHADOW_MODEL_VIEW_BUFFER.buffer);
        writeMatrix(shadowModelViewInverse, SHADOW_MODEL_VIEW_INVERSE_BUFFER.buffer);
        writeMatrix(shadowProjection, SHADOW_PROJECTION_BUFFER.buffer);
        writeMatrix(shadowProjectionInverse, SHADOW_PROJECTION_INVERSE_BUFFER.buffer);
    }

    private record ShadowView(Matrix4f modelView, float yaw, float pitch) { }
}
