package dev.veyra.mixin;

import dev.veyra.mixin.accessor.ShaderCompilationKeyAccessor;
import dev.veyra.vulkan.VeyraShaderCache;
import net.minecraft.resources.Identifier;
import net.vulkanmod.render.engine.VkGpuDevice;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import java.util.Map;
import java.util.Set;

@Mixin(value = VkGpuDevice.class, remap = false)
public abstract class VkGpuDeviceMixin implements VeyraShaderCache {
    @Shadow @Final private Map<Object, String> shaderSrcCache;

    @Override public void veyra$pruneShaderSources(Set<Identifier> retained) {
        shaderSrcCache.keySet().removeIf(key -> {
            Identifier id = ((ShaderCompilationKeyAccessor) key).veyra$getId();
            return id.getNamespace().equals("veyra") && !retained.contains(id);
        });
    }
}
