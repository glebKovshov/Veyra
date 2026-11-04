package dev.veyra.shader;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * GL lets a fragment shader declare outputs narrower than vec4 (Photon's gbuffers_skytextured writes
 * {@code out vec3 frag_color}); GL leaves the missing target components undefined. Veyra's geometry adapters
 * route vec4 outputs only, so widen each narrow output into a vec4 output written after the pack's main, with
 * 0 for missing colour channels and 1 for alpha. The pack keeps a variable of its declared type and name.
 */
final class NarrowFragmentOutputs {
    private static final Pattern NARROW_OUTPUT = Pattern.compile(
            "(?m)^[\\t ]*(layout\\s*\\(\\s*location\\s*=\\s*\\d+\\s*\\)\\s*)?out\\s+(float|vec2|vec3)\\s+"
                    + "([A-Za-z_][A-Za-z0-9_]*)\\s*;");
    private static final Pattern MAIN = Pattern.compile("\\bvoid\\s+main\\s*\\(\\s*(?:void\\s*)?\\)");
    private static final String WIDE_PREFIX = "veyraWideOutput_";

    private NarrowFragmentOutputs() { }

    static String widen(String source) {
        String masked = ShaderSource.maskBlockAndLineComments(source);
        Matcher output = NARROW_OUTPUT.matcher(masked);
        List<String> assignments = new ArrayList<>();
        StringBuilder widened = new StringBuilder(source.length());
        int cursor = 0;
        while (output.find()) {
            String layout = output.group(1) == null ? "" : output.group(1);
            String type = output.group(2), name = output.group(3);
            widened.append(source, cursor, output.start())
                    .append(layout).append("out vec4 ").append(WIDE_PREFIX).append(name).append(";\n")
                    .append(type).append(' ').append(name).append(';');
            cursor = output.end();
            assignments.add(WIDE_PREFIX + name + " = " + switch (type) {
                case "float" -> "vec4(" + name + ", 0.0, 0.0, 1.0)";
                case "vec2" -> "vec4(" + name + ", 0.0, 1.0)";
                default -> "vec4(" + name + ", 1.0)";
            } + ";");
        }
        if (assignments.isEmpty()) return source;
        widened.append(source, cursor, source.length());

        String result = widened.toString();
        Matcher main = MAIN.matcher(ShaderSource.maskBlockAndLineComments(result));
        if (!main.find()) return source;
        int start = main.start(), end = main.end();
        if (main.find()) return source;
        return result.substring(0, start) + "void veyraNarrowOutputsMain()" + result.substring(end)
                + "\nvoid main() {\n    veyraNarrowOutputsMain();\n    " + String.join("\n    ", assignments) + "\n}\n";
    }
}
