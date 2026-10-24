package dev.veyra.shader;

import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.io.IOException;
import java.io.InputStream;

/** Read-only audit of caller-provided pack archives; it never renders or redistributes them. */
public final class ShaderPackAudit {
    private static String quote(String text) {
        return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t") + "\"";
    }
    /**
     * At runtime BiomesMixin numbers vanilla biomes in Biomes.register order, which defines Iris'
     * biome ids and BIOME_* constants. Offline, read the same declaration order from Minecraft's
     * Biomes class when the game jar is on the audit classpath; otherwise BIOME_* stay undefined.
     */
    private static void seedVanillaBiomeIds() {
        try {
            Class<?> biomes = Class.forName("net.minecraft.world.level.biome.Biomes");
            int count = 0;
            for (java.lang.reflect.Field field : biomes.getDeclaredFields()) {
                if (!java.lang.reflect.Modifier.isStatic(field.getModifiers())
                        || !field.getType().getName().equals("net.minecraft.resources.ResourceKey")) continue;
                Object key = field.get(null);
                BiomeIds.register(key.getClass().getMethod("identifier").invoke(key).toString());
                count++;
            }
            System.err.println("Seeded " + count + " vanilla biome ids from Minecraft's Biomes class.");
        } catch (ReflectiveOperationException | LinkageError exception) {
            System.err.println("Minecraft's Biomes class is unavailable; BIOME_* constants stay undefined: " + exception);
        }
    }

    public static void main(String[] args) throws Exception {
        seedVanillaBiomeIds();
        String vulkanModCloudVertex = readVulkanModCloudVertex();
        int dimension = 0;
        String profile = "";
        for (String argument : args) {
            if (argument.startsWith("--dimension=")) { dimension = Integer.parseInt(argument.substring(12)); continue; }
            if (argument.startsWith("--profile=")) { profile = argument.substring(10); continue; }
            ShaderPack pack = ShaderPack.inspect(Path.of(argument)).configured(Map.of(), dimension);
            if (!profile.isEmpty()) {
                var values = pack.readOptions().profiles().get(profile);
                if (values == null) throw new IllegalArgumentException("Unknown profile '" + profile + "' in " + argument);
                pack = pack.configured(pack.readOptions().validate(values), dimension);
            }
            pack = pack.withRuntimeFeatures();
            // Match runtime selection: adapters see the pack's shaders.properties custom uniforms.
            CustomUniforms.activate(pack.readCustomUniforms());
            List<String> errors = new ArrayList<>();
            String capabilityError = "";
            int options = 0, profiles = 0, stages = 0, preprocessed = 0, accepted = 0, rejected = 0,
                    geometryVertices = 0, compute = 0, computeAccepted = 0, computeRejected = 0;
            List<String> activeComputeStages = new ArrayList<>();
            if (pack.supportsShaderPrograms()) {
                try { activeComputeStages.addAll(pack.activeComputeStageFiles()); }
                catch (Exception exception) { errors.add("compute-stage selection: " + exception.getMessage()); }
            }
            if (pack.supportsShaderPrograms()) {
                try { pack.validatePipelineCapabilities(); }
                catch (Exception exception) { capabilityError = exception.getMessage(); }
                try { var specification = pack.readOptions(); options = specification.options().size(); profiles = specification.profiles().size(); }
                catch (Exception exception) { errors.add("options: " + exception.getMessage()); }
                for (String file : pack.stageFiles().stream().sorted().toList()) {
                    stages++;
                    if (file.endsWith(".csh")) {
                        compute++;
                        if (!activeComputeStages.contains(file)) continue;
                    }
                    if (file.endsWith(".vsh") && (file.startsWith("gbuffers_") || file.startsWith("shadow"))) geometryVertices++;
                    try {
                        String source = (file.endsWith(".csh")
                                ? pack.readComputeStage(file) : pack.readStage(file)).orElseThrow();
                        preprocessed++;
                        if (file.endsWith(".csh")) {
                            try {
                                ComputeShaderAdapter.AdaptedStage adapted = ComputeShaderAdapter.adapt(
                                        file, source, pack.readImageProperties());
                                ShaderCompileSmoke.compileCompute(adapted.source(),
                                        pack.path().getFileName() + "/" + profile + "/" + file);
                                computeAccepted++;
                            } catch (Exception exception) {
                                computeRejected++;
                                errors.add(file + " compute adapter/compiler: " + exception.getMessage());
                            }
                            continue;
                        }
                        if (!file.endsWith(".fsh")) continue;
                        String program = file.substring(0, file.length() - 4);
                        try {
                            if (program.startsWith("gbuffers_terrain") || program.equals("gbuffers_water")) {
                                source = ShaderPackImageBindings.prepare(file, source, pack.readImageProperties()).source();
                                Map<String, TerrainVertexShaderAdapter.PackVarying> vertexOutputs =
                                        terrainVertexOutputs(pack, program, source);
                                TerrainShaderAdapter.adaptFragment(source, pack.hasShadowFragment(),
                                        program.equals("gbuffers_water"), vertexOutputs);
                            }
                            else if (program.startsWith("gbuffers_entities") || program.startsWith("gbuffers_hand")
                                    || program.startsWith("gbuffers_block") || program.equals("gbuffers_spidereyes")) {
                                source = ShaderPackImageBindings.prepare(file, source, pack.readImageProperties()).source();
                                Map<String, TerrainVertexShaderAdapter.PackVarying> vertexVaryings =
                                        entityVertexOutputs(pack, program);
                                EntityShaderAdapter.adaptGeometryFragment(source, program,
                                        program.startsWith("gbuffers_hand"), pack.hasShadowFragment(),
                                        -1, -1, ItemIdMap.UNMAPPED, true, false, vertexVaryings);
                            }
                            else if (program.equals("gbuffers_damagedblock")) {
                                var vertex = pack.readDamagedBlockVertex();
                                if (vertex.isPresent()) {
                                    Map<String, TerrainVertexShaderAdapter.PackVarying> vertexVaryings =
                                            EntityVertexShaderAdapter.describeVaryings(program, vertex.get());
                                    DamagedBlockShaderAdapter.adaptFragment(source, program, vertexVaryings,
                                            pack.hasShadowFragment(), false);
                                } else {
                                    DamagedBlockShaderAdapter.adaptFragment(source, program);
                                }
                                FragmentOutputs.parse(GlslPreprocessor.process(program + ".fsh", source));
                                pack.readGeometryBlendSettings(program);
                            }
                            else if (program.equals("gbuffers_weather")) {
                                source = ShaderPackImageBindings.prepare(file, source, pack.readImageProperties()).source();
                                Map<String, TerrainVertexShaderAdapter.PackVarying> vertexVaryings =
                                        pack.readStage(program + ".vsh").isPresent()
                                                ? BasicVertexShaderAdapter.describeVaryings(program,
                                                        pack.readStage(program + ".vsh").orElseThrow(),
                                                        BasicVertexShaderAdapter.Format.WEATHER)
                                                : Map.of();
                                FragmentOutputs.parse(GlslPreprocessor.process(program + ".fsh", source));
                                pack.readGeometryBlendSettings(program);
                                TextureColorDistanceShaderAdapter.adaptFragment(source, program, vertexVaryings,
                                        pack.hasShadowFragment(), true);
                            }
                            else if (program.equals("gbuffers_textured")) {
                                source = ShaderPackImageBindings.prepare(file, source, pack.readImageProperties()).source();
                                Map<String, TerrainVertexShaderAdapter.PackVarying> vertexVaryings =
                                        signTextVertexOutputs(pack, program);
                                Set<String> customTextureSamplers = TerrainShaderAdapter.usedCustomTextureSamplers(
                                        source, pack.readCustomTextureProperties().samplersForProgram(program));
                                FragmentOutputs.parse(GlslPreprocessor.process(file, source));
                                pack.readGeometryBlendSettings(program);
                                EntityShaderAdapter.adaptTextFragment(source, program, pack.hasShadowFragment(),
                                        -1, -1, ItemIdMap.UNMAPPED, customTextureSamplers, vertexVaryings);
                            }
                            else if (program.equals("gbuffers_clouds")) {
                                var prepared = ShaderPackImageBindings.prepare(file, source, pack.readImageProperties());
                                source = prepared.source();
                                Set<String> customSamplers = prepared.resources().stream()
                                        .filter(resource -> !resource.storageImage())
                                        .map(ComputeShaderAdapter.OpaqueResource::name)
                                        .collect(java.util.stream.Collectors.toUnmodifiableSet());
                                CloudShaderAdapter.adaptFragment(source, List.of(), customSamplers);
                            }
                            else continue; // Other bridges require pipeline-specific VulkanMod vertex inputs.
                            accepted++;
                        } catch (Exception exception) { rejected++; errors.add(file + " adapter: " + exception.getMessage()); }
                    } catch (Exception exception) { errors.add(file + " preprocess: " + exception.getMessage()); }
                }
                try {
                    var cloudVertex = pack.readCloudVertex();
                    if (cloudVertex.isPresent()) {
                        try {
                            var prepared = ShaderPackImageBindings.prepare("gbuffers_clouds.vsh", cloudVertex.get(),
                                    pack.readImageProperties());
                            CloudShaderAdapter.adaptVertexWithMetadata(vulkanModCloudVertex, prepared.source());
                        } catch (IOException exception) {
                            errors.add("gbuffers_clouds.vsh adapter: " + exception.getMessage());
                        }
                    }
                } catch (Exception exception) {
                    errors.add("gbuffers_clouds.vsh preprocess: " + exception.getMessage());
                }
            } else errors.add(pack.issue());
            System.out.println("{\"pack\":" + quote(pack.path().getFileName().toString()) + ",\"discovered\":" + pack.supportsShaderPrograms()
                    + ",\"dimension\":" + dimension + ",\"profile\":" + quote(profile) + ",\"capabilityError\":" + quote(capabilityError)
                    + ",\"options\":" + options + ",\"profiles\":" + profiles + ",\"stages\":" + stages + ",\"preprocessed\":" + preprocessed
                    + ",\"geometryFragmentsAccepted\":" + accepted + ",\"geometryFragmentsRejected\":" + rejected + ",\"geometryVertexStages\":" + geometryVertices
                    + ",\"computeStages\":" + compute + ",\"computeStagesActive\":" + activeComputeStages.size()
                    + ",\"computeStagesAccepted\":" + computeAccepted
                    + ",\"computeStagesRejected\":" + computeRejected + ",\"errorCount\":" + errors.size() + ",\"errors\":["
                    + String.join(",", errors.stream().limit(16).map(ShaderPackAudit::quote).toList()) + "],\"visualValidation\":false}");
        }
    }

    private static String readVulkanModCloudVertex() throws IOException {
        String resource = "assets/vulkanmod/shaders/basic/clouds/clouds.vsh";
        try (InputStream input = ShaderPackAudit.class.getClassLoader().getResourceAsStream(resource)) {
            if (input == null) throw new IOException("VulkanMod cloud vertex shader is missing from the audit classpath.");
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static Map<String, TerrainVertexShaderAdapter.PackVarying> terrainVertexOutputs(
            ShaderPack pack, String program, String fragmentSource) throws IOException {
        var vertex = pack.readStage(program + ".vsh");
        if (vertex.isEmpty()) return Map.of();
        String vertexSource = ShaderPackImageBindings.prepare(program + ".vsh", vertex.get(),
                pack.readImageProperties()).source();
        Map<String, TerrainVertexShaderAdapter.PackVarying> descriptions =
                TerrainVertexShaderAdapter.describeVaryings(program, vertexSource);
        var unusedLocations = TerrainShaderAdapter.unusedTerrainVertexOutputs(fragmentSource, descriptions);
        return TerrainVertexShaderAdapter.validateAndGetVaryings(program, vertexSource, unusedLocations);
    }

    private static Map<String, TerrainVertexShaderAdapter.PackVarying> entityVertexOutputs(
            ShaderPack pack, String program) throws IOException {
        var vertex = pack.readStage(program + ".vsh");
        if (vertex.isEmpty()) return Map.of();
        return EntityVertexShaderAdapter.describeVaryings(program, vertex.get());
    }

    private static Map<String, TerrainVertexShaderAdapter.PackVarying> signTextVertexOutputs(
            ShaderPack pack, String program) throws IOException {
        var vertex = pack.readStage(program + ".vsh");
        if (vertex.isEmpty()) return Map.of();
        var outputs = BasicVertexShaderAdapter.describeVaryings(
                program, vertex.get(), BasicVertexShaderAdapter.Format.SIGN_TEXT);
        BasicVertexShaderAdapter.describeVaryings(
                program, vertex.get(), BasicVertexShaderAdapter.Format.SIGN_TEXT_FULLBRIGHT);
        return outputs;
    }
}
