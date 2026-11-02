package dev.veyra.mixin;

import net.vulkanmod.render.engine.VkGpuBuffer;
import net.vulkanmod.vulkan.memory.MemoryManager;
import net.vulkanmod.vulkan.memory.buffer.Buffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = VkGpuBuffer.class, remap = false)
public abstract class VkGpuBufferMixin {
    @Redirect(method = "close", at = @At(value = "INVOKE", target =
            "Lnet/vulkanmod/vulkan/memory/MemoryManager;addToFreeable(Lnet/vulkanmod/vulkan/memory/buffer/Buffer;)V"))
    private void veyra$retirePendingUploads(MemoryManager memory, Buffer buffer) {
        // Initial uploads can submit after close; retire after the frame callback, not its preceding free pass.
        memory.addFrameOp(() -> memory.addToFreeable(buffer));
    }
}
