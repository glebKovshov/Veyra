package dev.veyra.vulkan;

import com.google.gson.JsonObject;
import java.util.List;
import net.vulkanmod.vulkan.shader.PipelineConfig;
import static org.lwjgl.vulkan.VK10.VK_SHADER_STAGE_FRAGMENT_BIT;
import static org.lwjgl.vulkan.VK10.VK_SHADER_STAGE_VERTEX_BIT;

/**
 * VulkanMod 0.6.7 for 1.21.1 ships the older cloud shader, whose fog block is fragment-only and has
 * no cloud fog end. The shared cloud adapter reads one block with that field in both stages.
 */
public final class VeyraCloudConfigs {
    private static final List<PipelineConfig.Uniform> NATIVE_FRAGMENT_BLOCK = List.of(
            new PipelineConfig.Uniform("vec4", "ColorModulator"), new PipelineConfig.Uniform("vec4", "FogColor"),
            new PipelineConfig.Uniform("float", "FogStart"), new PipelineConfig.Uniform("float", "FogEnd"));

    private VeyraCloudConfigs() { }

    public static PipelineConfig clouds(JsonObject json) {
        PipelineConfig nativeConfig = PipelineConfig.fromJson("clouds", json);
        if (nativeConfig.ubs.size() != 2 || nativeConfig.ubs.getFirst().binding != 0 || nativeConfig.ubs.getLast().binding != 1
                || !nativeConfig.ubs.getLast().uniforms.equals(NATIVE_FRAGMENT_BLOCK)) {
            throw new IllegalArgumentException("VulkanMod's cloud uniform layout changed");
        }
        PipelineConfig.UB uniforms = PipelineConfig.UB.builder(1, VK_SHADER_STAGE_VERTEX_BIT | VK_SHADER_STAGE_FRAGMENT_BIT)
                .addUniform("vec4", "ColorModulator").addUniform("float", "FogCloudsEnd").build();
        return new PipelineConfig(nativeConfig.shaderPaths, List.of(nativeConfig.ubs.getFirst(), uniforms),
                nativeConfig.imageDescriptors, nativeConfig.pushConstantsInfo);
    }

    public static String cloudVertex(String source) {
        String marker = "layout(location = 0) in vec3 Position;";
        if (!source.contains(marker) || source.indexOf(marker) != source.lastIndexOf(marker)
                || source.contains("FogCloudsEnd")) {
            throw new IllegalArgumentException("VulkanMod's cloud vertex layout changed");
        }
        return source.replace(marker, """
                layout(binding = 1) uniform VeyraCloudUniformBuffer {
                    vec4 ColorModulator;
                    float FogCloudsEnd;
                };
                """ + marker);
    }
}
