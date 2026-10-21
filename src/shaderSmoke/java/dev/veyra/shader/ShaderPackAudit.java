package dev.veyra.shader;

import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
            // Match runtime selection: adapters see the pack's shaders.properties custom uniforms.
            CustomUniforms.activate(pack.readCustomUniforms());
            List<String> errors = new ArrayList<>();
            String capabilityError = "";
            int options = 0, profiles = 0, stages = 0, preprocessed = 0, accepted = 0, rejected = 0, geometryVertices = 0, compute = 0;
            if (pack.supportsShaderPrograms()) {
                try { pack.validatePipelineCapabilities(); }
                catch (Exception exception) { capabilityError = exception.getMessage(); }
                try { var specification = pack.readOptions(); options = specification.options().size(); profiles = specification.profiles().size(); }
                catch (Exception exception) { errors.add("options: " + exception.getMessage()); }
                for (String file : pack.stageFiles().stream().sorted().toList()) {
                    stages++;
                    if (file.endsWith(".csh")) compute++;
                    if (file.endsWith(".vsh") && (file.startsWith("gbuffers_") || file.startsWith("shadow"))) geometryVertices++;
                    try {
                        String source = pack.readStage(file).orElseThrow();
                        preprocessed++;
                        if (!file.endsWith(".fsh")) continue;
                        String program = file.substring(0, file.length() - 4);
                        try {
                            if (program.startsWith("gbuffers_terrain") || program.equals("gbuffers_water")) {
                                Map<String, TerrainVertexShaderAdapter.PackVarying> vertexOutputs =
                                        terrainVertexOutputs(pack, program, source);
                                TerrainShaderAdapter.adaptFragment(source, pack.hasShadowFragment(),
                                        program.equals("gbuffers_water"), vertexOutputs);
                            }
                            else if (program.startsWith("gbuffers_entities") || program.startsWith("gbuffers_hand")
                                    || program.startsWith("gbuffers_block") || program.equals("gbuffers_spidereyes")) {
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
                            else if (program.equals("gbuffers_clouds")) CloudShaderAdapter.adaptFragment(source);
                            else continue; // Other bridges require pipeline-specific VulkanMod vertex inputs.
                            accepted++;
                        } catch (Exception exception) { rejected++; errors.add(file + " adapter: " + exception.getMessage()); }
                    } catch (Exception exception) { errors.add(file + " preprocess: " + exception.getMessage()); }
                }
                try {
                    pack.readCloudVertex().ifPresent(source -> {
                        try {
                            CloudShaderAdapter.adaptVertexWithMetadata(vulkanModCloudVertex, source);
                        } catch (IOException exception) {
                            errors.add("gbuffers_clouds.vsh adapter: " + exception.getMessage());
                        }
                    });
                } catch (Exception exception) {
                    errors.add("gbuffers_clouds.vsh preprocess: " + exception.getMessage());
                }
            } else errors.add(pack.issue());
            System.out.println("{\"pack\":" + quote(pack.path().getFileName().toString()) + ",\"discovered\":" + pack.supportsShaderPrograms()
                    + ",\"dimension\":" + dimension + ",\"profile\":" + quote(profile) + ",\"capabilityError\":" + quote(capabilityError)
                    + ",\"options\":" + options + ",\"profiles\":" + profiles + ",\"stages\":" + stages + ",\"preprocessed\":" + preprocessed
                    + ",\"geometryFragmentsAccepted\":" + accepted + ",\"geometryFragmentsRejected\":" + rejected + ",\"geometryVertexStages\":" + geometryVertices
                    + ",\"computeStages\":" + compute + ",\"errorCount\":" + errors.size() + ",\"errors\":["
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
        Map<String, TerrainVertexShaderAdapter.PackVarying> descriptions =
                TerrainVertexShaderAdapter.describeVaryings(program, vertex.get());
        var unusedLocations = TerrainShaderAdapter.unusedTerrainVertexOutputs(fragmentSource, descriptions);
        return TerrainVertexShaderAdapter.validateAndGetVaryings(program, vertex.get(), unusedLocations);
    }

    private static Map<String, TerrainVertexShaderAdapter.PackVarying> entityVertexOutputs(
            ShaderPack pack, String program) throws IOException {
        var vertex = pack.readStage(program + ".vsh");
        if (vertex.isEmpty()) return Map.of();
        return EntityVertexShaderAdapter.describeVaryings(program, vertex.get());
    }
}
