package dev.veyra.shader;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Expands comma-separated shader-pack interface declarations for VulkanMod's GLSL parser. */
final class PostVertexInterfaceDeclarations {
    private static final Pattern MULTI_DECLARATION = Pattern.compile(
            "(?m)^([\\t ]*(?:(?:layout\\s*\\([^\\r\\n)]*\\)\\s*)?)"
                    + "(?:(?:flat|smooth|noperspective|centroid|sample)\\s+)*"
                    + "(?:in|out|varying)\\s+[A-Za-z_][A-Za-z0-9_]*[\\t ]+)"
                    + "([A-Za-z_][A-Za-z0-9_]*(?:[\\t ]*,[\\t ]*[A-Za-z_][A-Za-z0-9_]*)+)"
                    + "[\\t ]*;[\\t ]*$");
    private static final Pattern EXPLICIT_LOCATION = Pattern.compile("layout\\s*\\(\\s*location\\s*=\\s*(\\d+)");

    private PostVertexInterfaceDeclarations() { }

    static String expand(String source) {
        Matcher matcher = MULTI_DECLARATION.matcher(source);
        StringBuilder result = new StringBuilder(source.length());
        while (matcher.find()) {
            String prefix = matcher.group(1);
            String[] names = matcher.group(2).split(",");
            Matcher locationMatcher = EXPLICIT_LOCATION.matcher(prefix);
            int location = locationMatcher.find() ? Integer.parseInt(locationMatcher.group(1)) : -1;
            StringBuilder declarations = new StringBuilder();
            for (int index = 0; index < names.length; index++) {
                if (index > 0) declarations.append('\n');
                String declarationPrefix = prefix;
                if (location >= 0) {
                    declarationPrefix = locationMatcher.replaceFirst(Matcher.quoteReplacement(
                            "layout(location = " + (location + index)));
                }
                declarations.append(declarationPrefix).append(names[index].strip()).append(';');
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(declarations.toString()));
        }
        matcher.appendTail(result);
        return result.toString();
    }
}
