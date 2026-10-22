package dev.veyra.shader;

import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Color-buffer samplers used by Iris post-processing vertex stages. */
final class PostVertexColorSamplers {
    private static final Pattern DECLARATION = Pattern.compile(
            "(?m)^[\\t ]*uniform\\s+(sampler\\w+)\\s+(colortex\\d+)\\s*;[^\\r\\n]*(?:\\r?\\n|$)");
    private static final Pattern REFERENCE = Pattern.compile("\\bcolortex(\\d+)\\b");

    private PostVertexColorSamplers() { }

    static Set<Integer> read(String source, String programName) {
        String active = UnusedSamplerDeclarations.removeUnused(source);
        String uncommented = ShaderSource.maskBlockAndLineComments(active);
        Set<String> declarations = new TreeSet<>();
        Matcher declaration = DECLARATION.matcher(uncommented);
        while (declaration.find()) {
            String samplerType = declaration.group(1);
            String name = declaration.group(2);
            if (!samplerType.equals("sampler2D")) {
                throw new IllegalArgumentException(programName + ".vsh " + name + " must use sampler2D.");
            }
            if (!declarations.add(name)) {
                throw new IllegalArgumentException(programName + ".vsh declares " + name + " more than once.");
            }
        }

        Set<Integer> indices = new TreeSet<>();
        Matcher reference = REFERENCE.matcher(uncommented);
        while (reference.find()) {
            int index;
            try {
                index = Integer.parseInt(reference.group(1));
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException(programName + ".vsh has an invalid color sampler.", exception);
            }
            if (index > 15) {
                throw new IllegalArgumentException(programName
                        + ".vsh uses colortex" + index + "; Veyra supports colortex0 through colortex15.");
            }
            indices.add(index);
        }
        return Set.copyOf(indices);
    }

    static String stripDeclarations(String source) {
        return DECLARATION.matcher(source).replaceAll("");
    }

    static String declarations(Set<Integer> indices) {
        StringBuilder source = new StringBuilder();
        if (indices.contains(0)) {
            source.append("uniform sampler2D InSampler;\n")
                    .append("#define colortex0 InSampler\n")
                    .append("#define Sampler0 InSampler\n");
        }
        for (int index : indices.stream().filter(value -> value > 0).sorted().toList()) {
            String sampler = "VeyraColortex" + index + "Sampler";
            source.append("uniform sampler2D ").append(sampler).append(";\n")
                    .append("#define colortex").append(index).append(' ').append(sampler).append('\n');
        }
        return source.toString();
    }
}
