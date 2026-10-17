package dev.veyra.shader;

import com.mojang.blaze3d.pipeline.TextureTarget;
import dev.veyra.VeyraClient;
import dev.veyra.mixin.accessor.CameraAccessor;
import dev.veyra.vulkan.VeyraDepthTextureView;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.phys.Vec3;
import net.vulkanmod.render.chunk.WorldRenderer;
import net.vulkanmod.render.engine.VkGpuTexture;
import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.VRenderSystem;
import net.vulkanmod.vulkan.framebuffer.Framebuffer;
import net.vulkanmod.vulkan.framebuffer.RenderPass;
import net.vulkanmod.vulkan.shader.Uniforms;
import net.vulkanmod.vulkan.texture.VTextureSelector;
import net.vulkanmod.vulkan.util.MappedBuffer;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.nio.ByteBuffer;

import static org.lwjgl.vulkan.VK10.VK_ATTACHMENT_LOAD_OP_CLEAR;
import static org.lwjgl.vulkan.VK10.VK_ATTACHMENT_STORE_OP_STORE;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;

/** Renders VulkanMod's opaque chunk layers into an Iris-compatible shadow depth map. */
public final class VeyraShadowRenderer {
    private static final int SHADOW_RESOLUTION = 1024;
    private static final float SHADOW_HALF_PLANE = 128.0F;
    private static final MappedBuffer SHADOW_MODEL_VIEW_BUFFER = new MappedBuffer(16 * Float.BYTES);
    private static final MappedBuffer SHADOW_PROJECTION_BUFFER = new MappedBuffer(16 * Float.BYTES);

    private static TextureTarget target;
    private static Framebuffer framebuffer;
    private static RenderPass renderPass;
    private static VeyraDepthTextureView depthTextureView;
    private static boolean rendering;

    static {
        Uniforms.mat4f_uniformMap.put("shadowModelView", () -> SHADOW_MODEL_VIEW_BUFFER);
        Uniforms.mat4f_uniformMap.put("shadowProjection", () -> SHADOW_PROJECTION_BUFFER);
        new Matrix4f().get(SHADOW_MODEL_VIEW_BUFFER.buffer.asFloatBuffer());
        new Matrix4f().get(SHADOW_PROJECTION_BUFFER.buffer.asFloatBuffer());
    }

    private VeyraShadowRenderer() {
    }

    public static void initialize() {
        // Trigger the static uniform registrations before VulkanMod compiles Veyra's terrain pipelines.
    }

    public static boolean isRendering() {
        return rendering;
    }

    public static void render(Camera camera, Matrix4f mainModelView, Matrix4f mainProjection) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || !VeyraRenderer.hasShadowPass()) {
            return;
        }

        Vec3 cameraPosition = camera.position();
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
            ShadowView shadowView = createShadowView(sunAngle);
            Matrix4f shadowModelView = shadowView.modelView();
            Matrix4f shadowProjection = new Matrix4f().setOrthoSymmetric(
                    SHADOW_HALF_PLANE * 2.0F, SHADOW_HALF_PLANE * 2.0F, 0.1F, SHADOW_HALF_PLANE * 2.0F, true);
            writeMatrix(shadowModelView, SHADOW_MODEL_VIEW_BUFFER.buffer);
            writeMatrix(shadowProjection, SHADOW_PROJECTION_BUFFER.buffer);

            Frustum shadowFrustum = new Frustum(shadowModelView, shadowProjection);
            shadowFrustum.prepare(cameraPosition.x, cameraPosition.y, cameraPosition.z);
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
        } catch (Exception exception) {
            VeyraClient.LOGGER.error("Could not render the shader pack shadow map.", exception);
        } finally {
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
        if (depthTextureView != null) {
            depthTextureView.close();
            depthTextureView = null;
        }
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
    }

    public static void clearBinding() {
        VRenderSystem.setShaderTexture(3, null);
    }

    private static void ensureResources() {
        if (renderPass != null) {
            return;
        }
        target = new TextureTarget("Veyra shadow map", SHADOW_RESOLUTION, SHADOW_RESOLUTION, true);
        VkGpuTexture colorTexture = (VkGpuTexture) target.getColorTexture();
        VkGpuTexture depthTexture = (VkGpuTexture) target.getDepthTexture();
        framebuffer = Framebuffer.builder(colorTexture.getVulkanImage(), depthTexture.getVulkanImage()).build();
        RenderPass.Builder passBuilder = RenderPass.builder(framebuffer);
        passBuilder.getColorAttachmentInfo().setFinalLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
        passBuilder.getDepthAttachmentInfo()
                .setOps(VK_ATTACHMENT_LOAD_OP_CLEAR, VK_ATTACHMENT_STORE_OP_STORE)
                .setFinalLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
        renderPass = passBuilder.build();
        depthTextureView = new VeyraDepthTextureView(depthTexture);
    }

    private static ShadowView createShadowView(float sunAngle) {
        Vector3f lightDirection = new Vector3f((float) Math.cos(sunAngle), (float) Math.sin(sunAngle), 0.25F);
        if (lightDirection.y < 0.0F) {
            lightDirection.negate();
        }
        lightDirection.normalize();

        Matrix4f modelView = new Matrix4f().setLookAt(
                lightDirection.x * SHADOW_HALF_PLANE,
                lightDirection.y * SHADOW_HALF_PLANE,
                lightDirection.z * SHADOW_HALF_PLANE,
                0.0F, 0.0F, 0.0F,
                0.0F, 0.0F, 1.0F);
        Vector3f viewDirection = lightDirection.negate(new Vector3f());
        float yaw = (float) Math.toDegrees(Math.atan2(-viewDirection.x, viewDirection.z));
        float pitch = (float) Math.toDegrees(-Math.asin(viewDirection.y));
        return new ShadowView(modelView, yaw, pitch);
    }

    private static void writeMatrix(Matrix4f matrix, ByteBuffer buffer) {
        matrix.get(buffer.asFloatBuffer());
    }

    private record ShadowView(Matrix4f modelView, float yaw, float pitch) {
    }
}
