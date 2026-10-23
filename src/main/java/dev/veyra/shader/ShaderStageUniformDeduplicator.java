package dev.veyra.shader;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Removes uniform fields repeated by the fragment stage in VulkanMod's shared stage parser. */
final class ShaderStageUniformDeduplicator {
    private static final Pattern UNIFORM_DECLARATION = Pattern.compile(
            "(?m)^[\\t ]*uniform\\s+(?:(?:lowp|mediump|highp)\\s+)?"
                    + "([A-Za-z_][A-Za-z0-9_]*)\\s+([A-Za-z_][A-Za-z0-9_]*)"
                    + "(\\s*\\[[^]\\r\\n]*\\])?\\s*;[\\t ]*(?://[^\\r\\n]*)?(?:\\r?\\n|$)");
    private static final Pattern SAMPLER_UNIFORM_DECLARATION = Pattern.compile(
            "(?m)^[\\t ]*(?:layout\\s*\\([^\\r\\n]*\\)\\s*)?uniform\\s+"
                    + "(?:(?:lowp|mediump|highp)\\s+)?(sampler\\w+)\\s+([A-Za-z_][A-Za-z0-9_]*)"
                    + "(\\s*\\[[^]\\r\\n]*\\])?\\s*;[\\t ]*(?://[^\\r\\n]*)?(?:\\r?\\n|$)");
    private ShaderStageUniformDeduplicator() { }

    static String removeFragmentDuplicates(String vertexSource, String fragmentSource, String programName)
            throws IOException {
        Map<String, String> vertexUniforms = declarations(vertexSource);
        Matcher matcher = UNIFORM_DECLARATION.matcher(ShaderSource.maskBlockAndLineComments(fragmentSource));
        StringBuilder result = new StringBuilder(fragmentSource);
        while (matcher.find()) {
            String type = matcher.group(1);
            if (isSampler(type)) continue;
            String name = matcher.group(2);
            String array = matcher.group(3) == null ? "" : matcher.group(3).replaceAll("\\s+", "");
            String vertexType = vertexUniforms.get(name);
            if (vertexType == null) continue;
            String fragmentType = type + array;
            if (!vertexType.equals(fragmentType)) {
                throw new IOException(programName + " declares uniform '" + name + "' with incompatible types in its vertex and fragment stages.");
            }
            for (int index = matcher.start(); index < matcher.end(); index++) {
                char character = result.charAt(index);
                if (character != '\n' && character != '\r') result.setCharAt(index, ' ');
            }
        }
        return result.toString();
    }

    /** Removes sampler declarations repeated while composing one vertex stage from native and pack code. */
    static String removeDuplicateSamplers(String nativeSource, String bridgeSource, String programName)
            throws IOException {
        Map<String, String> nativeSamplers = samplerDeclarations(nativeSource);
        Matcher matcher = SAMPLER_UNIFORM_DECLARATION.matcher(ShaderSource.maskBlockAndLineComments(bridgeSource));
        StringBuilder result = new StringBuilder(bridgeSource);
        while (matcher.find()) {
            String name = matcher.group(2);
            String array = matcher.group(3) == null ? "" : matcher.group(3).replaceAll("\\s+", "");
            String nativeType = nativeSamplers.get(name);
            if (nativeType == null) continue;
            String bridgeType = matcher.group(1) + array;
            if (!nativeType.equals(bridgeType)) {
                throw new IOException(programName + " declares sampler '" + name
                        + "' with incompatible types in its native and shader-pack vertex code.");
            }
            for (int index = matcher.start(); index < matcher.end(); index++) {
                char character = result.charAt(index);
                if (character != '\n' && character != '\r') result.setCharAt(index, ' ');
            }
        }
        return result.toString();
    }

    private static Map<String, String> declarations(String source) {
        Map<String, String> uniforms = new LinkedHashMap<>();
        Matcher matcher = UNIFORM_DECLARATION.matcher(ShaderSource.maskBlockAndLineComments(source));
        while (matcher.find()) {
            String type = matcher.group(1);
            if (isSampler(type)) continue;
            String array = matcher.group(3) == null ? "" : matcher.group(3).replaceAll("\\s+", "");
            uniforms.putIfAbsent(matcher.group(2), type + array);
        }
        return uniforms;
    }

    private static Map<String, String> samplerDeclarations(String source) {
        Map<String, String> samplers = new LinkedHashMap<>();
        Matcher matcher = SAMPLER_UNIFORM_DECLARATION.matcher(ShaderSource.maskBlockAndLineComments(source));
        while (matcher.find()) {
            String array = matcher.group(3) == null ? "" : matcher.group(3).replaceAll("\\s+", "");
            samplers.putIfAbsent(matcher.group(2), matcher.group(1) + array);
        }
        return samplers;
    }

    private static boolean isSampler(String type) {
        return type.contains("sampler") || type.contains("image");
    }

}
