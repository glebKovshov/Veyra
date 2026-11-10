package dev.veyra.compat;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.vulkanmod.gl.VkGlTexture;
import net.vulkanmod.vulkan.texture.SamplerManager;
import net.vulkanmod.vulkan.texture.VulkanImage;

/** A VulkanMod image addressed through its OpenGL-style texture id. */
public class VkGpuTexture extends GpuTexture {
    private static final Int2ObjectMap<Borrowed> BORROWED = new Int2ObjectOpenHashMap<>();
    private static final Int2ObjectMap<VkGpuTexture> OWNED = new Int2ObjectOpenHashMap<>();
    protected final int id;
    protected final VkGlTexture glTexture;
    protected boolean closed;
    private boolean modesDirty = true;

    protected VkGpuTexture(String label, TextureFormat format, int width, int height, int mipLevels,
                           int id, VkGlTexture glTexture) {
        super(label, format, width, height, mipLevels);
        this.id = id;
        this.glTexture = glTexture;
        if (!(this instanceof Borrowed)) OWNED.put(id, this);
    }

    /**
     * Wraps a texture id owned by Minecraft or VulkanMod. One wrapper is kept per id and image, so
     * identity comparisons behave like the texture handles of later versions.
     */
    public static VkGpuTexture of(int id) {
        VkGlTexture texture = id <= 0 ? null : VkGlTexture.getTexture(id);
        VulkanImage image = texture == null ? null : texture.getVulkanImage();
        if (image == null) {
            BORROWED.remove(id);
            return null;
        }
        VkGpuTexture owned = OWNED.get(id);
        if (owned != null && !owned.closed) return owned;
        Borrowed cached = BORROWED.get(id);
        if (cached == null || cached.image != image) {
            cached = new Borrowed(id, texture, image);
            BORROWED.put(id, cached);
        }
        return cached;
    }

    public static VkGpuTexture of(AbstractTexture texture) {
        VkGpuTexture wrapped = of(texture.getId());
        if (wrapped == null) throw new IllegalStateException("Texture has no VulkanMod image yet.");
        return wrapped;
    }

    /** The color attachment of {@code target}, like the texture getter of later render targets. */
    public static VkGpuTexture color(com.mojang.blaze3d.pipeline.RenderTarget target) {
        return (VkGpuTexture) ((dev.veyra.accessor.RenderTargetAccess) target).veyra$getColorTexture();
    }

    /** The depth attachment of {@code target}, or null when it has none. */
    public static VkGpuTexture depth(com.mojang.blaze3d.pipeline.RenderTarget target) {
        return (VkGpuTexture) ((dev.veyra.accessor.RenderTargetAccess) target).veyra$getDepthTexture();
    }

    /** Marks the handles of a texture id closed once VulkanMod deletes it, whoever deleted it. */
    public static void deleted(int id) {
        Borrowed borrowed = BORROWED.remove(id);
        if (borrowed != null) borrowed.closed = true;
        VkGpuTexture owned = OWNED.remove(id);
        if (owned != null) owned.closed = true;
        LegacyDraws.forget(id);
    }

    public int glId() { return id; }
    public VulkanImage getVulkanImage() { return glTexture.getVulkanImage(); }

    @Override public void setAddressMode(AddressMode u, AddressMode v) {
        super.setAddressMode(u, v);
        modesDirty = true;
    }

    @Override public void setTextureFilter(FilterMode min, FilterMode mag, boolean mipmaps) {
        super.setTextureFilter(min, mag, mipmaps);
        modesDirty = true;
    }

    /** Applies the texture-owned sampling state to the native image. */
    public void flushModeChanges() {
        if (!modesDirty) return;
        int flags = magFilter == FilterMode.LINEAR ? SamplerManager.LINEAR_FILTERING_BIT : 0;
        if (addressModeU == AddressMode.CLAMP_TO_EDGE) flags |= SamplerManager.CLAMP_BIT;
        if (useMipmaps && getMipLevels() > 1) {
            flags |= SamplerManager.USE_MIPMAPS_BIT;
            if (minFilter == FilterMode.LINEAR) flags |= SamplerManager.MIPMAP_LINEAR_FILTERING_BIT;
        }
        getVulkanImage().updateTextureSampler(getMipLevels() - 1, (byte) flags);
        modesDirty = false;
    }

    @Override public boolean isClosed() { return closed; }

    /**
     * Deletes the texture id, as closing a texture deletes it on later versions; VulkanMod frees the
     * image bound to it through its deferred queue.
     */
    @Override public void close() {
        if (closed) return;
        VkGlTexture.glDeleteTextures(id);
        deleted(id);
    }

    public static TextureFormat textureFormat(int format) {
        return switch (format) {
            case org.lwjgl.vulkan.VK10.VK_FORMAT_R8_UNORM -> TextureFormat.RED8;
            case org.lwjgl.vulkan.VK10.VK_FORMAT_D32_SFLOAT, org.lwjgl.vulkan.VK10.VK_FORMAT_D24_UNORM_S8_UINT,
                    org.lwjgl.vulkan.VK10.VK_FORMAT_D32_SFLOAT_S8_UINT, org.lwjgl.vulkan.VK10.VK_FORMAT_X8_D24_UNORM_PACK32
                    -> TextureFormat.DEPTH32;
            default -> TextureFormat.RGBA8;
        };
    }

    public static int vkFormat(TextureFormat format) {
        return switch (format) {
            case RGBA8 -> org.lwjgl.vulkan.VK10.VK_FORMAT_R8G8B8A8_UNORM;
            case RED8 -> org.lwjgl.vulkan.VK10.VK_FORMAT_R8_UNORM;
            case DEPTH32 -> org.lwjgl.vulkan.VK10.VK_FORMAT_D32_SFLOAT;
        };
    }

    private static final class Borrowed extends VkGpuTexture {
        private final VulkanImage image;

        private Borrowed(int id, VkGlTexture texture, VulkanImage image) {
            super(image.name, textureFormat(image.format), image.width, image.height, image.mipLevels, id, texture);
            this.image = image;
        }

        @Override public boolean isClosed() { return closed || glTexture.getVulkanImage() != image; }
    }
}
