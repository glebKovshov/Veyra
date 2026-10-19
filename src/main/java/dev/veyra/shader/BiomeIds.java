package dev.veyra.shader;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Iris biome ids: vanilla biomes numbered in {@code Biomes.register} order, exposed as the {@code biome}
 * uniform and {@code BIOME_<NAME>} constants. Unregistered (for example datapack) biomes read 0, as in Iris.
 */
public final class BiomeIds {
    private static final Map<String, Integer> IDS = new LinkedHashMap<>();
    private static final Map<String, Integer> CONSTANTS = new LinkedHashMap<>();

    private BiomeIds() {
    }

    /** Called from the Biomes registration mixin in registration order. */
    public static synchronized void register(String namespacedId) {
        if (IDS.containsKey(namespacedId)) return;
        int id = IDS.size();
        IDS.put(namespacedId, id);
        String path = namespacedId.substring(namespacedId.indexOf(':') + 1);
        CONSTANTS.put("BIOME_" + path.toUpperCase(Locale.ROOT), id);
    }

    public static synchronized int id(String namespacedId) {
        return IDS.getOrDefault(namespacedId, 0);
    }

    /** Value of a BIOME_* or PPT_* constant, or null when it is not defined. */
    public static synchronized Integer constant(String name) {
        return switch (name) {
            case "PPT_NONE" -> 0;
            case "PPT_RAIN" -> 1;
            case "PPT_SNOW" -> 2;
            default -> CONSTANTS.get(name);
        };
    }

    /** Preprocessor defines matching Iris' BIOME_* and PPT_* macros. */
    public static synchronized Map<String, String> defines() {
        Map<String, String> defines = new LinkedHashMap<>();
        CONSTANTS.forEach((name, id) -> defines.put(name, String.valueOf(id)));
        defines.put("PPT_NONE", "0");
        defines.put("PPT_RAIN", "1");
        defines.put("PPT_SNOW", "2");
        return defines;
    }
}
