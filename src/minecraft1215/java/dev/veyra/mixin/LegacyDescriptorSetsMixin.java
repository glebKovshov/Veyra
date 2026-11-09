package dev.veyra.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.veyra.mixin.accessor.NativePipelineAccessor;
import dev.veyra.vulkan.LegacyDescriptorBuffers;
import dev.veyra.vulkan.VeyraStorageDescriptor;
import java.nio.IntBuffer;
import java.util.List;
import net.vulkanmod.vulkan.memory.buffer.UniformBuffer;
import net.vulkanmod.vulkan.shader.Pipeline;
import net.vulkanmod.vulkan.shader.descriptor.UBO;
import org.lwjgl.vulkan.VkDescriptorPoolSize;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.vulkanmod.vulkan.shader.Pipeline$DescriptorSets", remap = false)
public abstract class LegacyDescriptorSetsMixin {
    @Shadow @Final private Pipeline pipeline;
    @Shadow @Final private IntBuffer dynamicOffsets;
    @Unique private List<UBO> veyra$orderedBuffers;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void veyra$cacheBindingOrder(Pipeline pipeline, CallbackInfo callback) {
        veyra$orderedBuffers = LegacyDescriptorBuffers.ordered(((NativePipelineAccessor) pipeline).veyra$getUniformBuffers());
    }

    @Inject(method = "updateUniforms", at = @At("HEAD"), cancellable = true)
    private void veyra$uploadUniforms(UniformBuffer global, CallbackInfo callback) {
        LegacyDescriptorBuffers.updateUniforms(veyra$orderedBuffers, dynamicOffsets, global);
        callback.cancel();
    }

    @WrapOperation(method = {"needsUpdate", "updateDescriptorSet"}, at = @At(value = "INVOKE", target =
            "Lnet/vulkanmod/vulkan/memory/buffer/UniformBuffer;getId()J"))
    private long veyra$bufferHandle(UniformBuffer buffer, Operation<Long> original, @Local UBO block) {
        return block instanceof VeyraStorageDescriptor storage ? storage.bufferId() : original.call(buffer);
    }

    @WrapOperation(method = "createDescriptorPool", at = @At(value = "INVOKE", target =
            "Lorg/lwjgl/vulkan/VkDescriptorPoolSize;type(I)Lorg/lwjgl/vulkan/VkDescriptorPoolSize;"))
    private VkDescriptorPoolSize veyra$poolType(VkDescriptorPoolSize size, int type,
            Operation<VkDescriptorPoolSize> original, @Local(index = 4) int index) {
        return original.call(size, LegacyDescriptorBuffers.poolType(((NativePipelineAccessor) pipeline).veyra$getUniformBuffers(),
                pipeline.getImageDescriptors(), index));
    }
}
