package dev.veyra.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.buffers.BufferUsage;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.veyra.compat.LegacyDraws;
import dev.veyra.compat.RenderPipeline;
import dev.veyra.compat.RenderPipelines;
import dev.veyra.compat.VanillaRenderTypes;
import dev.veyra.shader.IrisSkyHorizon;
import dev.veyra.shader.VeyraRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.CompiledShaderProgram;
import net.minecraft.client.renderer.ShaderProgram;
import net.minecraft.client.renderer.SkyRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Minecraft 1.21.3 draws each sky stage by binding a core shader program; later versions use
 * render types. Each program bind is routed like the render type that replaced it.
 */
@Mixin(SkyRenderer.class)
public abstract class SkyRendererMixin {
    @Unique private static final String SET_SHADER = "Lcom/mojang/blaze3d/systems/RenderSystem;setShader("
            + "Lnet/minecraft/client/renderer/ShaderProgram;)Lnet/minecraft/client/renderer/CompiledShaderProgram;";
    @Unique private VertexBuffer veyra$horizon;
    @Unique private int veyra$horizonDistance;

    @WrapOperation(method = {"renderSkyDisc", "renderDarkDisc"}, at = @At(value = "INVOKE", target = SET_SHADER))
    private CompiledShaderProgram veyra$routeSky(ShaderProgram program, Operation<CompiledShaderProgram> original) {
        return veyra$route(original.call(program), RenderPipelines.sky());
    }

    @WrapOperation(method = "renderStars", at = @At(value = "INVOKE", target = SET_SHADER))
    private CompiledShaderProgram veyra$routeStars(ShaderProgram program, Operation<CompiledShaderProgram> original) {
        return veyra$route(original.call(program), VanillaRenderTypes.stars());
    }

    @WrapOperation(method = {"renderSun", "renderMoon"}, at = @At(value = "INVOKE", target = SET_SHADER))
    private CompiledShaderProgram veyra$routeCelestial(ShaderProgram program, Operation<CompiledShaderProgram> original) {
        return veyra$route(original.call(program), VanillaRenderTypes.celestial());
    }

    @WrapOperation(method = "renderSunriseAndSunset", at = @At(value = "INVOKE", target = SET_SHADER))
    private CompiledShaderProgram veyra$routeSunrise(ShaderProgram program, Operation<CompiledShaderProgram> original) {
        return veyra$route(original.call(program), VanillaRenderTypes.sunriseSunset());
    }

    @WrapOperation(method = "renderEndSky", at = @At(value = "INVOKE", target = SET_SHADER))
    private CompiledShaderProgram veyra$routeEndSky(ShaderProgram program, Operation<CompiledShaderProgram> original) {
        return veyra$route(original.call(program), VanillaRenderTypes.endSky());
    }

    @Unique
    private static CompiledShaderProgram veyra$route(CompiledShaderProgram vanilla, RenderPipeline description) {
        LegacyDraws.beginProgramDraw(description);
        return com.mojang.blaze3d.systems.RenderSystem.getShader();
    }

    // Iris draws a horizon fan below the sky disc; shader packs expect it in the sky basic stage.
    @WrapOperation(method = "renderSkyDisc", at = @At(value = "INVOKE", target =
            "Lcom/mojang/blaze3d/vertex/VertexBuffer;drawWithShader(Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;"
                    + "Lnet/minecraft/client/renderer/CompiledShaderProgram;)V"))
    private void veyra$drawShaderHorizon(VertexBuffer buffer, Matrix4f modelView, Matrix4f projection,
            CompiledShaderProgram program, Operation<Void> original) {
        if (VeyraRenderer.isRenderingEnabled() && VeyraRenderer.getSkyBasicPipeline(RenderPipelines.sky()) != null) {
            int distance = Minecraft.getInstance().options.getEffectiveRenderDistance();
            if (veyra$horizon == null || veyra$horizonDistance != distance) {
                veyra$releaseHorizon();
                float[] vertices = IrisSkyHorizon.vertices(distance);
                var builder = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION);
                for (int index = 0; index < vertices.length; index += 3) {
                    builder.addVertex(vertices[index], vertices[index + 1], vertices[index + 2]);
                }
                veyra$horizon = new VertexBuffer(BufferUsage.STATIC_WRITE);
                veyra$horizon.bind();
                veyra$horizon.upload(builder.buildOrThrow());
                VertexBuffer.unbind();
                veyra$horizonDistance = distance;
            }
            original.call(veyra$horizon, modelView, projection, program);
        } else {
            veyra$releaseHorizon();
        }
        original.call(buffer, modelView, projection, program);
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

    @Inject(method = "renderSun", at = @At("HEAD"))
    private void veyra$beginSunStage(CallbackInfo ci) { VeyraRenderer.beginSkySunStage(); }

    @Inject(method = "renderSun", at = @At("RETURN"))
    private void veyra$endSunStage(CallbackInfo ci) { VeyraRenderer.endSkyRenderStage(); }

    @Inject(method = "renderMoon", at = @At("HEAD"))
    private void veyra$beginMoonStage(CallbackInfo ci) { VeyraRenderer.beginSkyMoonStage(); }

    @Inject(method = "renderMoon", at = @At("RETURN"))
    private void veyra$endMoonStage(CallbackInfo ci) { VeyraRenderer.endSkyRenderStage(); }

    @Inject(method = "renderEndSky", at = @At("HEAD"))
    private void veyra$beginEndSkyStage(CallbackInfo ci) { VeyraRenderer.beginSkyVoidStage(); }

    @Inject(method = "renderEndSky", at = @At("RETURN"))
    private void veyra$endEndSkyStage(CallbackInfo ci) { VeyraRenderer.endSkyRenderStage(); }
}
