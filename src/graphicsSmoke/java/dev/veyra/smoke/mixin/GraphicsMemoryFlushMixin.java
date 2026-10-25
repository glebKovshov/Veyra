package dev.veyra.smoke.mixin;

import dev.veyra.smoke.ShutdownEvidence;
import net.vulkanmod.vulkan.memory.MemoryManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = MemoryManager.class, remap = false)
public abstract class GraphicsMemoryFlushMixin {
    @Inject(method = "freeAllBuffers", at = @At("TAIL"))
    private void veyra$recordShutdownFlush(CallbackInfo callback) {
        ShutdownEvidence.recordCleanupFlush();
    }
}