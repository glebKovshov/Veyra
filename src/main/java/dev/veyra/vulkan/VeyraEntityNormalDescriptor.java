package dev.veyra.vulkan;

import net.vulkanmod.vulkan.shader.descriptor.ImageDescriptor;
import net.vulkanmod.vulkan.texture.VulkanImage;

/** Entity sampler descriptor that tracks the current material image without a global texture slot lookup. */
public final class VeyraEntityNormalDescriptor extends ImageDescriptor {
    private volatile VulkanImage image;

    public VeyraEntityNormalDescriptor(int binding, String qualifier, String name, int imageIdx, int descriptorType) {
        super(binding, qualifier, name, imageIdx, descriptorType);
    }

    public void bind(VulkanImage image) {
        if (image == null) throw new IllegalArgumentException("Entity normal descriptor image cannot be null.");
        this.image = image;
    }

    @Override
    public VulkanImage getImage() {
        return image;
    }
}
