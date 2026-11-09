package dev.veyra.vulkan;

import com.mojang.blaze3d.textures.GpuTexture;
import net.vulkanmod.vulkan.framebuffer.Framebuffer;
import net.vulkanmod.vulkan.pass.MainPass;

public interface LegacyMainPassAccess extends MainPass {
    Framebuffer getMainFramebuffer();
    GpuTexture getDepthAttachment();
}
