package dev.veyra.shader;

import java.io.IOException;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Iris render-stage constants and the stage values available to Veyra's split native pipelines. */
final class IrisRenderStages {
    // Keep this order aligned with Iris WorldRenderingPhase; Iris exposes these enum ordinals as macros.
    private enum Stage {
        NONE,
        SKY,
        SUNSET,
        CUSTOM_SKY,
        SUN,
        MOON,
        STARS,
        VOID,
        TERRAIN_SOLID,
        TERRAIN_CUTOUT_MIPPED,
        TERRAIN_CUTOUT,
        ENTITIES,
        BLOCK_ENTITIES,
        DESTROY,
        OUTLINE,
        DEBUG,
        HAND_SOLID,
        TERRAIN_TRANSLUCENT,
        TRIPWIRE,
        PARTICLES,
        CLOUDS,
        RAIN_SNOW,
        WORLD_BORDER,
        HAND_TRANSLUCENT
    }

    private static final Pattern UNIFORM = Pattern.compile(
            "(?m)^[\\t ]*uniform\\s+(?:(?:lowp|mediump|highp)\\s+)?"
                    + "([A-Za-z_][A-Za-z0-9_]*)\\s+renderStage\\s*;[\\t ]*(?://[^\\r\\n]*)?(?:\\r?\\n|$)");

    private IrisRenderStages() { }

    static int sunStage() { return Stage.SUN.ordinal(); }
    static int moonStage() { return Stage.MOON.ordinal(); }
    static int voidStage() { return Stage.VOID.ordinal(); }

    static Integer forShadowTerrainProgram(String programName) {
        return switch (programName) {
            case "shadow_solid" -> Stage.TERRAIN_SOLID.ordinal();
            case "shadow_cutout" -> Stage.TERRAIN_CUTOUT.ordinal();
            case "shadow_water" -> Stage.TERRAIN_TRANSLUCENT.ordinal();
            case "shadow_tripwire" -> Stage.TRIPWIRE.ordinal();
            default -> null;
        };
    }

    static List<Integer> skyTexturedStages(String pipelinePath) {
        return switch (pipelinePath) {
            case "pipeline/celestial" -> List.of(Stage.SUN.ordinal(), Stage.MOON.ordinal(), Stage.VOID.ordinal());
            case "pipeline/end_sky" -> List.of(Stage.VOID.ordinal());
            default -> List.of();
        };
    }

    static boolean hasRenderStageUniform(String source) {
        return UNIFORM.matcher(ShaderSource.maskBlockAndLineComments(source)).find();
    }

    static Map<String, String> macros() {
        Map<String, String> macros = new LinkedHashMap<>();
        for (Stage stage : Stage.values()) {
            macros.put("MC_RENDER_STAGE_" + stage.name(), Integer.toString(stage.ordinal()));
        }
        return Map.copyOf(macros);
    }

    static Integer forSkyFormat(BasicVertexShaderAdapter.Format format) {
        return switch (format) {
            case SKY -> Stage.SKY.ordinal();
            case STARS -> Stage.STARS.ordinal();
            case SUNRISE_SUNSET -> Stage.SUNSET.ordinal();
            case LEASH, LINE, LIGHTNING, SKY_TEXTURED, SKY_TEXTURED_COLOR, TEXTURED, TEXTURED_COLOR, WEATHER -> null;
        };
    }

    static Integer forSkyPipeline(String pipelinePath) {
        return switch (pipelinePath) {
            case "pipeline/sky" -> Stage.SKY.ordinal();
            case "pipeline/stars" -> Stage.STARS.ordinal();
            case "pipeline/sunrise_sunset" -> Stage.SUNSET.ordinal();
            default -> null;
        };
    }

    /** Replaces Iris's per-draw uniform with the constant for a native sky pipeline, when applicable. */
    static String bindUniform(String source, Integer stage, String programName) throws IOException {
        if (stage == null) return source;
        Matcher declaration = UNIFORM.matcher(ShaderSource.maskBlockAndLineComments(source));
        if (!declaration.find()) return source;
        if (!declaration.group(1).equals("int")) {
            throw new IOException(programName + " uniform 'renderStage' must have type int.");
        }
        int start = declaration.start();
        int end = declaration.end();
        if (declaration.find()) {
            throw new IOException(programName + " declares uniform 'renderStage' more than once.");
        }
        return source.substring(0, start) + "const int renderStage = " + stage + ";\n" + source.substring(end);
    }
}
