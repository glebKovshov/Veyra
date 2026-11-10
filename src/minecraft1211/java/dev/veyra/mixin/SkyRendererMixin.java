package dev.veyra.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.veyra.compat.LegacyDraws;
import dev.veyra.compat.RenderPipeline;
import dev.veyra.compat.RenderPipelines;
import dev.veyra.compat.VanillaRenderTypes;
import dev.veyra.shader.IrisSkyHorizon;
import dev.veyra.shader.VeyraRenderer;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Minecraft 1.21.1 has no sky renderer: the level renderer draws each sky stage with a core shader.
 * Each draw is routed like the render type that later replaced it, as on 1.21.3.
 */
@Mixin(LevelRenderer.class)
public abstract class SkyRendererMixin {
    @Unique private static final String RENDER_SKY = "renderSky(Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;FLnet/minecraft/client/Camera;ZLjava/lang/Runnable;)V";
    @Unique private static final String SET_SHADER = "Lcom/mojang/blaze3d/systems/RenderSystem;setShader(Ljava/util/function/Supplier;)V";
    @Unique private static final String DRAW_BUFFER = "Lcom/mojang/blaze3d/vertex/VertexBuffer;drawWithShader(Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lnet/minecraft/client/renderer/ShaderInstance;)V";
    @Unique private static final String DRAW_MESH = "Lcom/mojang/blaze3d/vertex/BufferUploader;drawWithShader(Lcom/mojang/blaze3d/vertex/MeshData;)V";
    @Unique private VertexBuffer veyra$horizon;
    @Unique private int veyra$horizonDistance;

    // The sky disc, then the stars, then the dark disc below the horizon.
    @WrapOperation(method = RENDER_SKY, at = @At(value = "INVOKE", target = DRAW_BUFFER, ordinal = 0))
    private void veyra$drawSkyDisc(VertexBuffer buffer, Matrix4f modelView, Matrix4f projection, ShaderInstance shader,
                                   Operation<Void> original) {
        ShaderInstance routed = veyra$route(shader, RenderPipelines.sky());
        veyra$drawShaderHorizon(modelView, projection, routed, original);
        buffer.bind();
        original.call(buffer, modelView, projection, routed);
    }

    @WrapOperation(method = RENDER_SKY, at = @At(value = "INVOKE", target = DRAW_BUFFER, ordinal = 1))
    private void veyra$drawStars(VertexBuffer buffer, Matrix4f modelView, Matrix4f projection, ShaderInstance shader,
                                 Operation<Void> original) {
        original.call(buffer, modelView, projection, veyra$route(shader, VanillaRenderTypes.stars()));
    }

    @WrapOperation(method = RENDER_SKY, at = @At(value = "INVOKE", target = DRAW_BUFFER, ordinal = 2))
    private void veyra$drawDarkDisc(VertexBuffer buffer, Matrix4f modelView, Matrix4f projection, ShaderInstance shader,
                                    Operation<Void> original) {
        original.call(buffer, modelView, projection, veyra$route(shader, RenderPipelines.sky()));
    }

    @WrapOperation(method = RENDER_SKY, at = @At(value = "INVOKE", target = SET_SHADER, ordinal = 0))
    private void veyra$routeSunrise(Supplier<ShaderInstance> shader, Operation<Void> original) {
        original.call(shader);
        LegacyDraws.beginProgramDraw(VanillaRenderTypes.sunriseSunset());
    }

    @WrapOperation(method = RENDER_SKY, at = @At(value = "INVOKE", target = SET_SHADER, ordinal = 1))
    private void veyra$routeCelestial(Supplier<ShaderInstance> shader, Operation<Void> original) {
        original.call(shader);
        LegacyDraws.beginProgramDraw(VanillaRenderTypes.celestial());
    }

    // The sunrise strip, the sun and the moon are drawn from built meshes, in that order.
    @WrapOperation(method = RENDER_SKY, at = @At(value = "INVOKE", target = DRAW_MESH, ordinal = 1))
    private void veyra$drawSun(MeshData mesh, Operation<Void> original) {
        VeyraRenderer.beginSkySunStage();
        try { original.call(mesh); }
        finally { VeyraRenderer.endSkyRenderStage(); }
    }

    @WrapOperation(method = RENDER_SKY, at = @At(value = "INVOKE", target = DRAW_MESH, ordinal = 2))
    private void veyra$drawMoon(MeshData mesh, Operation<Void> original) {
        VeyraRenderer.beginSkyMoonStage();
        try { original.call(mesh); }
        finally { VeyraRenderer.endSkyRenderStage(); }
    }

    @WrapOperation(method = "renderEndSky", at = @At(value = "INVOKE", target = SET_SHADER))
    private void veyra$routeEndSky(Supplier<ShaderInstance> shader, Operation<Void> original) {
        original.call(shader);
        LegacyDraws.beginProgramDraw(VanillaRenderTypes.endSky());
    }

    @Inject(method = "renderEndSky", at = @At("HEAD"))
    private void veyra$beginEndSkyStage(CallbackInfo ci) { VeyraRenderer.beginSkyVoidStage(); }

    @Inject(method = "renderEndSky", at = @At("RETURN"))
    private void veyra$endEndSkyStage(CallbackInfo ci) { VeyraRenderer.endSkyRenderStage(); }

    @Unique
    private static ShaderInstance veyra$route(ShaderInstance vanilla, RenderPipeline description) {
        RenderSystem.setShader(() -> vanilla);
        LegacyDraws.beginProgramDraw(description);
        return RenderSystem.getShader();
    }

    // Iris draws a horizon fan below the sky disc; shader packs expect it in the sky basic stage.
    @Unique
    private void veyra$drawShaderHorizon(Matrix4f modelView, Matrix4f projection, ShaderInstance shader,
                                         Operation<Void> draw) {
        if (!VeyraRenderer.isRenderingEnabled() || VeyraRenderer.getSkyBasicPipeline(RenderPipelines.sky()) == null) {
            veyra$releaseHorizon();
            return;
        }
        int distance = Minecraft.getInstance().options.getEffectiveRenderDistance();
        if (veyra$horizon == null || veyra$horizonDistance != distance) {
            veyra$releaseHorizon();
            float[] vertices = IrisSkyHorizon.vertices(distance);
            var builder = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION);
            for (int index = 0; index < vertices.length; index += 3) {
                builder.addVertex(vertices[index], vertices[index + 1], vertices[index + 2]);
            }
            veyra$horizon = new VertexBuffer(VertexBuffer.Usage.STATIC);
            veyra$horizon.bind();
            veyra$horizon.upload(builder.buildOrThrow());
            VertexBuffer.unbind();
            veyra$horizonDistance = distance;
        }
        veyra$horizon.bind();
        draw.call(veyra$horizon, modelView, projection, shader);
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void veyra$closeHorizon(CallbackInfo ci) { veyra$releaseHorizon(); }

    @Unique
    private void veyra$releaseHorizon() {
        if (veyra$horizon != null) {
            veyra$horizon.close();
            veyra$horizon = null;
        }
    }
}
