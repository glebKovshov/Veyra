package dev.veyra.shader;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import static org.lwjgl.vulkan.VK10.*;

/** Iris blend properties translated to Vulkan factors, indexed by physical colortex. */
public record GeometryBlendSettings(Mode global, Map<Integer, Mode> targets) {
    private static final List<String> LEGACY = List.of("gcolor", "gdepth", "gnormal", "composite", "gaux1", "gaux2", "gaux3", "gaux4");
    private static final Map<String, Integer> FACTORS = Map.ofEntries(
            Map.entry("ZERO", VK_BLEND_FACTOR_ZERO), Map.entry("ONE", VK_BLEND_FACTOR_ONE),
            Map.entry("SRC_COLOR", VK_BLEND_FACTOR_SRC_COLOR), Map.entry("ONE_MINUS_SRC_COLOR", VK_BLEND_FACTOR_ONE_MINUS_SRC_COLOR),
            Map.entry("DST_COLOR", VK_BLEND_FACTOR_DST_COLOR), Map.entry("ONE_MINUS_DST_COLOR", VK_BLEND_FACTOR_ONE_MINUS_DST_COLOR),
            Map.entry("SRC_ALPHA", VK_BLEND_FACTOR_SRC_ALPHA), Map.entry("ONE_MINUS_SRC_ALPHA", VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA),
            Map.entry("DST_ALPHA", VK_BLEND_FACTOR_DST_ALPHA), Map.entry("ONE_MINUS_DST_ALPHA", VK_BLEND_FACTOR_ONE_MINUS_DST_ALPHA),
            Map.entry("SRC_ALPHA_SATURATE", VK_BLEND_FACTOR_SRC_ALPHA_SATURATE));

    public GeometryBlendSettings { targets = Map.copyOf(targets); }
    public record Mode(boolean enabled, int sourceColor, int destinationColor, int sourceAlpha, int destinationAlpha) { }
    public Mode forTarget(int target) { return targets.getOrDefault(target, global); }

    public static GeometryBlendSettings read(Properties properties, String program) throws IOException {
        String prefix = "blend." + program;
        Mode global = properties.containsKey(prefix) ? parse(properties.getProperty(prefix)) : null;
        Map<Integer, Mode> buffers = new LinkedHashMap<>();
        for (String key : properties.stringPropertyNames().stream().sorted().toList()) {
            if (!key.startsWith(prefix + ".")) continue;
            String name = key.substring(prefix.length() + 1);
            int index = LEGACY.indexOf(name);
            if (index < 0 && name.matches("colortex\\d+")) {
                try { index = Integer.parseInt(name.substring(8)); }
                catch (NumberFormatException exception) { throw new IOException("Invalid blend buffer: " + key, exception); }
            }
            if (index < 0 || index > 15) throw new IOException("Unknown blend buffer: " + key);
            if (buffers.putIfAbsent(index, parse(properties.getProperty(key))) != null)
                throw new IOException("Duplicate legacy/colortex blend property for buffer " + index + ".");
        }
        return new GeometryBlendSettings(global, buffers);
    }

    private static Mode parse(String text) throws IOException {
        if (text.strip().equals("off")) return new Mode(false, VK_BLEND_FACTOR_ONE, VK_BLEND_FACTOR_ZERO, VK_BLEND_FACTOR_ONE, VK_BLEND_FACTOR_ZERO);
        String[] names = text.strip().split("\\s+");
        if (names.length != 4) throw new IOException("Blend mode requires 'off' or four factors: " + text);
        int[] factors = new int[4];
        for (int i = 0; i < factors.length; i++) {
            Integer factor = FACTORS.get(names[i]);
            if (factor == null) throw new IOException("Unsupported blend factor: " + names[i]);
            factors[i] = factor;
        }
        return new Mode(true, factors[0], factors[1], factors[2], factors[3]);
    }
}
