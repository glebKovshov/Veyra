package dev.veyra.vulkan;

import com.mojang.blaze3d.textures.GpuTexture;
import java.util.Objects;

/** A borrowed texture range for the pre-view Minecraft API; it never owns the texture. */
public class LegacyTextureView implements AutoCloseable {
    private final GpuTexture texture;
    private final int baseMipLevel;
    private final int mipLevels;
    private boolean closed;

    public LegacyTextureView(GpuTexture texture, int baseMipLevel, int mipLevels) {
        this.texture = Objects.requireNonNull(texture, "texture");
        if (baseMipLevel < 0 || baseMipLevel >= texture.getMipLevels()
                || mipLevels <= 0 || mipLevels > texture.getMipLevels() - baseMipLevel) {
            throw new IllegalArgumentException("Texture view mip range exceeds the underlying texture.");
        }
        this.baseMipLevel = baseMipLevel;
        this.mipLevels = mipLevels;
    }

    public GpuTexture texture() { return texture; }
    public int baseMipLevel() { return baseMipLevel; }
    public int mipLevels() { return mipLevels; }
    public int getWidth(int mip) { return texture.getWidth(absoluteMip(mip)); }
    public int getHeight(int mip) { return texture.getHeight(absoluteMip(mip)); }

    private int absoluteMip(int mip) {
        if (mip < 0 || mip >= mipLevels) throw new IllegalArgumentException("Mip is outside the texture view.");
        return baseMipLevel + mip;
    }

    public GpuTexture textureForBinding() {
        if (isClosed()) throw new IllegalStateException("Cannot bind a closed texture view.");
        if (baseMipLevel != 0 || mipLevels != texture.getMipLevels()) {
            throw new UnsupportedOperationException("Legacy subrange binding requires a native descriptor view.");
        }
        return texture;
    }

    public boolean isClosed() { return closed || texture.isClosed(); }
    @Override public void close() { closed = true; }
}
