package dev.veyra.mixin.accessor;

import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.framebuffer.Framebuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = Renderer.class, remap = false)
public interface RendererFramebufferAccessor {
    @Accessor("boundFramebuffer") void veyra$setBoundFramebuffer(Framebuffer framebuffer);
}
