package dev.veyra.mixin;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.veyra.VeyraClient;
import dev.veyra.shader.VeyraCloudVertexFormat;
import dev.veyra.shader.VeyraRenderer;
import dev.veyra.vulkan.VeyraCloudTexture;
import net.vulkanmod.render.sky.CloudRenderer;
import net.vulkanmod.vulkan.Renderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = CloudRenderer.class, remap = false)
public abstract class VulkanCloudRendererMixin {
    @Unique private boolean veyra$usesExtendedCloudFormat;

    @Inject(method = "renderClouds", at = @At("HEAD"), remap = false)
    private void veyra$captureAndPrepareCloudMesh(float tickDelta, int renderDistance,
                                                   double cameraX, double cameraY, double cameraZ,
                                                   long ticks, float partialTick, CallbackInfo ci) {
        VeyraRenderer.captureCloudSceneColor();
        if (VeyraRenderer.hasCapturedCloudSceneColor()) {
            Renderer.getInstance().getMainPass().rebindMainTarget();
        }
        VeyraRenderer.getCloudsPipeline();
        boolean extended = VeyraRenderer.requiresExtendedCloudVertexFormat();
        if (this.veyra$usesExtendedCloudFormat != extended) {
            ((CloudRenderer) (Object) this).resetBuffer();
            this.veyra$usesExtendedCloudFormat = extended;
        }
    }

    @Inject(method = "loadTexture", at = @At("TAIL"), remap = false)
    private void veyra$reloadCloudSampler(CallbackInfo ci) {
        try {
            VeyraCloudTexture.reloadIfLoaded();
        } catch (RuntimeException exception) {
            VeyraClient.LOGGER.warn("Could not refresh the Vulkan cloud sampler after a resource reload.", exception);
        }
    }

    @Inject(method = "buildClouds", at = @At("HEAD"), remap = false)
    private void veyra$beginCloudMesh(Tesselator tessellator, int centerX, int centerZ, double cloudY,
                                       CallbackInfoReturnable<MeshData> cir) {
        boolean extended = VeyraRenderer.requiresExtendedCloudVertexFormat();
        VeyraCloudVertexFormat.beginBuild(extended, centerX, centerZ, VeyraCloudTexture.width());
    }

    @Inject(method = "buildClouds", at = @At("RETURN"), remap = false)
    private void veyra$endCloudMesh(Tesselator tessellator, int centerX, int centerZ, double cloudY,
                                     CallbackInfoReturnable<MeshData> cir) {
        VeyraCloudVertexFormat.endBuild();
    }

    @ModifyArg(method = "buildClouds", at = @At(value = "INVOKE", target =
            "Lcom/mojang/blaze3d/vertex/Tesselator;begin(Lcom/mojang/blaze3d/vertex/VertexFormat$Mode;"
                    + "Lcom/mojang/blaze3d/vertex/VertexFormat;)Lcom/mojang/blaze3d/vertex/BufferBuilder;", remap = false),
            index = 1, remap = false)
    private VertexFormat veyra$selectCloudVertexFormat(VertexFormat original) {
        return VeyraRenderer.requiresExtendedCloudVertexFormat()
                ? VeyraCloudVertexFormat.VERTEX_FORMAT : original;
    }

    @Inject(method = "putVertex", at = @At("TAIL"), remap = false)
    private static void veyra$appendCloudAttributes(BufferBuilder builder, float x, float y, float z, int color,
                                                     CallbackInfo ci) {
        VeyraCloudVertexFormat.append(builder, x, y, z);
    }

}
