package dev.veyra.vulkan;

import net.minecraft.resources.Identifier;
import java.util.Set;

public interface VeyraShaderCache {
    void veyra$pruneShaderSources(Set<Identifier> retained);
}
