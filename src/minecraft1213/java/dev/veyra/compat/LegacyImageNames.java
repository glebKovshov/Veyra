package dev.veyra.compat;

import com.mojang.blaze3d.platform.NativeImage;
import dev.veyra.mixin.accessor.NativeImagePixelsAccessor;
import java.nio.ByteBuffer;
import net.vulkanmod.vulkan.texture.VulkanImage;
import org.lwjgl.system.MemoryUtil;

/** VulkanMod 0.5.3 images carry no debug name and upload only from buffers. */
public final class LegacyImageNames {
    private LegacyImageNames() { }

    public static String of(VulkanImage image) {
        return "VulkanImage@" + Long.toHexString(image.getId());
    }

    /** The pixels of {@code image} as a buffer over its native memory. */
    public static ByteBuffer pixels(NativeImage image) {
        long address = ((NativeImagePixelsAccessor) (Object) image).veyra$getPixels();
        return MemoryUtil.memByteBuffer(address, image.getWidth() * image.getHeight() * image.format().components());
    }
}
