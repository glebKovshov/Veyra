package dev.veyra.smoke.mixin;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import dev.veyra.vulkan.VeyraMrtFramebuffer;
import net.vulkanmod.interfaces.shader.ExtendedRenderPipeline;
import net.vulkanmod.render.engine.VkCommandEncoder;
import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.framebuffer.Framebuffer;
import net.vulkanmod.vulkan.texture.VulkanImage;
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
        if (!pipeline.getFragmentShader().getNamespace().equals("veyra")) return;
        var nativePipeline = ExtendedRenderPipeline.of(pipeline).getPipeline();
        if (nativePipeline == null) return;
        var renderer = Renderer.getInstance();
        var boundFramebuffer = renderer.getBoundFramebuffer();
        var boundRenderPass = renderer.getBoundRenderPass();
        if (veyra$seen.add("framebuffer:" + pipeline.getLocation())) {
            LoggerFactory.getLogger("veyra-graphics-smoke").info(
                    "Framebuffer evidence: pipeline={} bound={} renderPassTarget={}",
                    pipeline.getLocation(), veyra$describe(boundFramebuffer),
                    boundRenderPass == null ? "none" : veyra$describe(boundRenderPass.getFramebuffer()));
        }
        for (var descriptor : nativePipeline.getImageDescriptors()) {
            var image = VTextureSelector.getImage(descriptor.imageIdx);
            String key = pipeline.getLocation() + ":" + descriptor.name;
            if (image != null && veyra$seen.add(key)) LoggerFactory.getLogger("veyra-graphics-smoke").info(
                    "Descriptor evidence: pipeline={} sampler={} index={} image={} handle={} layout={}",
                    pipeline.getLocation(), descriptor.name, descriptor.imageIdx, image.name,
                    Long.toHexString(image.getId()), image.getCurrentLayout());
        }
    }

    @Unique private static String veyra$describe(Framebuffer framebuffer) {
        if (framebuffer == null) return "none";
        if (framebuffer instanceof VeyraMrtFramebuffer mrt) {
            return "MRT[" + String.join(",", mrt.colors().stream().map(GraphicsDescriptorsMixin::veyra$image).toList()) + "]";
        }
        VulkanImage color = framebuffer.getColorAttachment();
        return framebuffer.getClass().getSimpleName() + "[" + veyra$image(color) + "]";
    }

    @Unique private static String veyra$image(VulkanImage image) {
        return image == null ? "none" : image.name + "@" + Long.toHexString(image.getId())
                + "/layout=" + image.getCurrentLayout();
    }
}
