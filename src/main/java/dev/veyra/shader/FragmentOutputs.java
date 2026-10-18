package dev.veyra.shader;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Fragment output locations are distinct from shaderpack colortex indices. */
public record FragmentOutputs(List<Integer> targets, Map<Integer, String> variables) {
    private static final Pattern TARGETS = Pattern.compile("/\\*\\s*RENDERTARGETS\\s*:\\s*([0-9,\\s]+)\\s*\\*/");
    private static final Pattern LEGACY_TARGETS = Pattern.compile("/\\*\\s*DRAWBUFFERS\\s*:\\s*([0-9]+)\\s*\\*/");
    private static final Pattern OUTPUT = Pattern.compile("(?m)^[\\t ]*(?:layout\\s*\\(\\s*location\\s*=\\s*(\\d+)\\s*\\)\\s*)?out\\s+vec4\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*;[\\t ]*");
    private static final Pattern DATA = Pattern.compile("\\bgl_FragData\\s*\\[\\s*(\\d+)\\s*]");

    public FragmentOutputs {
        targets = List.copyOf(targets);
        variables = Map.copyOf(variables);
    }

    public static FragmentOutputs parse(String source) throws IOException {
        List<Integer> targets = new ArrayList<>();
        Matcher directive = TARGETS.matcher(source.replaceAll("(?m)//[^\r\n]*", ""));
        if (directive.find()) {
            for (String value : directive.group(1).split(",", -1)) targets.add(number(value.strip(), "render target"));
        } else {
            directive = LEGACY_TARGETS.matcher(source.replaceAll("(?m)//[^\r\n]*", ""));
            if (directive.find()) for (char value : directive.group(1).toCharArray()) targets.add(value - '0');
        }
        if (targets.size() > 8 || targets.stream().anyMatch(index -> index > 15)
                || targets.stream().distinct().count() != targets.size()) {
            throw new IOException("Geometry draw buffers require at most 8 distinct colortex0..15 targets.");
        }
        String active = ShaderSource.maskBlockAndLineComments(source);
        Map<Integer, String> variables = new LinkedHashMap<>();
        Matcher output = OUTPUT.matcher(active);
        while (output.find()) {
            String variable = output.group(2);
            int location = output.group(1) != null ? number(output.group(1), "output location")
                    : variable.matches("outColor\\d+") ? number(variable.substring(8), "output location") : 0;
            if (location > 7 || variables.putIfAbsent(location, variable) != null) {
                throw new IOException("Duplicate or unsupported geometry output location " + location + ".");
            }
        }
        Matcher data = DATA.matcher(active);
        while (data.find()) {
            int location = number(data.group(1), "gl_FragData index");
            if (location > 7) throw new IOException("gl_FragData indices must be 0..7.");
            String previous = variables.putIfAbsent(location, "veyraFragData" + location);
            if (previous != null && !previous.equals("veyraFragData" + location))
                throw new IOException("Cannot mix explicit outputs with gl_FragData at location " + location + ".");
        }
        String withoutData = DATA.matcher(active).replaceAll("");
        if (Pattern.compile("\\bgl_FragData\\b").matcher(withoutData).find())
            throw new IOException("Geometry gl_FragData indices must be constant integers.");
        if (Pattern.compile("\\bgl_FragColor\\b").matcher(active).find() || variables.isEmpty())
            variables.putIfAbsent(0, "veyraFragColor");
        int locations = variables.keySet().stream().mapToInt(Integer::intValue).max().orElse(0) + 1;
        if (targets.isEmpty()) for (int i = 0; i < locations; i++) targets.add(i);
        if (locations > targets.size()) throw new IOException("A geometry output location is missing its draw-buffer mapping.");
        return new FragmentOutputs(targets, variables);
    }

    private static int number(String value, String kind) throws IOException {
        try { return Integer.parseInt(value); }
        catch (NumberFormatException exception) { throw new IOException("Invalid " + kind + ": " + value, exception); }
    }

    public String declarations() {
        StringBuilder result = new StringBuilder();
        variables.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> result
                .append("layout(location = ").append(entry.getKey()).append(") out vec4 ")
                .append(entry.getValue()).append(";\n"));
        return result.toString();
    }

    public String rewriteLegacy(String source) {
        Matcher matcher = DATA.matcher(ShaderSource.maskBlockAndLineComments(source));
        StringBuilder rewritten = new StringBuilder();
        int end = 0;
        while (matcher.find()) {
            rewritten.append(source, end, matcher.start()).append(variables.get(Integer.parseInt(matcher.group(1))));
            end = matcher.end();
        }
        return rewritten.append(source.substring(end)).toString();
    }

    public boolean sceneOnly() { return targets.equals(List.of(0)); }
    public boolean writes(int location) { return variables.containsKey(location); }
}
