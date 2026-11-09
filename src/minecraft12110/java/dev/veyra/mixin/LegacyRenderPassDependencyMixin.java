package dev.veyra.mixin;

import net.vulkanmod.vulkan.framebuffer.RenderPass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import static org.lwjgl.vulkan.VK10.*;

@Mixin(value = RenderPass.class, remap = false)
abstract class LegacyRenderPassDependencyMixin {
    @ModifyArg(method = "createRenderPass", at = @At(value = "INVOKE", target =
            "Lorg/lwjgl/vulkan/VkSubpassDependency;srcStageMask(I)Lorg/lwjgl/vulkan/VkSubpassDependency;"), index = 0)
    private int veyra$includeDepthWriteStages(int stages) {
        return stages | VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT | VK_PIPELINE_STAGE_LATE_FRAGMENT_TESTS_BIT;
    }

    @ModifyArg(method = "createRenderPass", at = @At(value = "INVOKE", target =
            "Lorg/lwjgl/vulkan/VkSubpassDependency;srcAccessMask(I)Lorg/lwjgl/vulkan/VkSubpassDependency;"), index = 0)
    private int veyra$includeDepthWrites(int accesses) {
        // Final depth layout transitions follow depth stores in late fragment tests.
        return accesses | VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT;
    }
}
