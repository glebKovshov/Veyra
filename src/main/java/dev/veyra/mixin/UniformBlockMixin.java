package dev.veyra.mixin;

import dev.veyra.shader.ShaderPackStorageBuffers;
import dev.veyra.shader.VeyraStorageBufferResources;
import net.vulkanmod.vulkan.shader.converter.UniformBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = UniformBlock.class, remap = false)
public abstract class UniformBlockMixin {
    @Shadow String name;
    @Shadow int binding;

    @Inject(method = "getStringValue", at = @At("RETURN"), cancellable = true, remap = false)
    private void veyra$restoreStorageBufferDeclaration(CallbackInfoReturnable<String> callback) {
        VeyraStorageBufferResources resources = VeyraStorageBufferResources.active();
        if (resources == null) return;
        ShaderPackStorageBuffers.StorageBlock block = resources.block(name);
        if (block != null) {
            callback.setReturnValue(block.declaration(block.descriptorBinding()));
        }
    }
}
