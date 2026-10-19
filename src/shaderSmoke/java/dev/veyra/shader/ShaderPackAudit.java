package dev.veyra.shader;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Read-only audit of caller-provided pack archives; it never renders or redistributes them. */
public final class ShaderPackAudit {
    private static String quote(String text) {
        return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t") + "\"";
    }
    public static void main(String[] args) throws Exception {
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
                            if (program.startsWith("gbuffers_terrain") || program.equals("gbuffers_water"))
                                TerrainShaderAdapter.adaptFragment(source, pack.hasShadowFragment(), program.equals("gbuffers_water"));
                            else if (program.startsWith("gbuffers_entities") || program.startsWith("gbuffers_hand")
                                    || program.startsWith("gbuffers_block") || program.equals("gbuffers_spidereyes")) {
                                EntityShaderAdapter.adaptFragment(source, program,
                                        program.startsWith("gbuffers_hand"), pack.hasShadowFragment());
                            }
                            else if (program.equals("gbuffers_clouds")) CloudShaderAdapter.adaptFragment(source);
                            else continue; // Other bridges require pipeline-specific VulkanMod vertex inputs.
                            accepted++;
                        } catch (Exception exception) { rejected++; errors.add(file + " adapter: " + exception.getMessage()); }
                    } catch (Exception exception) { errors.add(file + " preprocess: " + exception.getMessage()); }
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
}
