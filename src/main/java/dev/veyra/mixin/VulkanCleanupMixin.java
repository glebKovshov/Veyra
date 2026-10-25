package dev.veyra.mixin;

import net.vulkanmod.vulkan.Vulkan;
import net.vulkanmod.vulkan.memory.MemoryManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Vulkan.class, remap = false)
public abstract class VulkanCleanupMixin {
    @Inject(method = "cleanUp", at = @At(value = "INVOKE", target = "Lorg/lwjgl/util/vma/Vma;vmaDestroyAllocator(J)V"))
    private static void veyra$flushDeferredResources(CallbackInfo callback) {
        MemoryManager.getInstance().freeAllBuffers();
    }
}
