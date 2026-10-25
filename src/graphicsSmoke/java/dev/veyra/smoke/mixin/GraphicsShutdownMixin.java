package dev.veyra.smoke.mixin;
import dev.veyra.smoke.ShutdownEvidence;
import net.vulkanmod.vulkan.Vulkan;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=Vulkan.class,remap=false)
public abstract class GraphicsShutdownMixin {
    @Inject(method="cleanUp",at=@At(value="INVOKE",target="Lorg/lwjgl/vulkan/VK10;vkDeviceWaitIdle(Lorg/lwjgl/vulkan/VkDevice;)I",shift=At.Shift.AFTER))
    private static void beforeCleanup(CallbackInfo ci){ShutdownEvidence.beginCleanup();ShutdownEvidence.record("before-backend-cleanup");}
    @Inject(method="cleanUp",at=@At(value="INVOKE",target="Lorg/lwjgl/util/vma/Vma;vmaDestroyAllocator(J)V"))
    private static void afterCleanup(CallbackInfo ci){ShutdownEvidence.record("before-allocator-destruction");}
}
