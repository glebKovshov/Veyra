package dev.veyra.vulkan;

import net.vulkanmod.vulkan.shader.descriptor.ImageDescriptor;
import net.vulkanmod.vulkan.texture.VulkanImage;

/** Image descriptor whose resource is owned by Veyra instead of a VulkanMod texture slot. */
public final class VeyraImageDescriptor extends ImageDescriptor {
    private final VulkanImage image;
    private int colorTargetIndex = -1;

    public VeyraImageDescriptor(int binding, String glslType, String name,
                                VulkanImage image, boolean storageImage) {
        super(binding, glslType, name, 0,
                storageImage ? org.lwjgl.vulkan.VK10.VK_DESCRIPTOR_TYPE_STORAGE_IMAGE
                        : org.lwjgl.vulkan.VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER);
        this.image = image;
        this.useSampler = !storageImage;
        setLayout(org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_GENERAL);
        isReadOnlyLayout = false;
        if (useSampler && image != null) {
            boolean integer = glslType.startsWith("usampler") || glslType.startsWith("isampler");
            boolean linear = !integer && (!(image instanceof VeyraCustomImage customImage)
                    || customImage.supportsLinearFiltering());
            image.setSampler(net.vulkanmod.vulkan.texture.SamplerManager.getSampler(true, linear, 0));
        }
    }

    public static VeyraImageDescriptor colorTarget(int binding, String glslType, String name,
                                                   int index, boolean storageImage) {
        VeyraImageDescriptor descriptor = new VeyraImageDescriptor(binding, glslType, name, null, storageImage);
        descriptor.colorTargetIndex = index;
        return descriptor;
    }

    public int colorTargetIndex() { return colorTargetIndex; }

    public boolean isColorTarget() { return colorTargetIndex >= 0; }

    @Override
    public VulkanImage getImage() {
        VulkanImage resolved = isColorTarget() ? dev.veyra.shader.VeyraRenderer.geometryColorAttachment(colorTargetIndex) : image;
        if (isColorTarget() && useSampler) {
            resolved.setSampler(net.vulkanmod.vulkan.texture.SamplerManager.getSampler(true, true, 0));
        }
        return resolved;
    }
}
