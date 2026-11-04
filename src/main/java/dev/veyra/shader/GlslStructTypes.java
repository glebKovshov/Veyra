package dev.veyra.shader;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Struct definitions of a preprocessed GLSL stage and the interface locations each occupies. GLSL lets a
 * stage pass a struct between vertex and fragment stages (Photon sends {@code OverworldFogParameters}); a
 * struct variable takes consecutive locations, one per vector member and one per matrix column.
 */
final class GlslStructTypes {
    private static final Pattern STRUCT = Pattern.compile("\\bstruct\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*\\{([^{}]*)}");
    private static final Pattern MEMBER = Pattern.compile(
            "(?:\\b(?:highp|mediump|lowp|flat|smooth|noperspective)\\s+)*([A-Za-z_][A-Za-z0-9_]*)\\s+"
                    + "([A-Za-z_][A-Za-z0-9_]*(?:\\s*\\[\\s*\\d+\\s*])?(?:\\s*,\\s*[A-Za-z_][A-Za-z0-9_]*(?:\\s*\\[\\s*\\d+\\s*])?)*)\\s*;");
    private static final Pattern ARRAY = Pattern.compile("\\[\\s*(\\d+)\\s*]");

    private final Map<String, Integer> spans;
    private final Map<String, Boolean> integerMembers;

    private GlslStructTypes(Map<String, Integer> spans, Map<String, Boolean> integerMembers) {
        this.spans = spans;
        this.integerMembers = integerMembers;
    }

    static GlslStructTypes parse(String source) {
        Map<String, Integer> spans = new LinkedHashMap<>();
        Map<String, Boolean> integers = new LinkedHashMap<>();
        Matcher struct = STRUCT.matcher(ShaderSource.maskBlockAndLineComments(source));
        while (struct.find()) {
            int span = 0;
            boolean integer = false;
            boolean resolved = true;
            Matcher member = MEMBER.matcher(struct.group(2));
            int cursor = 0;
            while (member.find()) {
                if (!struct.group(2).substring(cursor, member.start()).isBlank()) {
                    resolved = false;
                    break;
                }
                cursor = member.end();
                String type = member.group(1);
                int typeSpan = typeSpan(type, spans);
                if (typeSpan <= 0) {
                    resolved = false;
                    break;
                }
                integer |= isIntegerType(type) || integers.getOrDefault(type, false);
                for (String declarator : member.group(2).split(",")) {
                    Matcher array = ARRAY.matcher(declarator);
                    long count;
                    try {
                        count = array.find() ? Long.parseLong(array.group(1)) : 1;
                    } catch (NumberFormatException invalid) {
                        resolved = false;
                        break;
                    }
                    // No supported interface can exceed 64 locations. Bound arithmetic before narrowing.
                    if (count < 1 || count > 64 || span + typeSpan * count > 64) {
                        resolved = false;
                        break;
                    }
                    span += (int) (typeSpan * count);
                }
            }
            if (resolved && span > 0 && struct.group(2).substring(cursor).isBlank()) {
                spans.put(struct.group(1), span);
                integers.put(struct.group(1), integer);
            }
        }
        return new GlslStructTypes(Map.copyOf(spans), Map.copyOf(integers));
    }

    boolean contains(String type) {
        return spans.containsKey(type);
    }

    /** Interface locations used by one variable of this struct, or 0 when it is not a known struct. */
    int locationSpan(String type) {
        return spans.getOrDefault(type, 0);
    }

    /** Whether the struct holds integer members, which require flat interpolation across stages. */
    boolean hasIntegerMembers(String type) {
        return integerMembers.getOrDefault(type, false);
    }

    private static int typeSpan(String type, Map<String, Integer> structs) {
        return switch (type) {
            case "float", "vec2", "vec3", "vec4", "int", "ivec2", "ivec3", "ivec4",
                    "uint", "uvec2", "uvec3", "uvec4", "bool", "bvec2", "bvec3", "bvec4" -> 1;
            case "mat2" -> 2;
            case "mat3" -> 3;
            case "mat4" -> 4;
            default -> structs.getOrDefault(type, 0);
        };
    }

    private static boolean isIntegerType(String type) {
        return type.equals("int") || type.startsWith("ivec") || type.equals("uint") || type.startsWith("uvec")
                || type.equals("bool") || type.startsWith("bvec");
    }
}
