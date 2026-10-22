package dev.veyra.shader;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Links Iris post-pass vertex outputs to their matching fragment inputs. */
final class PostVertexVaryings {
    private static final Pattern INTERFACE_DECLARATION = Pattern.compile(
            "(?m)^[\\t ]*(?:layout\\s*\\(\\s*location\\s*=\\s*(\\d+)\\s*\\)\\s*)?"
                    + "((?:(?:flat|smooth|noperspective|centroid|sample)\\s+)*)"
                    + "(varying|in|out)\\s+([A-Za-z_][A-Za-z0-9_]*)\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*;"
                    + "[\\t ]*(?://[^\\r\\n]*)?(?:\\r?\\n|$)");
    private static final Pattern VERTEX_INTERFACE_START = Pattern.compile(
            "(?m)^[\\t ]*(?:layout\\s*\\([^\\r\\n)]*\\)\\s*)?"
                    + "(?:(?:flat|smooth|noperspective|centroid|sample)\\s+)*(?:varying|out)\\b");
    private static final Pattern FRAGMENT_INTERFACE_START = Pattern.compile(
            "(?m)^[\\t ]*(?:layout\\s*\\([^\\r\\n)]*\\)\\s*)?"
                    + "(?:(?:flat|smooth|noperspective|centroid|sample)\\s+)*(?:varying|in)\\b");
    private static final Pattern INTERPOLATION = Pattern.compile("\\b(flat|smooth|noperspective)\\b");
    private static final Set<String> SUPPORTED_TYPES = Set.of(
            "float", "double", "int", "uint", "bool",
            "vec2", "vec3", "vec4", "dvec2", "dvec3", "dvec4",
            "ivec2", "ivec3", "ivec4", "uvec2", "uvec3", "uvec4",
            "bvec2", "bvec3", "bvec4");

    private PostVertexVaryings() { }

    record Varying(String name, String type, String qualifiers, int location) {
        String declaration(String storage) {
            String direction = storage.equals("out") ? "OUT" : "IN";
            String macro = "VEYRA_INTERNAL_POST_" + direction + "_" + location;
            String interpolation = qualifiers.equals("smooth ") ? "" : qualifiers.strip() + " ";
            return "#define " + macro + " layout(location = " + location + ") " + interpolation
                    + storage + " " + type + " " + name + "\n" + macro + ";\n";
        }
    }

    record Interface(List<Varying> vertexOutputs, List<Varying> fragmentInputs) {
        Interface {
            vertexOutputs = List.copyOf(vertexOutputs);
            fragmentInputs = List.copyOf(fragmentInputs);
        }

        String vertexDeclarations() {
            return declarations(vertexOutputs, "out");
        }

        String fragmentDeclarations() {
            return declarations(fragmentInputs, "in");
        }

        private static String declarations(List<Varying> varyings, String storage) {
            StringBuilder declarations = new StringBuilder();
            for (Varying varying : varyings) declarations.append(varying.declaration(storage));
            return declarations.toString();
        }
    }

    static Interface link(String programName, String vertexSource, String fragmentSource) throws IOException {
        List<Declaration> vertex = declarations(programName, vertexSource, true);
        List<Declaration> fragment = declarations(programName, fragmentSource, false);
        Map<String, Declaration> vertexByName = byName(programName, vertex, "vertex output");
        Map<String, Declaration> fragmentByName = byName(programName, fragment, "fragment input");

        Declaration vertexTexCoord = vertexByName.get("texCoord");
        Declaration fragmentTexCoord = fragmentByName.get("texCoord");
        validateTexCoord(programName, vertexTexCoord, "vertex output");
        validateTexCoord(programName, fragmentTexCoord, "fragment input");
        if (vertexTexCoord != null && fragmentTexCoord != null) {
            validateCompatible(programName, vertexTexCoord, fragmentTexCoord);
        }

        LinkedHashMap<String, Declaration> outputs = new LinkedHashMap<>();
        outputs.put("texCoord", texCoordDeclaration(vertexTexCoord, fragmentTexCoord));
        for (Declaration declaration : vertex) {
            if (!declaration.name().equals("texCoord")) outputs.put(declaration.name(), declaration);
        }

        for (Declaration input : fragment) {
            if (input.name().equals("texCoord")) continue;
            Declaration output = vertexByName.get(input.name());
            if (output == null) {
                throw new IOException(programName + ".fsh reads varying '" + input.name()
                        + "' without a matching post-pass vertex output.");
            }
            validateCompatible(programName, output, input);
        }

        Map<String, Integer> locations = assignLocations(programName, outputs, fragmentByName);
        List<Varying> vertexOutputs = new ArrayList<>(outputs.size());
        for (Declaration output : outputs.values()) {
            Declaration input = fragmentByName.get(output.name());
            vertexOutputs.add(toVarying(output, input, locations.get(output.name())));
        }

        LinkedHashSet<String> inputNames = new LinkedHashSet<>();
        inputNames.add("texCoord");
        for (Declaration input : fragment) inputNames.add(input.name());
        List<Varying> fragmentInputs = new ArrayList<>(inputNames.size());
        for (String name : inputNames) {
            Declaration output = outputs.get(name);
            Declaration input = fragmentByName.get(name);
            Declaration source = input != null ? input : output;
            fragmentInputs.add(toVarying(source, output, locations.get(name)));
        }
        return new Interface(vertexOutputs, fragmentInputs);
    }

    static String stripVertexDeclarations(String source) {
        return stripDeclarations(source, true);
    }

    static String stripFragmentDeclarations(String source) {
        return stripDeclarations(source, false);
    }

    private static String stripDeclarations(String source, boolean vertex) {
        String expanded = PostVertexInterfaceDeclarations.expand(source);
        Matcher matcher = INTERFACE_DECLARATION.matcher(expanded);
        StringBuffer stripped = new StringBuffer();
        while (matcher.find()) {
            String storage = matcher.group(3);
            boolean isVertexOutput = storage.equals("varying") || storage.equals("out");
            boolean isFragmentInput = storage.equals("varying") || storage.equals("in");
            if (vertex ? isVertexOutput : isFragmentInput) {
                matcher.appendReplacement(stripped, "");
            }
        }
        matcher.appendTail(stripped);
        return stripped.toString();
    }

    private static List<Declaration> declarations(String programName, String source, boolean vertex)
            throws IOException {
        if (source == null || source.isBlank()) return List.of();
        String expanded = PostVertexInterfaceDeclarations.expand(source);
        String uncommented = ShaderSource.maskBlockAndLineComments(expanded);
        Matcher matcher = INTERFACE_DECLARATION.matcher(uncommented);
        List<Declaration> declarations = new ArrayList<>();
        while (matcher.find()) {
            String storage = matcher.group(3);
            boolean matchesStage = vertex
                    ? storage.equals("varying") || storage.equals("out")
                    : storage.equals("varying") || storage.equals("in");
            if (!matchesStage) continue;
            String type = matcher.group(4);
            if (!SUPPORTED_TYPES.contains(type)) {
                throw new IOException(programName + (vertex ? ".vsh" : ".fsh")
                        + " uses unsupported varying type '" + type + "'.");
            }
            Integer location = matcher.group(1) == null ? null : Integer.parseInt(matcher.group(1));
            String name = normalizeName(matcher.group(5));
            String qualifiers = normalizeQualifiers(programName, matcher.group(2));
            declarations.add(new Declaration(name, type, qualifiers, location));
        }
        Pattern start = vertex ? VERTEX_INTERFACE_START : FRAGMENT_INTERFACE_START;
        String withoutSupportedDeclarations = INTERFACE_DECLARATION.matcher(uncommented).replaceAll("");
        if (start.matcher(withoutSupportedDeclarations).find()) {
            throw new IOException(programName + (vertex ? ".vsh" : ".fsh")
                    + " contains a post-pass varying declaration Veyra cannot link.");
        }
        return List.copyOf(declarations);
    }

    private static Map<String, Declaration> byName(String programName, List<Declaration> declarations, String stage)
            throws IOException {
        LinkedHashMap<String, Declaration> byName = new LinkedHashMap<>();
        for (Declaration declaration : declarations) {
            if (byName.putIfAbsent(declaration.name(), declaration) != null) {
                throw new IOException(programName + " declares post-pass " + stage + " '"
                        + declaration.name() + "' more than once.");
            }
        }
        return byName;
    }

    private static Map<String, Integer> assignLocations(String programName,
            LinkedHashMap<String, Declaration> outputs, Map<String, Declaration> fragmentInputs) throws IOException {
        LinkedHashMap<String, Integer> locations = new LinkedHashMap<>();
        LinkedHashMap<Integer, String> namesByLocation = new LinkedHashMap<>();
        reserveLocation(programName, "texCoord", 0, locations, namesByLocation);
        for (Declaration output : outputs.values()) {
            if (output.name().equals("texCoord")) continue;
            Declaration input = fragmentInputs.get(output.name());
            Integer outputLocation = output.location();
            Integer inputLocation = input == null ? null : input.location();
            if (outputLocation != null && inputLocation != null && !outputLocation.equals(inputLocation)) {
                throw new IOException(programName + " varying '" + output.name()
                        + "' has different explicit vertex and fragment locations.");
            }
            Integer explicit = outputLocation != null ? outputLocation : inputLocation;
            if (explicit != null) reserveLocation(programName, output.name(), explicit, locations, namesByLocation);
        }
        int next = 1;
        for (Declaration output : outputs.values()) {
            if (locations.containsKey(output.name())) continue;
            while (namesByLocation.containsKey(next)) next++;
            reserveLocation(programName, output.name(), next++, locations, namesByLocation);
        }
        return Map.copyOf(locations);
    }

    private static void reserveLocation(String programName, String name, int location,
            Map<String, Integer> locations, Map<Integer, String> namesByLocation) throws IOException {
        if (location < 0) throw new IOException(programName + " has a negative post-pass varying location.");
        String existing = namesByLocation.putIfAbsent(location, name);
        if (existing != null && !existing.equals(name)) {
            throw new IOException(programName + " assigns post-pass varyings '" + existing + "' and '"
                    + name + "' to the same location " + location + ".");
        }
        locations.put(name, location);
    }

    private static void validateTexCoord(String programName, Declaration declaration, String stage)
            throws IOException {
        if (declaration == null) return;
        if (!declaration.type().equals("vec2")) {
            throw new IOException(programName + " " + stage + " 'texCoord' must use vec2.");
        }
        if (declaration.location() != null && declaration.location() != 0) {
            throw new IOException(programName + " " + stage + " 'texCoord' must use location 0.");
        }
    }

    private static void validateCompatible(String programName, Declaration output, Declaration input)
            throws IOException {
        if (!output.type().equals(input.type())) {
            throw new IOException(programName + " varying '" + output.name() + "' uses different vertex and fragment types.");
        }
        if (!output.qualifiers().equals(input.qualifiers())) {
            throw new IOException(programName + " varying '" + output.name()
                    + "' uses different vertex and fragment interpolation qualifiers.");
        }
        if (output.location() != null && input.location() != null && !output.location().equals(input.location())) {
            throw new IOException(programName + " varying '" + output.name()
                    + "' has different explicit vertex and fragment locations.");
        }
    }

    private static Declaration texCoordDeclaration(Declaration vertex, Declaration fragment) throws IOException {
        String qualifiers = vertex != null ? vertex.qualifiers()
                : fragment != null ? fragment.qualifiers() : "smooth ";
        return new Declaration("texCoord", "vec2", qualifiers, 0);
    }

    private static Varying toVarying(Declaration declaration, Declaration other, int location) {
        String qualifiers = declaration != null ? declaration.qualifiers()
                : other != null ? other.qualifiers() : "smooth ";
        String type = declaration != null ? declaration.type() : other.type();
        String name = declaration != null ? declaration.name() : other.name();
        return new Varying(name, type, qualifiers, location);
    }

    private static String normalizeName(String name) {
        return name.equals("texcoord") ? "texCoord" : name;
    }

    private static String normalizeQualifiers(String programName, String qualifiers) throws IOException {
        Matcher matcher = INTERPOLATION.matcher(qualifiers);
        String interpolation = null;
        while (matcher.find()) {
            if (interpolation != null && !interpolation.equals(matcher.group(1))) {
                throw new IOException(programName + " declares conflicting post-pass interpolation qualifiers.");
            }
            interpolation = matcher.group(1);
        }
        String result = qualifiers.replaceAll("\\s+", " ").trim();
        if (result.isEmpty()) return "smooth ";
        if (interpolation == null) result = "smooth " + result;
        else if (!result.endsWith(" ")) result += " ";
        return result;
    }

    private record Declaration(String name, String type, String qualifiers, Integer location) { }
}
