package dev.veyra.mixin;

import dev.veyra.mixin.accessor.MemoryManagerAccessor;
import dev.veyra.mixin.accessor.NativePipelineRegistryAccessor;
import net.vulkanmod.vulkan.Vulkan;
import net.vulkanmod.vulkan.memory.MemoryManager;
import net.vulkanmod.vulkan.shader.Pipeline;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.List;

@Mixin(value = Vulkan.class, remap = false)
public abstract class VulkanCleanupMixin {
    @Inject(method = "cleanUp", at = @At(value = "INVOKE", target = "Lorg/lwjgl/util/vma/Vma;vmaDestroyAllocator(J)V"))
    private static void veyra$flushDeferredResources(CallbackInfo callback) {
        MemoryManager memory = MemoryManager.getInstance();
        // Legacy command encoders leave their GL-style framebuffer cache alive at device shutdown.
        var framebuffers = dev.veyra.mixin.accessor.LegacyGlFramebufferAccessor.veyra$getFramebuffers();
        List.copyOf(framebuffers.values()).forEach(framebuffer ->
                ((dev.veyra.mixin.accessor.LegacyGlFramebufferAccessor) (Object) framebuffer).veyra$cleanUp(false));
        framebuffers.clear();
        memory.freeAllBuffers();
        // VulkanMod already waited for device idle; its shutdown leaves native caches registered.
        List.copyOf(NativePipelineRegistryAccessor.veyra$getPipelines()).forEach(Pipeline::cleanUp);
        memory.freeAllBuffers();
        List.copyOf(MemoryManagerAccessor.veyra$getImages().values()).forEach(image -> image.doFree());
        for (var buffer : List.copyOf(MemoryManagerAccessor.veyra$getBuffers().values())) {
            MemoryManager.freeBuffer(buffer.getId(), buffer.getAllocation());
            buffer.setId(0);
            buffer.setAllocation(0);
        }
    }
}
