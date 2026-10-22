package dev.veyra.shader;

import dev.veyra.vulkan.VeyraTextureSlots;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/** Auxiliary color inputs available to supported geometry programs. */
public final class GeometryColorSamplers {
    public static final int FIRST_IMAGE_SLOT = 6;
    public static final int MAX_INPUTS = 15;
    private static final List<String> LEGACY = List.of(
            "gcolor", "gdepth", "gnormal", "composite", "gaux1", "gaux2", "gaux3", "gaux4");
    private static final Pattern DECLARATION = Pattern.compile(
            "(?m)^[\\t ]*uniform\\s+(?:(?:lowp|mediump|highp)\\s+)?(sampler\\w+)\\s+(\\w+)\\s*;");

    private GeometryColorSamplers() { }

    public static Map<String, Integer> read(String source) throws IOException {
        String active = UnusedSamplerDeclarations.removeUnused(ShaderSource.maskBlockAndLineComments(source));
        var matcher = DECLARATION.matcher(active);
        Map<String, Integer> inputs = new LinkedHashMap<>();
        while (matcher.find()) {
            String name = matcher.group(2);
            int index = LEGACY.indexOf(name);
            if (name.matches("colortex\\d+")) {
                try { index = Integer.parseInt(name.substring(8)); }
                catch (NumberFormatException exception) { throw new IOException("Invalid color sampler: " + name, exception); }
            }
            if (index < 0) continue;
            if (index == 0 || index > 15)
                throw new IOException("Geometry can sample colortex1..15; scene color is not a geometry input: " + name);
            if (!matcher.group(1).equals("sampler2D")) throw new IOException(name + " must use sampler2D.");
            inputs.put(name, index);
        }
        Set<Integer> indices = indices(inputs);
        if (indices.size() > MAX_INPUTS) throw new IOException("Geometry exceeds Veyra's " + MAX_INPUTS
                + " auxiliary color sampler slots.");
        return Map.copyOf(inputs);
    }

    public static Set<Integer> indices(Map<String, Integer> inputs) { return new TreeSet<>(inputs.values()); }
    public static String samplerName(int index) { return "VeyraGeometryColor" + index + "Sampler"; }

    static void validatePipelineSamplerCount(int count) throws IOException {
        VeyraTextureSlots.validateSamplerCount(count);
    }
}
