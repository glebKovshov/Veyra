package dev.veyra.shader;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Iris 1.21.11 scalar and vec3 uniforms, plus Veyra compatibility helpers, that the renderer evaluates once
 * per frame and exposes through uniform blocks and custom-uniform inputs. Values are published by the renderer;
 * this class holds names, types and the latest snapshot so adapters and tests can use it without Minecraft.
 */
public final class IrisExtraUniforms {
    public enum Kind {
        FLOAT("float"), INT("int"), BOOL("bool"), VEC3("vec3");

        private final String glsl;

        Kind(String glsl) {
            this.glsl = glsl;
        }

        public String glsl() {
            return glsl;
        }
    }

    /** A uniform; {@code hardcodedCustom} marks Iris custom uniforms or Veyra helpers packs may redefine. */
    public record Definition(String name, Kind kind, boolean hardcodedCustom) {
    }

    private static final List<Definition> DEFINITIONS = List.of(
            // IrisExclusiveUniforms and world info.
            new Definition("heavyFog", Kind.BOOL, false),
            new Definition("heightLimit", Kind.INT, false),
            new Definition("logicalHeightLimit", Kind.INT, false),
            new Definition("hasCeiling", Kind.BOOL, false),
            new Definition("hasSkylight", Kind.BOOL, false),
            new Definition("ambientLight", Kind.FLOAT, false),
            new Definition("seaLevel", Kind.INT, false),
            new Definition("isElytraFlying", Kind.BOOL, false),
            new Definition("feetInWater", Kind.BOOL, false),
            new Definition("inSwimmingAnimation", Kind.BOOL, false),
            new Definition("isRiding", Kind.BOOL, false),
            new Definition("vehicleInWater", Kind.BOOL, false),
            new Definition("vehicleId", Kind.INT, false),
            new Definition("currentPlayerHealth", Kind.FLOAT, false),
            new Definition("maxPlayerHealth", Kind.FLOAT, false),
            new Definition("currentPlayerHunger", Kind.FLOAT, false),
            new Definition("maxPlayerHunger", Kind.FLOAT, false),
            new Definition("currentPlayerArmor", Kind.FLOAT, false),
            new Definition("maxPlayerArmor", Kind.FLOAT, false),
            new Definition("currentPlayerAir", Kind.FLOAT, false),
            new Definition("maxPlayerAir", Kind.FLOAT, false),
            new Definition("firstPersonCamera", Kind.BOOL, false),
            new Definition("currentColorSpace", Kind.INT, false),
            new Definition("chunkFadeTimeInv", Kind.FLOAT, false),
            new Definition("textureFilteringMode", Kind.INT, false),
            new Definition("playerBodyVector", Kind.VEC3, false),
            new Definition("vehicleLookVector", Kind.VEC3, false),
            new Definition("relativeVehiclePosition", Kind.VEC3, false),
            // CommonUniforms.
            new Definition("hideGUI", Kind.BOOL, false),
            new Definition("isRightHanded", Kind.BOOL, false),
            new Definition("anisotropicFiltering", Kind.INT, false),
            new Definition("framemod2", Kind.FLOAT, true),
            new Definition("framemod4", Kind.FLOAT, true),
            new Definition("framemod8", Kind.FLOAT, true),
            new Definition("framemod600", Kind.FLOAT, true),
            new Definition("pi", Kind.FLOAT, false),
            new Definition("dhFarPlane", Kind.FLOAT, false),
            new Definition("dhNearPlane", Kind.FLOAT, false),
            new Definition("dhRenderDistance", Kind.INT, false),
            // CelestialUniforms.
            new Definition("shadowAngle", Kind.FLOAT, false),
            new Definition("sunPosition", Kind.VEC3, false),
            new Definition("moonPosition", Kind.VEC3, false),
            new Definition("shadowLightPosition", Kind.VEC3, false),
            new Definition("upPosition", Kind.VEC3, false),
            new Definition("endFlashPosition", Kind.VEC3, false),
            // HardcodedCustomUniforms (BSL, Complementary and others redefine several of these).
            new Definition("timeAngle", Kind.FLOAT, true),
            new Definition("timeBrightness", Kind.FLOAT, true),
            new Definition("moonBrightness", Kind.FLOAT, true),
            new Definition("rainStrengthS", Kind.FLOAT, true),
            new Definition("rainStrengthShiningStars", Kind.FLOAT, true),
            new Definition("rainStrengthS2", Kind.FLOAT, true),
            new Definition("blindFactor", Kind.FLOAT, true),
            new Definition("isDry", Kind.FLOAT, true),
            new Definition("isRainy", Kind.FLOAT, true),
            new Definition("isSnowy", Kind.FLOAT, true),
            new Definition("isEyeInCave", Kind.FLOAT, true),
            new Definition("starter", Kind.FLOAT, true),
            new Definition("frameTimeSmooth", Kind.FLOAT, true),
            new Definition("eyeBrightnessM", Kind.FLOAT, true),
            new Definition("rainFactor", Kind.FLOAT, true),
            new Definition("BiomeTemp", Kind.FLOAT, true),
            new Definition("day", Kind.FLOAT, true),
            new Definition("night", Kind.FLOAT, true),
            new Definition("dawnDusk", Kind.FLOAT, true),
            new Definition("shdFade", Kind.FLOAT, true),
            new Definition("isPrecipitationRain", Kind.FLOAT, true),
            new Definition("touchmybody", Kind.FLOAT, true),
            new Definition("sneakSmooth", Kind.FLOAT, true),
            new Definition("burningSmooth", Kind.FLOAT, true),
            new Definition("effectStrength", Kind.FLOAT, true));
    private static final Map<String, Definition> BY_NAME = DEFINITIONS.stream()
            .collect(Collectors.toUnmodifiableMap(Definition::name, definition -> definition));
    /** Compatibility helpers computed by Veyra; they are not registered by Iris 1.21.11. */
    public static final Set<String> VEYRA_COMPATIBILITY_UNIFORMS = Set.of(
            "framemod2", "framemod4", "framemod8", "framemod600");
    /** Iris hardcoded custom uniforms; a shaders.properties definition with the same name wins. */
    public static final Set<String> HARDCODED_CUSTOM = DEFINITIONS.stream().filter(Definition::hardcodedCustom)
            .map(Definition::name).collect(Collectors.toUnmodifiableSet());
    private static final float[] ZERO_VECTOR = new float[3];

    private record Snapshot(Map<String, Double> scalars, Map<String, float[]> vectors) {
    }

    private static volatile Snapshot snapshot = new Snapshot(Map.of(), Map.of());

    private IrisExtraUniforms() {
    }

    public static List<Definition> definitions() {
        return DEFINITIONS;
    }

    public static Definition definition(String name) {
        return BY_NAME.get(name);
    }

    public static boolean contains(String name) {
        return BY_NAME.containsKey(name);
    }

    /** Names usable as custom-uniform expression inputs: scalars and vec3 components. */
    public static Set<String> expressionInputs() {
        Set<String> inputs = new java.util.LinkedHashSet<>();
        for (Definition definition : DEFINITIONS) {
            if (definition.kind() == Kind.VEC3) {
                inputs.add(definition.name() + ".x");
                inputs.add(definition.name() + ".y");
                inputs.add(definition.name() + ".z");
            } else {
                inputs.add(definition.name());
            }
        }
        return Set.copyOf(inputs);
    }

    /** Publishes one frame of values; missing entries read as zero. */
    public static void publish(Map<String, Double> scalars, Map<String, float[]> vectors) {
        snapshot = new Snapshot(Map.copyOf(scalars), Map.copyOf(new LinkedHashMap<>(vectors)));
    }

    public static double scalar(String name) {
        Double value = snapshot.scalars().get(name);
        return value == null ? 0.0 : value;
    }

    /** Scalar value or vec3 component ({@code name.x}) for custom-uniform expressions, or null. */
    public static Double expressionInput(String name) {
        int dot = name.indexOf('.');
        if (dot < 0) {
            return contains(name) ? scalar(name) : null;
        }
        Definition definition = definition(name.substring(0, dot));
        if (definition == null || definition.kind() != Kind.VEC3) return null;
        float[] vector = vector(definition.name());
        return switch (name.substring(dot + 1)) {
            case "x" -> (double) vector[0];
            case "y" -> (double) vector[1];
            case "z" -> (double) vector[2];
            default -> null;
        };
    }

    public static float[] vector(String name) {
        float[] value = snapshot.vectors().get(name);
        return value == null ? ZERO_VECTOR : value;
    }
}
