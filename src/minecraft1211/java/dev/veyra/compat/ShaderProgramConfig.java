package dev.veyra.compat;

import java.util.List;
import net.minecraft.resources.ResourceLocation;

/** Stand-in for the parsed core shader config Minecraft added in 1.21.2. */
public record ShaderProgramConfig(ResourceLocation vertex, ResourceLocation fragment, List<Sampler> samplers,
                                  List<Uniform> uniforms, ShaderDefines defines) {
    public record Sampler(String name) { }

    public record Uniform(String name, String type, int count, List<Float> values) { }
}
