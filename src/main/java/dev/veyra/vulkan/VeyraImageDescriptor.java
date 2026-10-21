package dev.veyra.vulkan;

import net.vulkanmod.vulkan.shader.descriptor.ImageDescriptor;
import net.vulkanmod.vulkan.texture.VulkanImage;

/** Image descriptor whose resource is owned by Veyra instead of a VulkanMod texture slot. */
public final class VeyraImageDescriptor extends ImageDescriptor {
    private final VulkanImage image;

    public VeyraImageDescriptor(int binding, String glslType, String name,
                                VulkanImage image, boolean storageImage) {
        super(binding, glslType, name, 0,
                storageImage ? org.lwjgl.vulkan.VK10.VK_DESCRIPTOR_TYPE_STORAGE_IMAGE
                        : org.lwjgl.vulkan.VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER);
        this.image = image;
        this.useSampler = !storageImage;
        setLayout(org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_GENERAL);
        isReadOnlyLayout = false;
        if (useSampler) {
            boolean integer = glslType.startsWith("usampler") || glslType.startsWith("isampler");
            boolean linear = !integer && (!(image instanceof VeyraImage3D volume) || volume.supportsLinearFiltering());
            image.setSampler(net.vulkanmod.vulkan.texture.SamplerManager.getSampler(true, linear, 0));
        }
    }

    @Override
    public VulkanImage getImage() {
        return image;
    }
}
