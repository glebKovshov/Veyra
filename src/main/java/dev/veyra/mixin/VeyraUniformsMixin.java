package dev.veyra.mixin;

import dev.veyra.shader.VeyraRenderer;
import net.vulkanmod.vulkan.shader.Uniforms;
import net.vulkanmod.vulkan.util.MappedBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Supplier;

@Mixin(value = Uniforms.class, remap = false)
public abstract class VeyraUniformsMixin {
    @Inject(method = "getUniformSupplier", at = @At("HEAD"), cancellable = true, remap = false)
    private static void veyra$provideCameraIntegerVectors(
            String type, String name, CallbackInfoReturnable<Supplier<MappedBuffer>> callback) {
        if (type.equals("ivec2")) {
            MappedBuffer buffer = VeyraRenderer.shaderPackIntegerVector2Uniform(name);
            if (buffer != null) {
                callback.setReturnValue(() -> buffer);
            }
            return;
        }
        if (!type.equals("ivec3")) {
            return;
        }
        MappedBuffer buffer = VeyraRenderer.shaderPackIntegerVectorUniform(name);
        if (buffer != null) {
            callback.setReturnValue(() -> buffer);
        }
    }
}
