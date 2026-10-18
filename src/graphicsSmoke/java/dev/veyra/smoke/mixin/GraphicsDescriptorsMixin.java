package dev.veyra.smoke.mixin;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.vulkanmod.interfaces.shader.ExtendedRenderPipeline;
import net.vulkanmod.render.engine.VkCommandEncoder;
import net.vulkanmod.vulkan.texture.VTextureSelector;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.HashSet;
import java.util.Set;

/** Diagnostic evidence for native descriptor aliases; excluded from the mod jar. */
@Mixin(value = VkCommandEncoder.class, remap = false)
public abstract class GraphicsDescriptorsMixin {
    @Unique private static final Set<String> veyra$seen = new HashSet<>();
    @Inject(method = "bindPipeline", at = @At("HEAD"), remap = false)
    private void veyra$recordDescriptors(RenderPipeline pipeline, CallbackInfoReturnable<Boolean> ci) {
        if (!pipeline.getLocation().getNamespace().equals("veyra")) return;
        var nativePipeline = ExtendedRenderPipeline.of(pipeline).getPipeline();
        if (nativePipeline == null) return;
        for (var descriptor : nativePipeline.getImageDescriptors()) {
            var image = VTextureSelector.getImage(descriptor.imageIdx);
            String key = pipeline.getLocation() + ":" + descriptor.name;
            if (image != null && veyra$seen.add(key)) LoggerFactory.getLogger("veyra-graphics-smoke").info(
                    "Descriptor evidence: pipeline={} sampler={} index={} image={} handle={} layout={}",
                    pipeline.getLocation(), descriptor.name, descriptor.imageIdx, image.name,
                    Long.toHexString(image.getId()), image.getCurrentLayout());
        }
    }
}
