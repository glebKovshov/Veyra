package dev.veyra.vulkan;

import java.util.List;
import net.vulkanmod.vulkan.shader.descriptor.ImageDescriptor;

/** Older pipelines expose their descriptors as a list without a name lookup. */
public final class LegacyImageDescriptors {
    private LegacyImageDescriptors() { }

    public static ImageDescriptor find(List<ImageDescriptor> descriptors, String name) {
        for (ImageDescriptor descriptor : descriptors) {
            if (descriptor.name.equals(name)) return descriptor;
        }
        return null;
    }
}
