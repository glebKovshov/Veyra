package dev.veyra.shader;

import java.nio.file.*;
import java.util.*;
import static org.lwjgl.util.shaderc.Shaderc.*;

public class ShaderCompileSmoke {
    public static void main(String[] args) throws Exception {
        long compiler = shaderc_compiler_initialize();
        long options = shaderc_compile_options_initialize();
        shaderc_compile_options_set_target_env(options, shaderc_target_env_vulkan, shaderc_env_version_vulkan_1_0);
        shaderc_compile_options_set_auto_bind_uniforms(options, true);
        shaderc_compile_options_set_auto_map_locations(options, true);
        int count = 0;
        int compiled = 0;
        try {
            try (var files = Files.walk(Path.of("examples"))) {
                for (Path path : files.filter(Files::isRegularFile).filter(p -> p.getFileName().toString().matches(
                        "gbuffers_(terrain(?:_solid|_cutout)?|water|entities(?:_translucent)?|hand|clouds)\\.fsh")).sorted().toList()) {
                    String name = path.getFileName().toString();
                    String source = ShaderIncludes.expand(name, Files.readString(path), included -> {
                        Path file = path.getParent().resolve(included);
                        return Files.exists(file) ? Optional.of(Files.readString(file)) : Optional.empty();
                    });
                    source = GlslPreprocessor.process(name, source);
                    Path properties = path.getParent().resolve("shaders.properties");
                    CustomUniforms.activate(Files.exists(properties)
                            ? CustomUniforms.parse(Files.readString(properties)) : CustomUniforms.EMPTY);
                    String adapted = name.equals("gbuffers_clouds.fsh") ? CloudShaderAdapter.adaptFragment(source)
                            : name.startsWith("gbuffers_entities") || name.equals("gbuffers_hand.fsh")
                            ? EntityShaderAdapter.adaptFragment(source, name.substring(0, name.length()-4),
                            name.equals("gbuffers_hand.fsh"), Files.exists(path.getParent().resolve("shadow.fsh")))
                            : TerrainShaderAdapter.adaptFragment(source, true, name.equals("gbuffers_water.fsh"));
                    long preprocessed = shaderc_compile_into_preprocessed_text(compiler, adapted, shaderc_fragment_shader,
                            path.toString(), "main", options);
                    try {
                        if (shaderc_result_get_compilation_status(preprocessed) != shaderc_compilation_status_success)
                            throw new AssertionError(shaderc_result_get_error_message(preprocessed));
                    } finally { shaderc_result_release(preprocessed); }
                    // Entity/cloud uniforms require VulkanMod's separate descriptor/UBO conversion.
                    if (!java.util.regex.Pattern.compile("(?m)^uniform\\s+(?!sampler)").matcher(adapted).find()) {
                        compile(compiler, options, adapted, path.toString(), true);
                        compiled++;
                    }
                    count++;
                }
            }
            String entityShadow = EntityShaderAdapter.adaptFragment("""
                    #version 120
                    uniform sampler2DShadow shadowtex0;
                    varying vec2 texcoord;
                    float sampleShadow(sampler2DShadow depthSampler, vec3 coordinate) {
                        return texture2DShadow(depthSampler, coordinate);
                    }
                    void main() {
                        gl_FragColor = vec4(sampleShadow(shadowtex0, vec3(texcoord, 0.5)));
                    }
                    """, "gbuffers_entities", false, true);
            compile(compiler, options, entityShadow, "entity-shadow-sampler", true);
            String entityOpaqueShadow = EntityShaderAdapter.adaptFragment("""
                    uniform sampler2DShadow shadowtex1;
                    float readDepth(sampler2DShadow sourceSampler) { return texture2DShadow(sourceSampler, vec3(0.5)); }
                    void main() { gl_FragColor = vec4(readDepth(shadowtex1)); }
                    """, "gbuffers_entities", false, true);
            compile(compiler, options, entityOpaqueShadow, "entity-opaque-shadow-comparison", true);
            String entityIdentifiers = EntityShaderAdapter.adaptFragment("""
                    #version 120
                    uniform int entityId;
                    uniform int blockEntityId;
                    uniform int currentRenderedItemId;
                    void main() {
                        gl_FragColor = vec4(float(entityId), float(blockEntityId),
                                float(currentRenderedItemId), 1.0);
                    }
                    """, "gbuffers_entities", false, false, 12, 83, 44);
            compile(compiler, options, entityIdentifiers, "entity-identifiers", true);
            String entityColor = EntityShaderAdapter.adaptFragment("""
                    #version 120
                    uniform vec4 entityColor;
                    void main() { gl_FragColor = entityColor; }
                    """, "gbuffers_entities");
            compile(compiler, options, entityColor, "entity-color-overlay", true);
            String itemEntityColor = EntityShaderAdapter.adaptFragment("""
                    #version 120
                    uniform vec4 entityColor;
                    void main() { gl_FragColor = entityColor; }
                    """, "gbuffers_entities", false, false,
                    -1, -1, ItemIdMap.UNMAPPED, false);
            compile(compiler, options, itemEntityColor, "item-entity-color-fallback", true);
            String entityLightmap = EntityShaderAdapter.adaptFragment("""
                    #version 120
                    varying vec2 lmCoord;
                    void main() { gl_FragColor = vec4(lmCoord, 0.0, 1.0); }
                    """, "gbuffers_entities");
            compile(compiler, options, entityLightmap, "entity-lightmap-alias", true);
            String shadowItemIdentifier = EntityShaderAdapter.adaptFragment("""
                    uniform int currentRenderedItemId;
                    void main() { gl_FragColor = vec4(float(currentRenderedItemId)); }
                    """, "shadow_entities", false, false, -1, -1, 44);
            compile(compiler, options, shadowItemIdentifier, "shadow-item-identifier", true);
            String cloudFog = CloudShaderAdapter.adaptFragment("""
                    uniform vec3 fogColor;
                    uniform float fogStart;
                    uniform float fogEnd;
                    void main() {
                        float fog = clamp((fogEnd - 1.0) / max(fogEnd - fogStart, 1.0), 0.0, 1.0);
                        gl_FragColor = vec4(mix(vec3(0.25), fogColor, fog), 1.0);
                    }
                    """);
            compile(compiler, options, cloudFog, "cloud-fog-uniforms", true);
            Path cloudVertexPath = Path.of("examples/gbuffers-clouds/shaders/gbuffers_clouds.vsh");
            String cloudPackVertex = GlslPreprocessor.process("gbuffers_clouds.vsh", Files.readString(cloudVertexPath));
            String cloudVertex = CloudShaderAdapter.adaptVertex("""
                    #version 450
                    layout(binding = 0) uniform UBO0 { mat4 MVP; vec3 ModelOffset; };
                    layout(binding = 1) uniform UBO { vec4 ColorModulator; float FogCloudsEnd; };
                    layout(location = 0) in vec3 Position;
                    layout(location = 1) in vec4 Color;
                    layout(location = 0) out vec4 vertexColor;
                    layout(location = 1) out float vertexDistance;
                    void main() {
                        gl_Position = MVP * vec4(Position, 1.0);
                        vertexColor = Color * ColorModulator;
                        vertexDistance = length(Position + ModelOffset);
                    }
                    """, cloudPackVertex);
            compileVertex(compiler, options, cloudVertex, "cloud-vertex-adapter", true);
            String terrainVertex = TerrainShaderAdapter.adaptVertex("""
                    #version 460
                    layout(binding = 0) uniform UBO0 { mat4 MVP; };
                    layout(binding = 1) uniform UBO {
                        int UseRgss;
                    };
                    layout(binding = 4) uniform sampler2D Sampler2;
                    layout(location = 0) in ivec4 Position;
                    layout(location = 1) in uvec2 UV0;
                    layout (location = 2) in uint PackedColor;
                    layout(location = 0) out vec4 vertexColor;
                    layout(location = 1) out vec2 texCoord0;
                    layout (location = 4) out flat float fadeFactor;
                    const float UV_INV = 1.0 / 32768.0;
                    vec3 getVertexPosition() { return vec3(Position.xyz); }
                    vec4 sample_lightmap2(sampler2D sourceSampler, int coordinates) {
                        return texture(sourceSampler, vec2(float(coordinates)));
                    }
                    void main() {
                        const vec3 pos = getVertexPosition();
                        gl_Position = MVP * vec4(pos, 1.0);
                        const vec4 Color = unpackUnorm4x8(PackedColor);
                        vertexColor = Color * sample_lightmap2(Sampler2, Position.a);
                        texCoord0 = UV0 * UV_INV;
                        fadeFactor = 1.0;
                    }
                    """);
            compileVertex(compiler, options, terrainVertex, "terrain-view-position-vertex-adapter", true);
            String postShadowSource = "uniform sampler2DShadow shadowtex1;\n"
                    + "float compare(sampler2DShadow depthMap){return texture(depthMap,vec3(0.5));}\n"
                    + "layout(location=0) out vec4 outColor;void main(){outColor=vec4(texture(shadowtex1,vec3(0.5))+compare(shadowtex1))+shadow2D(shadowtex1,vec3(0.5));}\n";
            var postShadows = PostShadowSamplers.adapt(postShadowSource, true);
            compile(compiler, options, "#version 450\n" + PostShadowSamplers.bridge(postShadows.inputs()) + postShadows.source(),
                    "post-shadow-comparison-helpers", true);
            if (count == 0) throw new AssertionError("No example shaders found.");
            String inactive = ShaderIncludes.expand("final.fsh", "#if 0\n#include \"missing.glsl\"\n#endif\n", p -> Optional.empty());
            compile(compiler, options, "#version 450\n" + inactive + "layout(location=0) out vec4 color;\nvoid main(){color=vec4(1);}\n", "inactive-missing-include", true);
            String active = ShaderIncludes.expand("final.fsh", "#include \"missing.glsl\"\n", p -> Optional.empty());
            compile(compiler, options, "#version 450\n" + active + "void main(){}\n", "active-missing-include", false);
            String guarded = ShaderIncludes.expand("final.fsh", "#include \"guard.glsl\"\n", p -> Optional.of(
                    "#ifndef GUARD\n#define GUARD\n#include \"guard.glsl\"\n#endif\n"));
            compile(compiler, options, "#version 450\n" + guarded + "layout(location=0) out vec4 color;\nvoid main(){color=vec4(1);}\n", "guarded-recursion", true);
            String recursive = ShaderIncludes.expand("loop.glsl", "#include \"loop.glsl\"\n", p -> Optional.of("#include \"loop.glsl\"\n"));
            compile(compiler, options, "#version 450\n" + recursive + "void main(){}\n", "unguarded-recursion", false);
            compile(compiler, options, TerrainShaderAdapter.adaptFragment("#version 120\n/* DRAWBUFFERS:0 */\n"
                    + "void main(){gl_FragData[1]=vec4(0.5);gl_FragData[0]=gl_FragData[1];}\n"),
                    "unbound-fragment-output-dataflow", true);
            System.out.println("Preprocessed " + count + " example shaders, compiled " + compiled + " directly to Vulkan SPIR-V; 4 conditional-include checks plus entity/item-ID, entity-overlay/lightmap, entity-shadow, cloud-fog, cloud-vertex and terrain-view-position fixtures passed.");
            System.out.println("Unbound fragment-output dataflow compiled to Vulkan SPIR-V.");
        } finally {
            shaderc_compile_options_release(options);
            shaderc_compiler_release(compiler);
        }
    }
    private static void compile(long compiler, long options, String source, String name, boolean expected) {
        long result = shaderc_compile_into_spv(compiler, source, shaderc_fragment_shader, name, "main", options);
        try {
            boolean success = shaderc_result_get_compilation_status(result) == shaderc_compilation_status_success;
            if (success != expected) throw new AssertionError(name + ": " + shaderc_result_get_error_message(result));
            System.out.println("PASS " + name);
        } finally { shaderc_result_release(result); }
    }

    private static void compileVertex(long compiler, long options, String source, String name, boolean expected) {
        long result = shaderc_compile_into_spv(compiler, source, shaderc_vertex_shader, name, "main", options);
        try {
            boolean success = shaderc_result_get_compilation_status(result) == shaderc_compilation_status_success;
            if (success != expected) throw new AssertionError(name + ": " + shaderc_result_get_error_message(result));
            System.out.println("PASS " + name);
        } finally { shaderc_result_release(result); }
    }
}
