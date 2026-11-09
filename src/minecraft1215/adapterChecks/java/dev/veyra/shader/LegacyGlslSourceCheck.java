package dev.veyra.shader;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import java.io.IOException;
import java.util.List;
import net.vulkanmod.vulkan.shader.converter.GlslConverter;

public final class LegacyGlslSourceCheck {
    public static void run() throws Exception {
        checkPostInterfaceMacros();
        String terrain = TerrainShaderAdapter.adaptFragment("#version 120\nvoid main(){gl_FragColor=vec4(1.0);}");
        assert !terrain.contains("TextureSize;") && !terrain.contains("TexelSize;") && !terrain.contains("UseRgss;")
                : "Legacy terrain GLSL must match the CPU block without the modern filtering fields";
        String vertex = """
                #version 450
                // uniform Broken { not a field; } ignored;
                layout(std140) uniform Projection {
                    mat4 ProjMat;
                    vec2 OutSize;
                };
                layout(std140) uniform VeyraParameters {
                    float viewWidth; /* a misleading ; } uniform */
                    vec3 fogColor;
                } veyraParameters;
                #define viewWidth (veyraParameters.viewWidth)
                in vec3 Position;
                out vec3 tint;
                void offset(
                    out vec3 value,
                    in vec3 delta) { value = delta; }
                void main() {
                    vec3 adjusted;
                    offset(adjusted, Position);
                    tint = veyraParameters.fogColor;
                    gl_Position = ProjMat * vec4(adjusted / viewWidth, 1.0);
                }
                """;
        String prepared = LegacyGlslSource.prepare(vertex);
        assert prepared.contains("uniform mat4 ProjMat;") && prepared.contains("uniform vec3 fogColor;");
        assert !prepared.contains("veyraParameters.") && !prepared.contains("uniform Broken");
        assert prepared.contains("#define viewWidth (viewWidth)");
        assert prepared.contains("veyraLegacyParameterout vec3 value");
        assert prepared.contains("veyraLegacyParameterin vec3 delta");
        assert prepared.contains("#define veyraLegacyParameterout out");
        var converter = new GlslConverter();
        converter.process(DefaultVertexFormat.POSITION, prepared, LegacyGlslSource.prepare("""
                #version 450
                in vec3 tint;
                out vec4 fragColor;
                void main() { fragColor = vec4(tint, 1.0); }
                """));
        var ubo = converter.createUBO();
        assert ubo.getBinding() == 0;
        assert ubo.getUniforms().stream().map(value -> value.getName()).toList()
                .equals(List.of("ProjMat", "OutSize", "viewWidth", "fogColor"));
        assert ubo.getSize() == 92;
        assert converter.getSamplerList().isEmpty();
        assert converter.getVshConverted().startsWith("#version 450");
        assert converter.getVshConverted().contains("veyraLegacyParameterout vec3 value");
        assert converter.getFshConverted().contains("layout(location = 0) in vec3 tint;");
        compile(converter.getVshConverted(), org.lwjgl.util.shaderc.Shaderc.shaderc_vertex_shader);
        compile(converter.getFshConverted(), org.lwjgl.util.shaderc.Shaderc.shaderc_fragment_shader);
        String fog = LegacyGlslSource.prepare("""
                layout(std140) uniform Fog {
                    float FogEnvironmentalStart;
                    float FogEnvironmentalEnd;
                    float FogRenderDistanceStart;
                    float FogRenderDistanceEnd;
                };
                """);
        var fogConverter = new GlslConverter();
        var fogFragment = LegacyGlslSource.fragment(fog + """
                out vec4 color;
                void main() { color = vec4(FogEnd - FogStart); }
                """);
        fogConverter.process(DefaultVertexFormat.POSITION, fog + """
                in vec3 Position;
                void main() { gl_Position = vec4(Position, 1.0); }
                """, fogFragment.source());
        assert fogConverter.createUBO().getUniforms().size() == 2 : "Effective fog terms were not merged";
        compile(fogConverter.getVshConverted(), org.lwjgl.util.shaderc.Shaderc.shaderc_vertex_shader);
        compile(fogFragment.restore(fogConverter.getFshConverted()), org.lwjgl.util.shaderc.Shaderc.shaderc_fragment_shader);
        var mrt = LegacyGlslSource.fragment(LegacyGlslSource.prepare("""
                #define VEYRA_MRT layout(location = 2) out vec4 bright;
                VEYRA_MRT
                layout(location = 0) out vec4 base;
                out vec4 spare;
                void main() { base = vec4(1.0); bright = vec4(2.0); spare = vec4(3.0); }
                """));
        assert mrt.source().contains("layout(location = 1) out vec4 spare;");
        var mrtConverter = new GlslConverter();
        mrtConverter.process(DefaultVertexFormat.POSITION,
                "in vec3 Position;\nvoid main() { gl_Position = vec4(Position, 1.0); }", mrt.source());
        compile(mrt.restore(mrtConverter.getFshConverted()), org.lwjgl.util.shaderc.Shaderc.shaderc_fragment_shader);
        try {
            LegacyGlslSource.fragment("layout(location = 0) out vec4 first;\nlayout(location = 0) out vec4 second;");
            throw new AssertionError("Duplicate fragment locations accepted");
        } catch (IOException expected) { }
        assert LegacyGlslSource.fragment("void main() {}").restore("native output").equals("native output");
        assert LegacyGlslSource.prepare("#define DIR(in) (in)\nvoid f(inout vec3 p) {}").contains("#define DIR(in) (in)");
        rejects("layout(std140) uniform Block { float values[4]; };");
        rejects("layout(std140) uniform Block { float repeated; float repeated; };");
        rejects("layout(std140) uniform Block { float field;");
        rejects("layout(std140) uniform Block { float field; } values[2];");
        rejects("void veyraLegacyParameterin() {}");
        System.out.println("Legacy GLSL instance lowering, comments, function directions and native UBO conversion passed");
    }
    private static void checkPostInterfaceMacros() throws Exception {
        var defines = new net.minecraft.client.renderer.ShaderDefines(java.util.Map.of("ALPHA_CUTOUT", "0.25"),
                java.util.Set.of("EMISSIVE"));
        String conditional = LegacyGlslSource.preprocess("conditional.fsh", """
                #version 450
                #ifdef EMISSIVE
                const float cutoff = ALPHA_CUTOUT;
                #else
                in vec4 missingVertexOutput;
                #endif
                """, defines);
        assert conditional.contains("cutoff = 0.25") && !conditional.contains("missingVertexOutput");
        assert conditional.startsWith("#version 450");
        String vertex;
        try (var stream = LegacyGlslSourceCheck.class.getResourceAsStream("/assets/minecraft/shaders/post/screenquad.vsh")) {
            assert stream != null : "Pinned old screenquad source missing";
            vertex = new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        String fragment = LegacyGlslSource.prepare("""
                #version 450
                #define VEYRA_INTERNAL_POST_IN_0 layout(location = 0) in vec2 texCoord
                VEYRA_INTERNAL_POST_IN_0;
                in vec4 unusedVertexValue;
                out vec4 fragColor;
                void main() { fragColor = vec4(texCoord, 0.0, 1.0); }
                """);
        assert fragment.contains("in vec2 texCoord;") && !fragment.contains("VEYRA_INTERNAL_POST_IN_0");
        fragment = LegacyGlslSource.stripUnusedFragmentInputs(fragment);
        assert !fragment.contains("unusedVertexValue");
        assert LegacyGlslSource.stripUnusedFragmentInputs("in vec4 needed;\nvoid main() { fragColor = needed; }")
                .contains("in vec4 needed;");
        var converter = new GlslConverter();
        converter.process(DefaultVertexFormat.POSITION, LegacyGlslSource.prepare(vertex), fragment);
        compile(converter.getVshConverted(), org.lwjgl.util.shaderc.Shaderc.shaderc_vertex_shader);
        compile(converter.getFshConverted(), org.lwjgl.util.shaderc.Shaderc.shaderc_fragment_shader);
        String inPlace = LegacyGlslSource.prepare("""
                #define VEYRA_INTERNAL_POST_LOCATION(n) layout(location = n)
                #define VEYRA_INTERNAL_POST_OUT_3 VEYRA_INTERNAL_POST_LOCATION(3) flat out ivec2 material
                VEYRA_INTERNAL_POST_OUT_3;
                #define USER_MACRO layout(location = 1) in vec2 untouched
                """);
        assert inPlace.contains("flat out ivec2 material;");
        assert inPlace.contains("#define USER_MACRO layout(location = 1) in vec2 untouched");
    }

    private static void rejects(String source) throws Exception {
        try { LegacyGlslSource.prepare(source); throw new AssertionError("Unsupported GLSL declaration accepted"); }
        catch (IOException expected) { }
    }
    private static void compile(String source, int kind) {
        long compiler = org.lwjgl.util.shaderc.Shaderc.shaderc_compiler_initialize();
        if (compiler == 0) throw new AssertionError("Could not initialize shaderc");
        try {
            long result = org.lwjgl.util.shaderc.Shaderc.shaderc_compile_into_spv(compiler, source, kind,
                    "legacy-converter-check", "main", 0);
            if (result == 0) throw new AssertionError("shaderc returned no result");
            try {
                assert org.lwjgl.util.shaderc.Shaderc.shaderc_result_get_compilation_status(result)
                        == org.lwjgl.util.shaderc.Shaderc.shaderc_compilation_status_success
                        : org.lwjgl.util.shaderc.Shaderc.shaderc_result_get_error_message(result);
            } finally { org.lwjgl.util.shaderc.Shaderc.shaderc_result_release(result); }
        } finally { org.lwjgl.util.shaderc.Shaderc.shaderc_compiler_release(compiler); }
    }
}
