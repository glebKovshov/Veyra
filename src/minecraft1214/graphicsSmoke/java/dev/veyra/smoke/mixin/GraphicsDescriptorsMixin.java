package dev.veyra.smoke.mixin;

import dev.veyra.compat.RenderPipeline;
import dev.veyra.shader.VeyraStorageBufferUBO;
import dev.veyra.vulkan.VeyraMrtFramebuffer;
import dev.veyra.compat.ExtendedRenderPipeline;
import dev.veyra.compat.LegacyDraws;
import net.minecraft.client.renderer.CompiledShaderProgram;
import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.framebuffer.Framebuffer;
import net.vulkanmod.vulkan.texture.VulkanImage;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.HashSet;
import java.util.Set;

/**
 * Diagnostic evidence for native descriptor aliases; excluded from the mod jar. Minecraft 1.21.4
 * binds a program instead of a pipeline; the evidence is taken once Veyra has bound its inputs.
 */
@Mixin(value = LegacyDraws.class, remap = false)
public abstract class GraphicsDescriptorsMixin {
    @Unique private static final Set<String> veyra$seen = new HashSet<>();
    @Inject(method = "beforeBind", at = @At("TAIL"), remap = false)
    private static void veyra$recordDescriptors(CompiledShaderProgram program, CallbackInfo ci) {
        RenderPipeline pipeline = LegacyDraws.pipeline(program);
        if (pipeline == null || !pipeline.getFragmentShader().getNamespace().equals("veyra")) return;
        var nativePipeline = ExtendedRenderPipeline.of(pipeline).getPipeline();
        if (nativePipeline == null) return;
        var renderer = Renderer.getInstance();
        var boundFramebuffer = renderer.getBoundFramebuffer();
        var boundRenderPass = renderer.getBoundRenderPass();
        boolean shadowMrt = pipeline.getLocation().toString().startsWith("veyra:pipeline/shadow_entities/")
                && boundFramebuffer instanceof VeyraMrtFramebuffer;
        String framebufferKey = "framebuffer:" + pipeline.getLocation()
                + (shadowMrt ? ":" + System.identityHashCode(boundFramebuffer) : "");
        if (veyra$seen.add(framebufferKey)) {
            LoggerFactory.getLogger("veyra-graphics-smoke").info(
                    "Framebuffer evidence: pipeline={} bound={} renderPassTarget={}",
                    pipeline.getLocation(), veyra$describe(boundFramebuffer),
                    boundRenderPass == null ? "none" : veyra$describe(boundRenderPass.getFramebuffer()));
        }
        for (var descriptor : nativePipeline.getImageDescriptors()) {
            VulkanImage image = descriptor.getImage();
            String key = pipeline.getLocation() + ":" + descriptor.name;
            if (image != null && veyra$seen.add(key)) LoggerFactory.getLogger("veyra-graphics-smoke").info(
                    "Descriptor evidence: pipeline={} sampler={} binding={} type={} image={} handle={} layout={} view={} sampledView={}",
                    pipeline.getLocation(), descriptor.name, descriptor.getBinding(), descriptor.getType(), image.name,
                    Long.toHexString(image.getId()), image.getCurrentLayout(),
                    Long.toHexString(image.getImageView()), Long.toHexString(descriptor.getImageView(image)));
        }
        // A pass that samples the image it is drawing into reads undefined data (Vulkan feedback loop).
        VulkanImage target = boundRenderPass == null || boundRenderPass.getFramebuffer() == null
                ? null : boundRenderPass.getFramebuffer().getColorAttachment();
        if (target != null) for (var descriptor : nativePipeline.getImageDescriptors()) {
            VulkanImage image = descriptor.getImage();
            if (image != null && image.getId() == target.getId()
                    && veyra$seen.add("feedback:" + pipeline.getLocation() + ":" + descriptor.name + ":" + target.getId()))
                LoggerFactory.getLogger("veyra-graphics-smoke").warn("FEEDBACK pipeline={} sampler={} image={}",
                        pipeline.getLocation(), descriptor.name, veyra$image(image));
        }
        for (var buffer : dev.veyra.vulkan.VeyraUniformBlocks.buffers(nativePipeline)) {
            if (!(buffer instanceof VeyraStorageBufferUBO storage)) continue;
            String key = pipeline.getLocation() + ":storage:" + dev.veyra.vulkan.VeyraUniformBlocks.description(buffer);
            if (veyra$seen.add(key)) LoggerFactory.getLogger("veyra-graphics-smoke").info(
                    "Storage buffer evidence: pipeline={} buffer={} binding={} type={} stages={} global={} handle={} offset={} range={}",
                    pipeline.getLocation(), dev.veyra.vulkan.VeyraUniformBlocks.description(buffer), buffer.getBinding(), buffer.getType(), buffer.getStages(),
                    false, Long.toHexString(storage.bufferId()), 0, storage.getSize());
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
