package dev.veyra.accessor;

import java.util.List;
import net.vulkanmod.vulkan.shader.descriptor.UBO;

public interface LegacyConvertedShader {
    List<UBO> veyra$storageDescriptors();
    java.util.Map<String, String> veyra$uniformTypes();
}
