package dev.veyra.mixin;

import com.mojang.blaze3d.shaders.ShaderSource;
import com.mojang.blaze3d.shaders.ShaderType;
import dev.veyra.VeyraClient;
import dev.veyra.mixin.accessor.ShaderCompilationKeyAccessor;
import dev.veyra.shader.EntityShaderAdapter;
import dev.veyra.vulkan.VeyraShaderCache;
import net.minecraft.client.renderer.ShaderDefines;
import net.minecraft.resources.Identifier;
import net.vulkanmod.render.engine.VkGpuDevice;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.IOException;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import java.util.Map;
import java.util.Set;

@Mixin(value = VkGpuDevice.class, remap = false)
public abstract class VkGpuDeviceMixin implements VeyraShaderCache {
    @Shadow @Final private Map<Object, String> shaderSrcCache;

    @Inject(method = "getCachedShaderSrc", at = @At("RETURN"), cancellable = true,
            remap = false, require = 1)
    protected void veyra$adaptCrumblingBlockVertexFormat(
            Identifier identifier, ShaderType type, ShaderDefines defines, ShaderSource shaderSource,
            CallbackInfoReturnable<String> callback) {
        if (type != ShaderType.VERTEX
                || !Identifier.withDefaultNamespace("core/rendertype_crumbling").equals(identifier)
                || callback.getReturnValue() == null) {
            return;
        }
        try {
            String adapted = EntityShaderAdapter.adaptBlockVertexFormat(
                    callback.getReturnValue(), "rendertype_crumbling");
            if (Boolean.getBoolean("veyra.graphicsSmoke")) {
                VeyraClient.LOGGER.info("Graphics smoke adapted VulkanMod crumbling UV2 to ivec2: {}",
                        adapted.contains("in ivec2 UV2;"));
            }
            callback.setReturnValue(adapted);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not adapt VulkanMod's BLOCK-format crumbling vertex shader.",
                    exception);
        }
    }

    @Override public void veyra$pruneShaderSources(Set<Identifier> retained) {
        shaderSrcCache.keySet().removeIf(key -> {
            Identifier id = ((ShaderCompilationKeyAccessor) key).veyra$getId();
            return id.getNamespace().equals("veyra") && !retained.contains(id);
        });
    }
}
