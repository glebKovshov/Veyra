package dev.veyra.shader;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Iris only sets the uniforms it registers; any other non-opaque uniform keeps its GLSL initializer, or
 * 0 without one, because GL never receives a value for it. Veyra rewrites such declarations into
 * constants with the same value so the Vulkan adapters need not bind them. Uniforms that Iris does set
 * are left alone: Veyra either provides them or the adapters reject the program.
 */
final class IrisUnsetUniforms {
    /**
     * Uniform names Iris 1.21.11 (commit 0a1fcaa) supplies to shader-pack programs: direct
     * registrations, MatrixUniforms combinations, externally managed uniforms, and names its GLSL
     * transformers replace (centerDepthSmooth, chunkOffset, modelOffset and the core-profile matrices).
     */
    static final Set<String> IRIS_SET_UNIFORMS = Set.of(
            "centerDepthSmooth", "chunkOffset", "modelOffset", "modelViewMatrix", "modelViewMatrixInverse",
            "projectionMatrix", "projectionMatrixInverse", "normalMatrix", "textureMatrix",
            "BiomeTemp", "alphaTestRef", "ambientLight", "anisotropicFiltering", "aspectRatio", "atlasSize",
            "bedrockLevel", "biome", "biome_category", "biome_precipitation", "blendFunc", "blindFactor",
            "blindness", "blockEntityId", "burningSmooth", "cameraPosition", "cameraPositionFract",
            "cameraPositionInt", "chunkFadeTimeInv", "cloudHeight", "cloudTime", "constantMood",
            "currentColorSpace", "currentDate", "currentPlayerAir", "currentPlayerArmor", "currentPlayerHealth",
            "currentPlayerHunger", "currentRenderedItemId", "currentSelectedBlockId", "currentSelectedBlockPos",
            "currentTime", "currentYearTime", "darknessFactor", "darknessLightFactor", "dawnDusk", "day",
            "dhFarPlane", "dhNearPlane", "dhPreviousProjection", "dhProjection", "dhProjectionInverse",
            "dhRenderDistance", "effectStrength", "endFlashIntensity", "endFlashPosition", "entityColor",
            "entityId", "eyeAltitude", "eyeBrightness", "eyeBrightnessM", "eyeBrightnessSmooth", "eyePosition",
            "far", "feetInWater", "firstPersonCamera", "fogColor", "fogDensity", "fogEnd", "fogMode", "fogShape",
            "fogStart", "frameCounter", "frameTime", "frameTimeCounter", "frameTimeSmooth", "gbufferModelView",
            "gbufferModelViewInverse", "gbufferPreviousModelView", "gbufferPreviousProjection", "gbufferProjection",
            "gbufferProjectionInverse", "gtextureId", "gtextureSize", "hasCeiling", "hasSkylight", "heavyFog",
            "heightLimit", "heldBlockLightColor", "heldBlockLightColor2", "heldBlockLightValue",
            "heldBlockLightValue2", "heldItemId", "heldItemId2", "hideGUI", "inSwimmingAnimation",
            "iris_CameraTranslation", "iris_ColorModulator", "iris_FogColor", "iris_FogDensity", "iris_FogEnd",
            "iris_FogStart", "iris_GlintAlpha", "iris_LightmapTextureMatrix", "iris_ModelOffset", "iris_ModelScale",
            "iris_ModelViewMat", "iris_ModelViewMatrix", "iris_NormalMat", "iris_NormalMatrix", "iris_ProjMat",
            "iris_ProjectionMatrix", "iris_ScreenSize", "iris_TextureMat", "iris_TextureScale",
            "iris_currentAlphaTest", "isDry", "isElytraFlying", "isEyeInCave", "isEyeInWater",
            "isPrecipitationRain", "isRainy", "isRiding", "isRightHanded", "isSnowy", "isSpectator", "is_burning",
            "is_hurt", "is_invisible", "is_on_ground", "is_sneaking", "is_sprinting", "lightningBoltPosition",
            "logicalHeightLimit", "maxPlayerAir", "maxPlayerArmor", "maxPlayerHealth", "maxPlayerHunger",
            "moonBrightness", "moonPhase", "moonPosition", "near", "night", "nightVision", "pi", "playerBodyVector",
            "playerLookVector", "playerMood", "previousCameraPosition", "previousCameraPositionFract",
            "previousCameraPositionInt", "previousEndFlashIntensity", "rainFactor", "rainStrength", "rainStrengthS",
            "rainStrengthS2", "rainStrengthShiningStars", "rainfall", "relativeEyePosition",
            "relativeVehiclePosition", "renderStage", "screenBrightness", "seaLevel", "shadowAngle", "shadowFade",
            "shadowLightPosition", "shadowModelView", "shadowModelViewInverse", "shadowProjection",
            "shadowProjectionInverse", "shdFade", "skyColor", "sneakSmooth", "starter", "sunAngle", "sunPosition",
            "temperature", "textureFilteringMode", "textureReloadCount", "thunderStrength", "timeAngle",
            "timeBrightness", "touchmybody", "u_ModelScale", "u_ModelViewProjectionMatrix", "u_RegionOffset",
            "u_TextureScale", "upPosition", "vehicleId", "vehicleInWater", "vehicleLookVector", "velocity",
            "viewHeight", "viewWidth", "wetness", "worldDay", "worldTime");
    private static final Pattern DECLARATION = Pattern.compile(
            "(?m)^([\\t ]*)uniform[\\t ]+(?:(?:lowp|mediump|highp)[\\t ]+)?"
                    + "(float|int|uint|bool|(?:[biu]?vec[234])|(?:mat[234](?:x[234])?))[\\t ]+"
                    + "([A-Za-z_][A-Za-z0-9_]*)[\\t ]*(?:=[\\t ]*([^;\\r\\n]+?))?[\\t ]*;");

    private IrisUnsetUniforms() {
    }

    /**
     * Rewrites declarations of uniforms that Iris never sets into constants, and drops initializers
     * from uniforms Iris or the pack's custom uniforms do set.
     */
    static String apply(String source, Set<String> packCustomUniforms) {
        Matcher matcher = DECLARATION.matcher(source);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String indent = matcher.group(1);
            String type = matcher.group(2);
            String name = matcher.group(3);
            String initializer = matcher.group(4);
            String replacement;
            if (IRIS_SET_UNIFORMS.contains(name) || packCustomUniforms.contains(name)) {
                // Iris overwrites any initializer; keep a plain declaration for the adapters to bind.
                replacement = initializer == null ? matcher.group()
                        : indent + "uniform " + type + " " + name + ";";
            } else {
                replacement = indent + "const " + type + " " + name + " = "
                        + (initializer == null ? zero(type) : initializer.strip()) + ";";
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static String zero(String type) {
        return switch (type) {
            case "float" -> "0.0";
            case "int" -> "0";
            case "uint" -> "0u";
            case "bool" -> "false";
            default -> type + "(" + (type.startsWith("u") ? "0u" : type.startsWith("i") ? "0"
                    : type.startsWith("b") ? "false" : "0.0") + ")";
        };
    }
}
