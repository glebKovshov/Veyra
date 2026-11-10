package dev.veyra.compat;

import com.mojang.blaze3d.platform.NativeImage;

/** The subset of the 1.21.5 command encoder API that Veyra uses. */
public interface CommandEncoder {
    void writeToTexture(GpuTexture texture, NativeImage image);
    void clearDepthTexture(GpuTexture depth, double value);
}
