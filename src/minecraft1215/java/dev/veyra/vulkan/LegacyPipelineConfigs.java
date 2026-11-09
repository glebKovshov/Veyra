package dev.veyra.vulkan;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.List;
import static org.lwjgl.vulkan.VK10.*;

public final class LegacyPipelineConfigs {
    private static final LegacyPipelineConfig TERRAIN = LegacyPipelineConfig.fromJson("terrain",
            LegacyShaderLoadUtil.getJsonConfig(LegacyShaderLoadUtil.resolveShaderPath("basic"), "terrain"));
    public static final LegacyPipelineConfig.UB TERRAIN_UB0 = TERRAIN.ubs.getFirst();
    public static final JsonArray TERRAIN_PC = TERRAIN.pushConstantsInfo.deepCopy();
    private LegacyPipelineConfigs() { }

    public static LegacyPipelineConfig clouds(JsonObject json) {
        var config = LegacyPipelineConfig.fromJson("clouds", json);
        var expected = List.of(new LegacyPipelineConfig.Field("vec4", "ColorModulator"),
                new LegacyPipelineConfig.Field("vec4", "FogColor"),
                new LegacyPipelineConfig.Field("float", "FogStart"), new LegacyPipelineConfig.Field("float", "FogEnd"));
        if (config.ubs.size() != 2 || config.ubs.getFirst().binding != 0
                || config.ubs.getLast().binding != 1 || !config.ubs.getLast().fields.equals(expected))
            throw new IllegalArgumentException("VulkanMod's legacy cloud uniform layout changed");
        // The shared cloud adapter reads this same block in both vertex and fragment stages.
        var uniforms = LegacyPipelineConfig.UB.builder(1, VK_SHADER_STAGE_VERTEX_BIT | VK_SHADER_STAGE_FRAGMENT_BIT)
                .addUniform("vec4", "ColorModulator").addUniform("float", "FogCloudsEnd").build();
        return new LegacyPipelineConfig(config.shaderPaths, List.of(config.ubs.getFirst(), uniforms),
                config.imageDescriptors, config.pushConstantsInfo);
    }

    public static String cloudVertex(String source) {
        String marker = "layout(location = 0) in vec3 Position;";
        if (!source.contains(marker) || source.indexOf(marker) != source.lastIndexOf(marker)
                || source.contains("FogCloudsEnd"))
            throw new IllegalArgumentException("VulkanMod's legacy cloud vertex layout changed");
        return source.replace(marker, """
                layout(binding = 1) uniform VeyraCloudUniformBuffer {
                    vec4 ColorModulator;
                    float FogCloudsEnd;
                };
                """ + marker);
    }
}
