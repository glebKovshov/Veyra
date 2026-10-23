package dev.veyra.shader;

import dev.veyra.vulkan.VeyraNormalTextures;
import dev.veyra.vulkan.VeyraEntityNormalDescriptor;
import net.vulkanmod.vulkan.shader.descriptor.ImageDescriptor;
import net.vulkanmod.vulkan.texture.VTextureSelector;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Assigns Veyra's entity normal sampler a texture slot that does not collide with parser-order slots. */
public final class EntitySamplerBindings {
    private EntitySamplerBindings() { }

    public static List<ImageDescriptor> reserveNormalSamplerSlot(List<ImageDescriptor> parserDescriptors) {
        List<ImageDescriptor> descriptors = new ArrayList<>(parserDescriptors);
        int normalIndex = -1;
        Set<Integer> occupied = new HashSet<>();
        for (int index = 0; index < descriptors.size(); index++) {
            ImageDescriptor descriptor = descriptors.get(index);
            if (VeyraNormalTextures.SAMPLER.equals(descriptor.name)) {
                if (normalIndex >= 0) {
                    throw new IllegalStateException("VulkanMod created multiple entity normal samplers.");
                }
                normalIndex = index;
            } else {
                occupied.add(descriptor.imageIdx);
            }
        }
        if (normalIndex < 0) return descriptors;

        ImageDescriptor original = descriptors.get(normalIndex);
        int imageSlot = VeyraNormalTextures.IMAGE_SLOT;
        if (occupied.contains(imageSlot)) {
            imageSlot = -1;
            for (int candidate = 0; candidate < VTextureSelector.SIZE; candidate++) {
                if (!occupied.contains(candidate)) {
                    imageSlot = candidate;
                    break;
                }
            }
            if (imageSlot < 0) {
                throw new IllegalStateException("No free VulkanMod texture slot is available for entity normal maps.");
            }
        }
        if (imageSlot == original.imageIdx && original instanceof VeyraEntityNormalDescriptor) return descriptors;

        ImageDescriptor rebound = new VeyraEntityNormalDescriptor(original.getBinding(), original.qualifier,
                original.name, imageSlot, original.getType());
        rebound.setLayout(original.getLayout());
        rebound.setMipLevel(original.getMipLevel());
        rebound.useSampler = original.useSampler;
        rebound.isReadOnlyLayout = original.isReadOnlyLayout;
        descriptors.set(normalIndex, rebound);
        return descriptors;
    }
}
