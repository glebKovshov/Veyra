package dev.veyra.vulkan;

import net.vulkanmod.vulkan.shader.descriptor.ImageDescriptor;

/** Keeps Veyra-owned image descriptors on the native backend's constructor contract. */
public abstract class VeyraImageDescriptorBase extends ImageDescriptor {
    protected VeyraImageDescriptorBase(int binding, String qualifier, String name, int imageIdx, int descriptorType) {
        super(binding, qualifier, name, imageIdx, descriptorType);
    }
}
