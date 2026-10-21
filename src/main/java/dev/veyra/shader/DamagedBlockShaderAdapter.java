package dev.veyra.shader;

import java.io.IOException;
import java.util.Map;
import java.util.regex.Pattern;

/** Adapts Iris damaged-block fragments to Minecraft's crumbling render pipeline. */
public final class DamagedBlockShaderAdapter {
    private static final Pattern VERSION_DIRECTIVE = Pattern.compile("(?m)^[\\t ]*#version[^\\r\\n]*(?:\\r?\\n|$)");
    private static final Pattern ALPHA_TEST_UNIFORM = Pattern.compile(
            "(?m)^[\\t ]*uniform\\s+(?:(?:lowp|mediump|highp)\\s+)?float\\s+alphaTestRef\\s*;[\\t ]*(?://[^\\r\\n]*)?(?:\\r?\\n|$)");
    private static final Pattern ALPHA_TEST_DEFINE = Pattern.compile("(?m)^[\\t ]*#\\s*define\\s+alphaTestRef\\b");

    private DamagedBlockShaderAdapter() {
    }

    public static String adaptFragment(String source, String programName) throws IOException {
        return adaptFragment(source, programName, null, true, true);
    }

    static String adaptFragment(String source, String programName,
                                Map<String, TerrainVertexShaderAdapter.PackVarying> vertexOutputs,
                                boolean shadowPassAvailable, boolean lightmapSamplerAvailable) throws IOException {
        String adapted = VERSION_DIRECTIVE.matcher(source.replace("\uFEFF", "")).replaceAll("");
        adapted = ALPHA_TEST_UNIFORM.matcher(adapted).replaceAll("");
        if (!ALPHA_TEST_DEFINE.matcher(adapted).find()) {
            adapted = "#define alphaTestRef 0.1\n" + adapted;
        }
        return vertexOutputs == null
                ? TextureColorDistanceShaderAdapter.adaptFragment(adapted, programName, true, true)
                : TextureColorDistanceShaderAdapter.adaptFragment(adapted, programName, vertexOutputs,
                        shadowPassAvailable, lightmapSamplerAvailable);
    }
}
