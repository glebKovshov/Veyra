package dev.veyra.vulkan;

import net.vulkanmod.vulkan.shader.descriptor.ImageDescriptor;
import static org.lwjgl.vulkan.VK10.*;

public abstract class VeyraImageDescriptorBase extends ImageDescriptor {
    protected VeyraImageDescriptorBase(int binding, String qualifier, String name, int imageIdx, int descriptorType) {
        super(binding, qualifier, name, imageIdx, storage(descriptorType));
    }
    private static boolean storage(int descriptorType) {
        return switch (descriptorType) {
            case VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER -> false;
            case VK_DESCRIPTOR_TYPE_STORAGE_IMAGE -> true;
            default -> throw new IllegalArgumentException("Unsupported legacy image descriptor type: " + descriptorType);
        };
    }
}
