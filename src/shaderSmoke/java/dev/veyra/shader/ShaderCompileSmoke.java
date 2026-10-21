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
                        "gbuffers_(terrain(?:_solid|_cutout)?|water|entities(?:_translucent)?|block(?:_translucent)?|"
                                + "hand(?:_water)?|spidereyes|clouds|damagedblock)\\.fsh")).sorted().toList()) {
                    String name = path.getFileName().toString();
                    String source = ShaderIncludes.expand(name, Files.readString(path), included -> {
                        Path file = path.getParent().resolve(included);
                        return Files.exists(file) ? Optional.of(Files.readString(file)) : Optional.empty();
                    });
                    source = GlslPreprocessor.process(name, source);
                    String programName = name.substring(0, name.length() - 4);
                    Path vertexPath = path.getParent().resolve(programName + ".vsh");
                    Path properties = path.getParent().resolve("shaders.properties");
                    CustomUniforms.activate(Files.exists(properties)
                            ? CustomUniforms.parse(Files.readString(properties)) : CustomUniforms.EMPTY);
                    Map<String, TerrainVertexShaderAdapter.PackVarying> packVaryings = Map.of();
                    if (Files.exists(vertexPath)) {
                        String packVertex = ShaderIncludes.expand(programName + ".vsh", Files.readString(vertexPath), included -> {
                            Path file = vertexPath.getParent().resolve(included);
                            return Files.exists(file) ? Optional.of(Files.readString(file)) : Optional.empty();
                        });
                        if (EntityVertexShaderAdapter.supportsProgram(programName)) {
                            boolean hand = programName.startsWith("gbuffers_hand");
                            String nativeInput = programName.equals("gbuffers_damagedblock")
                                    ? EntityShaderAdapter.adaptBlockVertexFormat(damagedBlockNativeVertexSource(), programName)
                                    : entityNativeVertexSource();
                            String nativeVertex = EntityShaderAdapter.adaptVertexShader(nativeInput,
                                    false, programName,
                                    EntityShaderAdapter.requiresNormalInput(source),
                                    EntityShaderAdapter.requiresLightmapInput(source),
                                    EntityShaderAdapter.requiresViewPositionInput(source),
                                    EntityShaderAdapter.requiresViewNormalInput(source), false);
                            EntityVertexShaderAdapter.AdaptedStage adaptedVertex = EntityVertexShaderAdapter.adaptStage(
                                    nativeVertex, packVertex, programName, hand);
                            compileVertex(compiler, options, adaptedVertex.source(),
                                    path.getParent().getFileName() + "/" + programName + ".vsh", true);
                            packVaryings = adaptedVertex.varyings();
                        } else {
                            packVaryings = TerrainVertexShaderAdapter.validateAndGetVaryings(programName, packVertex);
                        }
                    }
                    String adapted = name.equals("gbuffers_clouds.fsh") ? CloudShaderAdapter.adaptFragment(source)
                            : programName.equals("gbuffers_damagedblock")
                            ? Files.exists(vertexPath)
                                    ? DamagedBlockShaderAdapter.adaptFragment(source, programName, packVaryings,
                                            false, false)
                                    : DamagedBlockShaderAdapter.adaptFragment(source, programName)
                            : EntityVertexShaderAdapter.supportsProgram(programName)
                            ? EntityShaderAdapter.adaptGeometryFragment(source, name.substring(0, name.length()-4),
                            programName.startsWith("gbuffers_hand"), Files.exists(path.getParent().resolve("shadow.fsh")),
                            -1, -1, ItemIdMap.UNMAPPED, ShaderPackUniformAdapter.supportsEntityColorProgram(
                                    name.substring(0, name.length()-4)), false, packVaryings)
                            : TerrainShaderAdapter.adaptFragment(source, true, name.equals("gbuffers_water.fsh"),
                                    packVaryings);
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
            Path glintFixture = Path.of("examples/glint-vertex-execution/shaders");
            String glintPackVertex = GlslPreprocessor.process("gbuffers_armor_glint.vsh",
                    Files.readString(glintFixture.resolve("gbuffers_armor_glint.vsh")));
            String glintNativeVertex = """
                    #version 460
                    layout(location = 0) in vec3 Position;
                    layout(location = 1) in vec2 UV0;
                    layout(location = 1) out vec2 texCoord0;
                    layout(location = 2) out float sphericalVertexDistance;
                    layout(location = 3) out float cylindricalVertexDistance;
                    layout(set = 0, binding = 0, std140) uniform DynamicTransforms {
                        mat4 ProjMat;
                        mat4 ModelViewMat;
                        mat4 TextureMat;
                    };
                    void main() {
                        gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
                        texCoord0 = (TextureMat * vec4(UV0, 0.0, 1.0)).xy;
                        sphericalVertexDistance = 0.0;
                        cylindricalVertexDistance = 0.0;
                    }
                    """;
            CustomUniforms.activate(CustomUniforms.EMPTY);
            GlintVertexShaderAdapter.AdaptedStage glintVertex = GlintVertexShaderAdapter.adaptStage(
                    glintNativeVertex, glintPackVertex, "gbuffers_armor_glint");
            String glintPackFragment = GlslPreprocessor.process("gbuffers_armor_glint.fsh",
                    Files.readString(glintFixture.resolve("gbuffers_armor_glint.fsh")));
            String glintFragment = TextureColorDistanceShaderAdapter.adaptFragment(glintPackFragment,
                    "gbuffers_armor_glint", glintVertex.outputsColor(), true,
                    glintVertex.colorInterpolation() == null ? "smooth" : glintVertex.colorInterpolation(),
                    glintVertex.texCoordInterpolation(), true);
            compileVertex(compiler, options, glintVertex.source(), "armor-glint-vertex-stage", true);
            compile(compiler, options, glintFragment, "armor-glint-fragment-stage", true);
            String glintTexAliasFragment = TextureColorDistanceShaderAdapter.adaptFragment("""
                    #version 120
                    uniform sampler2D tex;
                    varying vec2 texcoord;
                    void main() { gl_FragColor = texture2D(tex, texcoord); }
                    """, "gbuffers_armor_glint", false);
            compile(compiler, options, glintTexAliasFragment, "armor-glint-iris-tex-sampler-alias", true);
            String glintMrtFragment = TextureColorDistanceShaderAdapter.adaptFragment("""
                    #version 120
                    /* RENDERTARGETS: 0,4 */
                    varying vec2 texcoord;
                    void main() {
                        gl_FragData[0] = vec4(texcoord, 0.0, 1.0);
                        gl_FragData[1] = vec4(0.0, 1.0, 0.0, 1.0);
                    }
                    """, "gbuffers_armor_glint", false);
            compile(compiler, options, glintMrtFragment, "armor-glint-mrt-outputs", true);
            GlintVertexShaderAdapter.AdaptedStage glintSharedLibraryVertex = GlintVertexShaderAdapter.adaptStage(
                    glintNativeVertex, """
                            #version 130
                            uniform sampler2D noisetex;
                            out vec2 texCoord;
                            flat out vec4 glColor;
                            vec2 GetLightMapCoordinates() {
                                return (gl_TextureMatrix[1] * gl_MultiTexCoord1).xy;
                            }
                            float ReadNoise() { return texture2D(noisetex, vec2(0.5)).r; }
                            void main() {
                                gl_Position = ftransform();
                                texCoord = gl_MultiTexCoord0.xy;
                                glColor = gl_Color;
                                  float noise = ReadNoise();
                            }
                            """, "gbuffers_armor_glint");
            if (!glintSharedLibraryVertex.usesNoiseSampler()) {
                throw new AssertionError("The armor-glint shared-library noisetex was not bridged.");
            }
            compileVertex(compiler, options, glintSharedLibraryVertex.source(),
                    "armor-glint-shared-noise-lightmap-helpers", true);
            String nativeLeashVertex = """
                    #version 460
                    layout(location = 0) in vec3 Position;
                    layout(location = 1) in vec4 Color;
                    layout(location = 2) in ivec2 UV2;
                    layout(location = 0) out vec4 vertexColor;
                    layout(location = 1) out float sphericalVertexDistance;
                    layout(location = 2) out float cylindricalVertexDistance;
                    layout(set = 0, binding = 0, std140) uniform NativeTransforms {
                        mat4 ModelViewMat;
                        mat4 ProjMat;
                        mat4 TextureMat;
                        vec4 ColorModulator;
                    };
                    void main() {
                        gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
                        vertexColor = Color * ColorModulator;
                        sphericalVertexDistance = 0.0;
                        cylindricalVertexDistance = 0.0;
                    }
                    """;
            var basicLeashVertex = BasicVertexShaderAdapter.adaptStage(nativeLeashVertex, """
                    #version 130
                    varying vec2 lmCoord;
                    flat varying vec4 color;
                    void main() {
                        gl_Position = ftransform();
                        lmCoord = (gl_TextureMatrix[1] * gl_MultiTexCoord1).xy;
                        color = gl_Color;
                    }
                    """, "gbuffers_basic", BasicVertexShaderAdapter.Format.LEASH);
            compileVertex(compiler, options, basicLeashVertex.source(), "basic-leash-vertex-adapter", true);
            String nativeLineVertex = """
                    #version 460
                    layout(location = 0) in vec3 Position;
                    layout(location = 1) in vec4 Color;
                    layout(location = 2) in vec3 Normal;
                    layout(location = 3) in float LineWidth;
                    layout(location = 0) out vec4 vertexColor;
                    layout(location = 1) out float sphericalVertexDistance;
                    layout(location = 2) out float cylindricalVertexDistance;
                    layout(set = 0, binding = 0, std140) uniform NativeTransforms {
                        mat4 ModelViewMat;
                        mat4 ProjMat;
                        mat4 TextureMat;
                        vec4 ColorModulator;
                        vec2 ScreenSize;
                    };
                    void main() {
                        gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
                        vertexColor = Color * ColorModulator;
                        sphericalVertexDistance = 0.0;
                        cylindricalVertexDistance = 0.0;
                    }
                    """;
            var basicLineVertex = BasicVertexShaderAdapter.adaptStage(nativeLineVertex, """
                    #version 130
                    flat out vec4 color;
                    out vec3 normal;
                    void main() {
                        gl_Position = ftransform();
                        color = gl_Color;
                        normal = gl_Normal;
                    }
                    """, "gbuffers_line", BasicVertexShaderAdapter.Format.LINE);
            compileVertex(compiler, options, basicLineVertex.source(), "basic-line-vertex-adapter", true);
            Path basicFixture = Path.of("examples/basic-vertex-execution/shaders");
            String basicPackVertexSource = GlslPreprocessor.process("gbuffers_basic.vsh",
                    Files.readString(basicFixture.resolve("gbuffers_basic.vsh")));
            var basicPackVertex = BasicVertexShaderAdapter.adaptStage(nativeLeashVertex,
                    basicPackVertexSource, "gbuffers_basic", BasicVertexShaderAdapter.Format.LEASH);
            compileVertex(compiler, options, basicPackVertex.source(), "basic-pack-leash-vertex", true);
            String basicPackFragment = TextureColorDistanceShaderAdapter.adaptFragment(
                    Files.readString(basicFixture.resolve("gbuffers_basic.fsh")), "gbuffers_basic",
                    basicPackVertex.varyings(), false, true);
            compile(compiler, options, basicPackFragment, "basic-pack-leash-fragment", true);
            Path skyFixture = Path.of("examples/gbuffers-skybasic-vertex-execution/shaders");
            String skyPackVertexSource = GlslPreprocessor.process("gbuffers_skybasic.vsh",
                    Files.readString(skyFixture.resolve("gbuffers_skybasic.vsh")));
            String skyPackFragmentSource = Files.readString(skyFixture.resolve("gbuffers_skybasic.fsh"));
            Map<BasicVertexShaderAdapter.Format, String> nativeSkyVertices = new LinkedHashMap<>();
            nativeSkyVertices.put(BasicVertexShaderAdapter.Format.SKY, """
                    #version 460
                    layout(location = 0) in vec3 Position;
                    out float sphericalVertexDistance;
                    out float cylindricalVertexDistance;
                    layout(set = 0, binding = 0, std140) uniform NativeTransforms {
                        mat4 ModelViewMat;
                        mat4 ProjMat;
                        mat4 TextureMat;
                        vec4 ColorModulator;
                        vec2 ScreenSize;
                    };
                    void main() {
                        gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
                        sphericalVertexDistance = 1.0;
                        cylindricalVertexDistance = 1.0;
                    }
                    """);
            nativeSkyVertices.put(BasicVertexShaderAdapter.Format.STARS, """
                    #version 460
                    layout(location = 0) in vec3 Position;
                    layout(set = 0, binding = 0, std140) uniform NativeTransforms {
                        mat4 ModelViewMat;
                        mat4 ProjMat;
                        mat4 TextureMat;
                        vec4 ColorModulator;
                        vec2 ScreenSize;
                    };
                    void main() { gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0); }
                    """);
            nativeSkyVertices.put(BasicVertexShaderAdapter.Format.SUNRISE_SUNSET, """
                    #version 460
                    layout(location = 0) in vec3 Position;
                    layout(location = 1) in vec4 Color;
                    out vec4 vertexColor;
                    layout(set = 0, binding = 0, std140) uniform NativeTransforms {
                        mat4 ModelViewMat;
                        mat4 ProjMat;
                        mat4 TextureMat;
                        vec4 ColorModulator;
                        vec2 ScreenSize;
                    };
                    void main() {
                        gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
                        vertexColor = Color;
                    }
                    """);
            for (var entry : nativeSkyVertices.entrySet()) {
                var skyVertex = BasicVertexShaderAdapter.adaptStage(entry.getValue(), skyPackVertexSource,
                        "gbuffers_skybasic", entry.getKey());
                compileVertex(compiler, options, skyVertex.source(),
                        "skybasic-" + entry.getKey().name().toLowerCase() + "-vertex", true);
                String skyFragment = TextureColorDistanceShaderAdapter.adaptFragment(skyPackFragmentSource,
                        "gbuffers_skybasic", skyVertex.varyings(), false, false);
                compile(compiler, options, skyFragment,
                        "skybasic-" + entry.getKey().name().toLowerCase() + "-fragment", true);
            }
            count++;
            compiled += nativeSkyVertices.size() * 2;
            String lightningPackVertexSource = """
                    #version 130
                    varying vec4 color;
                    void main() {
                        gl_Position = ftransform();
                        color = gl_Color;
                    }
                    """;
            BasicVertexShaderAdapter.AdaptedStage lightningVertex = BasicVertexShaderAdapter.validate(
                    "gbuffers_lightning", lightningPackVertexSource, BasicVertexShaderAdapter.Format.LIGHTNING);
            String lightningFragment = TextureColorDistanceShaderAdapter.adaptFragment("""
                    #version 130
                    varying vec4 color;
                    varying float sphericalVertexDistance;
                    varying float cylindricalVertexDistance;
                    void main() {
                        gl_FragColor = color + vec4(sphericalVertexDistance + cylindricalVertexDistance);
                    }
                    """, "gbuffers_lightning", lightningVertex.varyings(), false, false);
            compileVertex(compiler, options, lightningVertex.source(), "lightning-pack-vertex", true);
            compile(compiler, options, lightningFragment, "lightning-pack-fragment", true);
            count++;
            compiled += 2;

            Path weatherFixture = Path.of("examples/weather-vertex-execution/shaders");
            String weatherPackVertex = GlslPreprocessor.process("gbuffers_weather.vsh",
                    Files.readString(weatherFixture.resolve("gbuffers_weather.vsh")));
            String weatherPackFragment = GlslPreprocessor.process("gbuffers_weather.fsh",
                    Files.readString(weatherFixture.resolve("gbuffers_weather.fsh")));
            BasicVertexShaderAdapter.AdaptedStage weatherVertex = BasicVertexShaderAdapter.adaptStage(
                    weatherNativeVertexSource(), weatherPackVertex, "gbuffers_weather",
                    BasicVertexShaderAdapter.Format.WEATHER);
            String weatherFragment = TextureColorDistanceShaderAdapter.adaptFragment(weatherPackFragment,
                    "gbuffers_weather", weatherVertex.varyings(), false, true);
            compileVertex(compiler, options, weatherVertex.source(), "weather-pack-vertex", true);
            compile(compiler, options, weatherFragment, "weather-pack-fragment", true);
            count++;
            compiled += 2;

            String skyTexturedPackVertexSource = """
                    #version 130
                    varying vec2 texCoord;
                    varying vec4 color;
                    void main() {
                        gl_Position = ftransform();
                        texCoord = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
                        color = gl_Color;
                    }
                    """;
            String skyTexturedPackFragmentSource = """
                    #version 130
                    uniform int renderStage;
                    varying vec2 texCoord;
                    varying vec4 color;
                    void main() {
                        gl_FragColor = vec4(texCoord, float(renderStage) / 23.0, 1.0) * color;
                    }
                    """;
            for (BasicVertexShaderAdapter.Format format : List.of(
                    BasicVertexShaderAdapter.Format.SKY_TEXTURED,
                    BasicVertexShaderAdapter.Format.SKY_TEXTURED_COLOR)) {
                BasicVertexShaderAdapter.AdaptedStage skyTexturedVertex = BasicVertexShaderAdapter.validate(
                        "gbuffers_skytextured", skyTexturedPackVertexSource, format);
                compileVertex(compiler, options, skyTexturedVertex.source(),
                        "skytextured-" + format.name().toLowerCase() + "-vertex", true);
                for (int renderStage : IrisRenderStages.skyTexturedStages("pipeline/celestial")) {
                    String skyTexturedFragment = TextureColorDistanceShaderAdapter.adaptFragment(
                            skyTexturedPackFragmentSource, "gbuffers_skytextured",
                            skyTexturedVertex.varyings(), false, false, renderStage);
                    compile(compiler, options, skyTexturedFragment,
                            "skytextured-" + format.name().toLowerCase() + "-stage-" + renderStage, true);
                }
            }
            count++;
            compiled += 8;

            String texturedPackVertexSource = """
                    #version 130
                    varying vec2 texcoord;
                    void main() {
                        gl_Position = ftransform();
                        texcoord = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
                    }
                    """;
            BasicVertexShaderAdapter.AdaptedStage texturedVertex = BasicVertexShaderAdapter.validate(
                    "gbuffers_textured", texturedPackVertexSource, BasicVertexShaderAdapter.Format.TEXTURED);
            String texturedFragment = TextureColorDistanceShaderAdapter.adaptFragment("""
                    #version 130
                    varying vec2 texcoord;
                    uniform sampler2D tex;
                    void main() {
                        vec2 texel = 1.0 / vec2(textureSize(tex, 0));
                        gl_FragColor = texture2D(tex, texcoord + texel);
                    }
                    """, "gbuffers_textured", texturedVertex.varyings(), false, false);
            compileVertex(compiler, options, texturedVertex.source(), "textured-pack-vertex", true);
            compile(compiler, options, texturedFragment, "textured-pack-fragment", true);
            count++;
            compiled += 2;
            Path beaconFixture = Path.of("examples/beaconbeam-vertex-execution/shaders");
            String beaconPackVertexSource = GlslPreprocessor.process("gbuffers_beaconbeam.vsh",
                    Files.readString(beaconFixture.resolve("gbuffers_beaconbeam.vsh")));
            String nativeBeaconVertex = """
                    #version 460
                    layout(location = 0) in vec3 Position;
                    layout(location = 1) in vec4 Color;
                    layout(location = 2) in vec2 UV0;
                    layout(location = 0) out vec4 vertexColor;
                    layout(location = 1) out vec2 texCoord0;
                    layout(set = 0, binding = 0, std140) uniform NativeTransforms {
                        mat4 ProjMat;
                        mat4 ModelViewMat;
                        mat4 TextureMat;
                    };
                    void main() {
                        gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
                        vertexColor = Color;
                        texCoord0 = (TextureMat * vec4(UV0, 0.0, 1.0)).xy;
                    }
                    """;
            BeaconBeamVertexShaderAdapter.AdaptedStage beaconVertex = BeaconBeamVertexShaderAdapter.adaptStage(
                    nativeBeaconVertex, beaconPackVertexSource, "gbuffers_beaconbeam");
            String beaconPackFragment = TextureColorDistanceShaderAdapter.adaptFragment(
                    Files.readString(beaconFixture.resolve("gbuffers_beaconbeam.fsh")), "gbuffers_beaconbeam",
                    beaconVertex.varyings(), false, true);
            // Direct shaderc compilation does not run VulkanMod's named-uniform binding path.
            String beaconVertexForShaderc = beaconVertex.source().replace("uniform mat4 gbufferModelView;",
                    "layout(set = 0, binding = 3, std140) uniform BeaconPackUniforms { mat4 gbufferModelView; };");
            compileVertex(compiler, options, beaconVertexForShaderc, "beacon-beam-pack-vertex", true);
            compile(compiler, options, beaconPackFragment, "beacon-beam-flat-varying-mrt-fragment", true);
            count++;
            compiled += 2;
            var basicFallbackLineVertex = BasicVertexShaderAdapter.adaptStage(nativeLineVertex, """
                    #version 130
                    void main() {
                        gl_Position = ProjMat * ModelViewMat * vec4(vaPosition + vaNormal, 1.0);
                    }
                    """, "gbuffers_line", BasicVertexShaderAdapter.Format.LINE, true);
            compileVertex(compiler, options, basicFallbackLineVertex.source(),
                    "basic-fallback-line-vertex-widening", true);
            String linePackVertexSource = GlslPreprocessor.process("gbuffers_line.vsh",
                    Files.readString(basicFixture.resolve("gbuffers_line.vsh")));
            var linePackVertex = BasicVertexShaderAdapter.adaptStage(nativeLineVertex,
                    linePackVertexSource, "gbuffers_line", BasicVertexShaderAdapter.Format.LINE);
            compileVertex(compiler, options, linePackVertex.source(), "basic-pack-line-vertex", true);
            String linePackFragment = TextureColorDistanceShaderAdapter.adaptFragment(
                    Files.readString(basicFixture.resolve("gbuffers_line.fsh")), "gbuffers_line",
                    linePackVertex.varyings(), false, false);
            compile(compiler, options, linePackFragment, "basic-pack-line-fragment", true);
            String basicLineFragment = TextureColorDistanceShaderAdapter.adaptFragment("""
                    #version 130
                    /* DRAWBUFFERS:06 */
                    flat in vec2 lmCoord;
                    in vec3 normal;
                    flat in vec3 upVec, sunVec;
                    flat in vec4 glColor;
                    void main() {
                        gl_FragData[0] = glColor * vec4(normal + upVec + sunVec, 1.0);
                        gl_FragData[1] = vec4(lmCoord, 0.0, 1.0);
                    }
                    """, "gbuffers_line", Map.of(
                    "glColor", new TerrainVertexShaderAdapter.PackVarying("vec4", "flat", 0, "vertexColor", true),
                    "lmCoord", new TerrainVertexShaderAdapter.PackVarying("vec2", "flat", 3,
                            "veyraBasicLightmapCoord", true),
                    "normal", new TerrainVertexShaderAdapter.PackVarying("vec3", "smooth", 4,
                            "veyraBasicNormal", true),
                    "upVec", new TerrainVertexShaderAdapter.PackVarying("vec3", "flat", 6,
                            "veyraPackVarying_upVec", false),
                    "sunVec", new TerrainVertexShaderAdapter.PackVarying("vec3", "flat", 7,
                            "veyraPackVarying_sunVec", false)));
            compile(compiler, options, basicLineFragment, "basic-line-fragment-mrt-adapter", true);
            count += 8;
            compiled += 9;
            String basicSamplerFragment = TextureColorDistanceShaderAdapter.adaptFragment("""
                    #version 130
                    /* DRAWBUFFERS:06 */
                    uniform sampler2D texture;
                    uniform sampler2D lightmap;
                    uniform sampler2D noisetex;
                    uniform sampler2D specular;
                    uniform sampler2DShadow shadowtex0;
                    uniform sampler2D shadowtex1;
                    uniform sampler2D shadowcolor0;
                    uniform sampler2D gdepth;
                    uniform sampler2D colortex6;
                    flat in vec2 lmCoord;
                    void main() {
                        float shadow = texture2DShadow(shadowtex0, vec3(lmCoord, 0.5));
                        gl_FragData[0] = texture2D(texture, lmCoord) + texture2D(lightmap, lmCoord)
                                + texture2D(noisetex, lmCoord) + texture2D(specular, lmCoord)
                                + texture2D(shadowtex1, lmCoord) + texture2D(shadowcolor0, lmCoord)
                                + texture2D(gdepth, lmCoord) + texture2D(colortex6, lmCoord) + vec4(shadow);
                        gl_FragData[1] = vec4(lmCoord, 0.0, 1.0);
                    }
                    """, "gbuffers_basic", Map.of(
                    "lmCoord", new TerrainVertexShaderAdapter.PackVarying(
                            "vec2", "flat", 3, "veyraBasicLightmapCoord", true)), true, true);
            compile(compiler, options, basicSamplerFragment, "basic-line-fragment-sampler-bridge", true);
            count++;
            compiled++;
            count += 4;
            compiled += 4;
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
            String entityMrt = EntityShaderAdapter.adaptFragment("""
                    #version 120
                    /* RENDERTARGETS:0,12 */
                    layout(location = 0) out vec4 sceneColor;
                    layout(location = 1) out vec4 materialColor;
                    void main() {
                        sceneColor = vec4(0.9, 0.0, 0.9, 1.0);
                        materialColor = vec4(1.0, 0.0, 1.0, 1.0);
                    }
                    """, "gbuffers_entities");
            compile(compiler, options, entityMrt, "entity-multiple-render-targets", true);
            String entityLegacyMrt = EntityShaderAdapter.adaptFragment("""
                    #version 120
                    /* DRAWBUFFERS:04 */
                    void main() {
                        gl_FragData[0] = vec4(0.9, 0.0, 0.9, 1.0);
                        gl_FragData[1] = vec4(1.0, 0.0, 1.0, 1.0);
                    }
                    """, "gbuffers_entities");
            compile(compiler, options, entityLegacyMrt, "entity-legacy-multiple-render-targets", true);
            String entityColorFeedback = EntityShaderAdapter.adaptGeometryFragment("""
                    #version 120
                    /* RENDERTARGETS:0,4 */
                    uniform sampler2D colortex4;
                    void main() {
                        vec4 previous = texture2D(colortex4, vec2(0.5));
                        gl_FragData[0] = previous;
                        gl_FragData[1] = previous;
                    }
                    """, "gbuffers_entities", false, false, -1, -1,
                    ItemIdMap.UNMAPPED, true, false);
            compile(compiler, options, entityColorFeedback, "entity-colortex-feedback-snapshot", true);
            EntityVertexShaderAdapter.AdaptedStage entityNoiseVertex = EntityVertexShaderAdapter.adaptStage(
                    entityNativeVertexSource(), """
                            #version 120
                            uniform sampler2D noisetex;
                            varying vec3 LightC;
                            void main() {
                                gl_Position = ftransform() + texture2D(noisetex, vec2(0.5));
                                LightC = vec3(1.0, 0.0, 1.0);
                            }
                            """, "gbuffers_entities", false);
            compileVertex(compiler, options, entityNoiseVertex.source(), "entity-noisetex-vertex", true);
            String blockLightmapNative = EntityShaderAdapter.adaptBlockVertexFormat(
                    damagedBlockNativeVertexSource(), "gbuffers_damagedblock");
            EntityVertexShaderAdapter.AdaptedStage blockLightmapVertex = EntityVertexShaderAdapter.adaptStage(
                    blockLightmapNative, """
                            #version 120
                            varying vec2 lmCoord;
                            void main() {
                                gl_Position = ftransform();
                                lmCoord = (gl_TextureMatrix[1] * gl_MultiTexCoord1).xy;
                            }
                            """, "gbuffers_damagedblock", false);
            compileVertex(compiler, options, blockLightmapVertex.source(),
                    "damaged-block-block-format-lightmap-alias", true);
            Path mainHandFixture = Path.of("examples/current-rendered-item-id/shaders/gbuffers_hand.fsh");
            String mainHandPackSource = GlslPreprocessor.process(
                    mainHandFixture.getFileName().toString(), Files.readString(mainHandFixture));
            String mainHandShader = EntityShaderAdapter.adaptFragment(mainHandPackSource,
                    "gbuffers_hand", true, false, -1, -1, ItemIdMap.UNMAPPED, true, true);
            compile(compiler, options, mainHandShader, "hand-main-hand-specialization", true);
            String offHandShader = EntityShaderAdapter.adaptFragment(mainHandPackSource,
                    "gbuffers_hand", true, false, -1, -1, ItemIdMap.UNMAPPED, true, false);
            compile(compiler, options, offHandShader, "hand-off-hand-specialization", true);
            String handDirections = EntityShaderAdapter.adaptFragment("""
                    #version 120
                    uniform mat4 gbufferModelView;
                    varying vec2 texCoord, lmCoord;
                    varying vec3 sunVec, upVec, eastVec;
                    void main() {
                        gl_FragColor = vec4(normalize(sunVec) + normalize(upVec) + normalize(eastVec), 1.0);
                    }
                    """, "gbuffers_hand", true, false);
            String handDirectionsWithUniformBlock = handDirections
                    .replace("uniform vec3 sunPosition;\n", "")
                    .replace("uniform mat4 gbufferModelView;\n", "")
                    .replaceFirst("#version 460\\n", "#version 460\n"
                            + "layout(set = 0, binding = 1, std140) uniform DirectionInputs {\n"
                            + "    vec3 sunPosition;\n"
                            + "    mat4 gbufferModelView;\n"
                            + "};\n");
            compile(compiler, options, handDirectionsWithUniformBlock, "hand-directional-inputs", true);
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
            CloudShaderAdapter.AdaptedVertex modelViewCloudVertex = CloudShaderAdapter.adaptVertexWithMetadata("""
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
                    """, """
                    varying vec2 texcoord;
                    varying vec4 color;
                    varying float dist;
                    void main() {
                        gl_Position = ftransform();
                        dist = length(gl_ModelViewMatrix * gl_Vertex);
                        texcoord = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
                        color = gl_Color;
                    }
                    """);
            String modelViewCloudVertexSource = appendCloudUniformFields(
                    modelViewCloudVertex.source(), modelViewCloudVertex.uniformFields());
            String modelViewCloudFragmentSource = CloudShaderAdapter.adapt("""
                    uniform sampler2D texture;
                    varying vec2 texcoord;
                    varying vec4 color;
                    varying float dist;
                    void main() { gl_FragColor = texture2D(texture, texcoord) * color * clamp(dist, 0.0, 1.0); }
                    """, modelViewCloudVertex.uniformFields()).source();
            compileVertex(compiler, options, modelViewCloudVertexSource, "cloud-model-view-uniform", true);
            compile(compiler, options, modelViewCloudFragmentSource, "cloud-model-view-varying-link", true);
            CloudShaderAdapter.AdaptedVertex bslCloudVertex = CloudShaderAdapter.adaptVertexWithMetadata("""
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
                    """, """
                    varying vec2 texCoord;
                    varying vec3 normal;
                    varying vec3 sunVec, upVec, eastVec;
                    varying vec4 color;
                    uniform float timeAngle;
                    uniform mat4 gbufferModelView;
                    void main() {
                        texCoord = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
                        color = gl_Color;
                        normal = normalize(gl_NormalMatrix * gl_Normal);
                        sunVec = normalize((gbufferModelView * vec4(vec3(timeAngle), 1.0)).xyz);
                        upVec = normalize(gbufferModelView[1].xyz);
                        eastVec = normalize(gbufferModelView[0].xyz);
                        gl_Position = ftransform();
                    }
                    """);
            String bslCloudVertexSource = appendCloudUniformFields(
                    bslCloudVertex.source(), bslCloudVertex.uniformFields());
            String bslCloudFragmentSource = CloudShaderAdapter.adapt("""
                    uniform sampler2D texture;
                    varying vec2 texCoord;
                    varying vec3 normal;
                    varying vec3 sunVec, upVec, eastVec;
                    varying vec4 color;
                    void main() {
                        vec4 albedo = texture2D(texture, texCoord);
                        albedo.rgb *= dot(normal, upVec) + dot(sunVec, eastVec);
                        gl_FragColor = albedo * color;
                    }
                    """, bslCloudVertex.uniformFields()).source();
            compileVertex(compiler, options, bslCloudVertexSource, "bsl-cloud-vertex-inputs", true);
            compile(compiler, options, bslCloudFragmentSource, "bsl-cloud-fragment-inputs", true);
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
            String terrainCustomVertex = TerrainVertexShaderAdapter.adapt(terrainVertex, """
                    #version 120
                    attribute vec4 mc_Entity;
                    attribute vec4 mc_midTexCoord;
                    attribute vec4 at_tangent;
                    void main() {
                        vec4 position = gl_Vertex;
                        position.xyz += gl_Normal + at_tangent.xyz + mc_Entity.xyz * 0.0 + mc_midTexCoord.xyz * 0.0;
                        position.xy += (gl_TextureMatrix[1] * gl_MultiTexCoord1).xy * 0.0;
                        position.xy += (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy * 0.0;
                        position += gl_Color * 0.0;
                        gl_Position = gl_ModelViewProjectionMatrix * position;
                    }
                    """, "gbuffers_terrain");
            compileVertex(compiler, options, terrainCustomVertex, "terrain-custom-vertex-main", true);
            TerrainVertexShaderAdapter.AdaptedStage terrainVaryings = TerrainVertexShaderAdapter.adaptStage(
                    terrainVertex, """
                            #version 130
                            out vec2 texCoord;
                            out vec3 normal;
                            flat out int mat;
                            flat out float chunkFade;
                            void main() {
                                texCoord = gl_MultiTexCoord0.xy;
                                normal = gl_Normal;
                                mat = 27101;
                                chunkFade = mc_chunkFade;
                            }
                            """, "gbuffers_terrain");
            compileVertex(compiler, options, terrainVaryings.source(),
                    "terrain-custom-vertex-varyings", true);
            String terrainVaryingFragment = TerrainShaderAdapter.adaptFragment("""
                    #version 130
                    in vec2 texCoord;
                    in vec3 normal;
                    flat in int mat;
                    flat in float chunkFade;
                    void main() {
                        gl_FragColor = vec4(texCoord, normal.x * 0.0 + float(mat) * 0.0 + chunkFade * 0.0, 1.0);
                    }
                    """, false, false, terrainVaryings.varyings());
            compile(compiler, options, terrainVaryingFragment,
                    "terrain-custom-fragment-varyings", true);
            TerrainVertexShaderAdapter.AdaptedStage terrainNoiseMatrixVertex =
                    TerrainVertexShaderAdapter.adaptStage(terrainVertex, """
                            #version 130
                            uniform sampler2D noisetex;
                            out mat3 tbnMatrix;
                            out vec4 matrixFollowing;
                            vec3 noiseOffset(vec2 uv) { return texture2D(noisetex, uv).rgb; }
                            void main() {
                                float noise = noiseOffset(vec2(0.5)).x;
                                tbnMatrix = mat3(noise + 1.0);
                                matrixFollowing = vec4(noise);
                            }
                            """, "gbuffers_water", 5);
            compileVertex(compiler, options, terrainNoiseMatrixVertex.source(),
                    "terrain-noisetex-matrix-vertex-varyings", true);
            String terrainNoiseMatrixFragment = TerrainShaderAdapter.adaptFragment("""
                    #version 130
                    in mat3 tbnMatrix;
                    in vec4 matrixFollowing;
                    void main() {
                        gl_FragColor = vec4(tbnMatrix[0] + matrixFollowing.rgb, 1.0);
                    }
                    """, false, false, terrainNoiseMatrixVertex.varyings());
            compile(compiler, options, terrainNoiseMatrixFragment,
                    "terrain-noisetex-matrix-fragment-varyings", true);
            String terrainMidCoord = TerrainShaderAdapter.adaptFragment("""
                    #version 120
                    varying vec2 absMidCoordPos;
                    void main() { gl_FragColor = vec4(absMidCoordPos, 0.0, 1.0); }
                    """);
            compile(compiler, options, terrainMidCoord, "terrain-abs-midcoordpos-fragment-adapter", true);
            String terrainSpriteBounds = TerrainShaderAdapter.adaptFragment("""
                    #version 120
                    varying vec4 spriteBounds;
                    void main() { gl_FragColor = vec4(spriteBounds.xy, spriteBounds.zw); }
                    """);
            compile(compiler, options, terrainSpriteBounds, "terrain-sprite-bounds-fragment-adapter", true);
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
            System.out.println("Preprocessed " + count + " example shaders, compiled " + compiled + " directly to Vulkan SPIR-V; 4 conditional-include checks plus entity/item-ID, entity-overlay/lightmap/MRT, entity-shadow, entity vertex/varying execution, damaged-block BLOCK-format lightmap inputs, hand-side/direction, cloud-fog/vertex/texture-normal inputs, armor-glint vertex, basic leash/line vertex, sky-basic vertex inputs/varyings on all three native pipelines, beacon-beam flat varyings/MRT, weather-particle vertex/lightmap/varyings, fragment MRT/sampler adapters, Iris tex sampler alias and MRT outputs, terrain-view-position, custom terrain vertex main/varyings, terrain-abs-midcoordpos, and terrain-sprite-bounds fixtures passed.");
            System.out.println("Unbound fragment-output dataflow compiled to Vulkan SPIR-V.");
        } finally {
            shaderc_compile_options_release(options);
            shaderc_compiler_release(compiler);
        }
    }

    private static String weatherNativeVertexSource() {
        return """
                #version 460
                layout(location = 0) in vec3 Position;
                layout(location = 1) in vec2 UV0;
                layout(location = 2) in vec4 Color;
                layout(location = 3) in ivec2 UV2;
                out float sphericalVertexDistance;
                out float cylindricalVertexDistance;
                out vec2 texCoord0;
                out vec4 vertexColor;
                layout(set = 0, binding = 0, std140) uniform NativeTransforms {
                    mat4 ModelViewMat;
                    mat4 ProjMat;
                    mat4 TextureMat;
                    vec4 ColorModulator;
                };
                uniform sampler2D Sampler2;
                void main() {
                    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
                    sphericalVertexDistance = length(Position);
                    cylindricalVertexDistance = length(Position.xz);
                    texCoord0 = UV0;
                    vertexColor = Color * texelFetch(Sampler2, UV2 / 16, 0);
                }
                """;
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

    private static String entityNativeVertexSource() {
        return """
                #version 460
                layout(location = 0) in vec3 Position;
                layout(location = 1) in vec4 Color;
                layout(location = 2) in vec2 UV0;
                layout(location = 3) in ivec2 UV1;
                layout(location = 4) in ivec2 UV2;
                layout(location = 5) in vec3 Normal;
                layout(location = 0) out vec4 vertexColor;
                layout(location = 1) out vec2 texCoord0;
                layout(location = 2) out vec4 lightMapColor;
                layout(location = 3) out vec4 overlayColor;
                layout(set = 0, binding = 0, std140) uniform DynamicTransforms {
                    mat4 ModelViewMat;
                    mat4 ProjMat;
                    mat4 TextureMat;
                };
                void main() {
                    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
                    vertexColor = Color;
                    texCoord0 = (TextureMat * vec4(UV0, 0.0, 1.0)).xy;
                    lightMapColor = vec4(vec2(UV2), 0.0, 1.0);
                    overlayColor = vec4(vec2(UV1), 0.0, 1.0);
                }
                """;
    }

    private static String damagedBlockNativeVertexSource() {
        return """
                #version 460
                in vec3 Position;
                in vec4 Color;
                in vec2 UV0;
                in vec2 UV2;
                in vec3 Normal;
                layout(location = 0) out vec4 vertexColor;
                layout(location = 1) out vec2 texCoord0;
                layout(location = 2) out vec2 texCoord2;
                layout(set = 0, binding = 0, std140) uniform DynamicTransforms {
                    mat4 ModelViewMat;
                    mat4 ProjMat;
                    mat4 TextureMat;
                };
                void main() {
                    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
                    vertexColor = Color;
                    texCoord0 = UV0;
                    texCoord2 = UV2;
                }
                """;
    }

    private static String appendCloudUniformFields(String source,
            java.util.List<ShaderPackUniformAdapter.UniformField> fields) {
        if (fields.isEmpty()) return source;
        String marker = "float FogCloudsEnd;";
        int markerIndex = source.indexOf(marker);
        if (markerIndex < 0) throw new AssertionError("Cloud vertex fixture has no FogCloudsEnd field.");
        int lineEnd = source.indexOf('\n', markerIndex);
        StringBuilder additions = new StringBuilder();
        for (ShaderPackUniformAdapter.UniformField field : fields) {
            additions.append("    ").append(field.type()).append(' ').append(field.name()).append(";\n");
        }
        return source.substring(0, lineEnd + 1) + additions + source.substring(lineEnd + 1);
    }
}
