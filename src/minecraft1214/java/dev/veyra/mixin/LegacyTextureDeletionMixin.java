package dev.veyra.mixin;

import dev.veyra.compat.VkGpuTexture;
import java.nio.IntBuffer;
import net.vulkanmod.gl.VkGlTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Texture handles see deletions made through Minecraft's OpenGL-style calls, as closes on later versions. */
@Mixin(value = VkGlTexture.class, remap = false)
public abstract class LegacyTextureDeletionMixin {
    @Inject(method = "glDeleteTextures(I)V", at = @At("RETURN"))
    private static void veyra$forgetTexture(int id, CallbackInfo callback) {
        VkGpuTexture.deleted(id);
    }

    @Inject(method = "glDeleteTextures(Ljava/nio/IntBuffer;)V", at = @At("HEAD"))
    private static void veyra$forgetTextures(IntBuffer ids, CallbackInfo callback) {
        for (int index = ids.position(); index < ids.limit(); index++) VkGpuTexture.deleted(ids.get(index));
    }
}
