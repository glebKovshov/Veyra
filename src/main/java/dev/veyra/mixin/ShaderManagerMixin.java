package dev.veyra.mixin;

import com.mojang.blaze3d.shaders.ShaderType;
import dev.veyra.shader.EntityShaderAdapter;
import dev.veyra.shader.VeyraRenderer;
import net.minecraft.client.renderer.ShaderManager;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.IOException;

@Mixin(ShaderManager.class)
public abstract class ShaderManagerMixin {
    @Inject(method = "apply(Lnet/minecraft/client/renderer/ShaderManager$Configs;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V", at = @At("TAIL"))
    private void veyra$invalidatePipelines(ShaderManager.Configs configs, ResourceManager resourceManager,
                                            ProfilerFiller profiler, CallbackInfo callback) {
        VeyraRenderer.invalidatePipelines();
    }

    @Inject(method = "getShader", at = @At("RETURN"), cancellable = true)
    private void veyra$adaptCrumblingBlockVertexFormat(
            Identifier identifier, ShaderType type, CallbackInfoReturnable<String> callback) {
        if (type != ShaderType.VERTEX
                || !Identifier.withDefaultNamespace("core/rendertype_crumbling").equals(identifier)
                || callback.getReturnValue() == null) {
            return;
        }
        try {
            callback.setReturnValue(EntityShaderAdapter.adaptBlockVertexFormat(
                    callback.getReturnValue(), "rendertype_crumbling"));
        } catch (IOException exception) {
            throw new IllegalStateException("Could not adapt Minecraft's BLOCK-format crumbling vertex shader.",
                    exception);
        }
    }
}
