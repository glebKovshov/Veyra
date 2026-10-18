package dev.veyra.shader;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
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
import net.vulkanmod.vulkan.shader.Uniforms;
import net.vulkanmod.vulkan.texture.VTextureSelector;
import net.vulkanmod.vulkan.util.MappedBuffer;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector3f;

import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.Set;

import static org.lwjgl.vulkan.VK10.VK_ATTACHMENT_LOAD_OP_CLEAR;
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
    private static Framebuffer framebuffer;
    private static RenderPass renderPass;
    private static GpuTextureView depthTextureView;
    private static RenderBuffers shadowRenderBuffers;
    private static SubmitNodeStorage shadowSubmitNodeStorage;
    private static FeatureRenderDispatcher shadowFeatureRenderDispatcher;
    private static boolean rendering;
    private static boolean renderingFeatures;
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

    public static boolean isRendering() {
        return rendering;
    }

    public static boolean isRenderingFeatures() {
        return renderingFeatures;
    }

    public static GpuTextureView shadowColorTextureView() {
        return target == null ? null : target.getColorTextureView();
    }

    public static GpuTextureView shadowDepthTextureView() {
        return target == null ? null : target.getDepthTextureView();
    }

    public static Matrix4f shadowModelView() {
        return new Matrix4f(shadowModelView);
    }

    public static Matrix4f shadowProjection() {
        return new Matrix4f(shadowProjection);
    }

    public static void configure(ShadowSettings settings) {
        if (shadowSettings.resolution() != settings.resolution()) {
            release();
        }
        if (!shadowSettings.equals(settings)) {
            VeyraClient.LOGGER.info("Using Iris shadow settings: map={}x{}, distance={}, interval={}, sunPathRotation={}, shadowDistanceRenderMul={}, entityShadowDistanceMul={}, shadowTranslucent={}.",
                    settings.resolution(), settings.resolution(), settings.distance(),
                    settings.intervalSize(), settings.sunPathRotation(), settings.distanceRenderMultiplier(),
                    settings.entityShadowDistanceMultiplier(), settings.shadowTranslucent());
        }
        shadowSettings = settings;
    }

    public static boolean shouldRenderBlockEntity(BlockPos blockPos) {
        if (!rendering || blockPos == null || !shadowSettings.hasDistanceRenderLimit()) {
            return true;
        }
        return isWithinShadowRenderDistance(blockPos.getX() + 0.5, blockPos.getY() + 0.5, blockPos.getZ() + 0.5);
    }

    public static void render(Camera camera, Matrix4f mainModelView, Matrix4f mainProjection,
                              DeltaTracker deltaTracker, LevelRenderState levelRenderState) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || !VeyraRenderer.hasShadowPass()) {
            shadowModelView.identity();
            shadowProjection.identity();
            updateShadowMatrixUniformBuffers();
            return;
        }

        Vec3 cameraPosition = camera.position();
        shadowCameraPosition = cameraPosition;
        float originalYaw = camera.yRot();
        float originalPitch = camera.xRot();
        boolean cameraRotationChanged = false;
        boolean shadowRenderPassStarted = false;
        boolean spectator = minecraft.player.isSpectator();
        WorldRenderer worldRenderer = WorldRenderer.getInstance();
        Renderer renderer = Renderer.getInstance();
        Frustum mainFrustum = new Frustum(mainModelView, mainProjection);
        mainFrustum.prepare(cameraPosition.x, cameraPosition.y, cameraPosition.z);
        try {
            ensureResources();
            float sunAngle = (minecraft.level.getDayTime() % 24000L) / 24000.0F * (float) (Math.PI * 2.0);
            ShadowView shadowView = createShadowView(sunAngle, cameraPosition);
            Matrix4f shadowModelView = shadowView.modelView();
            float shadowDistance = shadowSettings.distance();
            Matrix4f shadowProjection = new Matrix4f().setOrthoSymmetric(
                    shadowDistance * 2.0F, shadowDistance * 2.0F, 0.1F, shadowDistance * 2.0F, true);
            VeyraShadowRenderer.shadowModelView.set(shadowModelView);
            VeyraShadowRenderer.shadowProjection.set(shadowProjection);
            updateShadowMatrixUniformBuffers();

            Frustum shadowFrustum = shadowSettings.hasDistanceRenderLimit()
                    ? new DistanceLimitedFrustum(shadowModelView, shadowProjection, cameraPosition,
                            shadowSettings.distanceRenderLimit())
                    : new Frustum(shadowModelView, shadowProjection);
            shadowFrustum.prepare(cameraPosition.x, cameraPosition.y, cameraPosition.z);
            Frustum entityShadowFrustum = createEntityShadowFrustum(
                    shadowFrustum, shadowModelView, shadowProjection, cameraPosition);
            entityShadowFrustum.prepare(cameraPosition.x, cameraPosition.y, cameraPosition.z);
            ((CameraAccessor) (Object) camera).veyra$setRotation(shadowView.yaw(), shadowView.pitch());
            cameraRotationChanged = true;
            worldRenderer.setupRenderer(camera, shadowFrustum, false, spectator);
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
            if (shadowSettings.shadowTranslucent()) {
                worldRenderer.renderSectionLayer(net.vulkanmod.render.vertex.TerrainRenderType.TRANSLUCENT,
                        cameraPosition.x, cameraPosition.y, cameraPosition.z, shadowModelView, shadowProjection);
            }
            renderer.endRenderPass();
            shadowRenderPassStarted = false;
            renderEntityAndBlockEntityGeometry(camera, deltaTracker, levelRenderState,
                    entityShadowFrustum, cameraPosition, shadowModelView, shadowProjection,
                    mainModelView, mainProjection, worldRenderer);
        } catch (Exception exception) {
            VeyraClient.LOGGER.error("Could not render the shader pack shadow map.", exception);
        } finally {
            renderingFeatures = false;
            rendering = false;
            if (shadowRenderPassStarted) {
                renderer.endRenderPass();
            }
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
        if (rendering) {
            Renderer.getInstance().endRenderPass();
            rendering = false;
        }
        VRenderSystem.setShaderTexture(3, null);
        depthTextureView = null;
        if (framebuffer != null) {
            framebuffer.cleanUp(false);
            framebuffer = null;
        }
        if (renderPass != null) {
            renderPass.cleanUp();
            renderPass = null;
        }
        if (target != null) {
            target.destroyBuffers();
            target = null;
        }
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
        // The target owns this view. Sampling its native depth view avoids creating a second
        // Vulkan view object for the same image, which can upset VulkanMod's descriptor binding.
        depthTextureView = target.getDepthTextureView();
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
            for (var entity : minecraft.level.entitiesForRendering()) {
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
            if (player != null && submittedEntities.add(player)
                    && entityDispatcher.shouldRender(player, entityShadowFrustum,
                    cameraPosition.x, cameraPosition.y, cameraPosition.z)) {
                EntityRenderState state = entityDispatcher.extractEntity(player, tickDelta);
                entityDispatcher.submit(state, levelRenderState.cameraRenderState,
                        state.x - cameraPosition.x, state.y - cameraPosition.y, state.z - cameraPosition.z,
                        poseStack, submits);
            }

            worldRenderer.renderBlockEntities(poseStack, levelRenderState, submits,
                    new it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<>());
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
        if (lightDirection.y < 0.0F) {
            lightDirection.negate();
        }
        lightDirection.normalize();

        float shadowDistance = shadowSettings.distance();
        Matrix4f modelView = new Matrix4f().setLookAt(
                lightDirection.x * shadowDistance,
                lightDirection.y * shadowDistance,
                lightDirection.z * shadowDistance,
                0.0F, 0.0F, 0.0F,
                0.0F, 0.0F, 1.0F);
        float intervalSize = shadowSettings.intervalSize();
        if (intervalSize > 0.0F) {
            float halfInterval = intervalSize / 2.0F;
            modelView.translate(
                    (float) cameraPosition.x % intervalSize - halfInterval,
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

    private record ShadowView(Matrix4f modelView, float yaw, float pitch) {
    }
}
