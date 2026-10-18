package dev.veyra.shader;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Discovers the shaderpack option syntax; does not modify files inside the pack. */
public final class ShaderPackOptions {
    private static final Pattern DEFINE = Pattern.compile(
            "^([\\t ]*)(//[\\t ]*)?#[\\t ]*define[\\t ]+([A-Za-z_][A-Za-z0-9_]*)(?:[\\t ]+([^/\\r\\n]*?))?[\\t ]*(?://(.*))?$");
    private static final Pattern CONST = Pattern.compile(
            "^([\\t ]*)const[\\t ]+(bool|int|float)[\\t ]+([A-Za-z_][A-Za-z0-9_]*)[\\t ]*=[\\t ]*([^;]+);[\\t ]*(?://(.*))?$");
    private static final Pattern VALUES = Pattern.compile("\\[([^]\\r\\n]+)]");
    private static final Pattern BOOL_REFERENCE = Pattern.compile("(?m)^[\\t ]*#[\\t ]*ifn?def[\\t ]+([A-Za-z_][A-Za-z0-9_]*)");
    private static final Pattern SAFE_VALUE = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*|[+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:[eE][+-]?\\d+)?[fFuU]?");

    public record Option(String name, String defaultValue, List<String> values, boolean booleanDefine,
                         boolean constant, String description) {
        public Option { values = List.copyOf(values); }
        public String validate(String value) throws IOException {
            if (!values.contains(value)) throw new IOException("Invalid value '" + value + "' for " + name + ". Allowed: " + values);
            return value;
        }
    }

    private final Map<String, Option> options;
    private final Properties properties;
    private final Map<String, Map<String, String>> profiles;
    private final Map<String, String> labels;
    private final List<String> diagnostics;

    private ShaderPackOptions(Map<String, Option> options, Properties properties, Map<String, Map<String, String>> profiles,
                              Map<String, String> labels, List<String> diagnostics) {
        this.options = Collections.unmodifiableMap(new LinkedHashMap<>(options));
        this.properties = properties;
        this.profiles = Collections.unmodifiableMap(new LinkedHashMap<>(profiles));
        this.labels = Map.copyOf(labels);
        this.diagnostics = List.copyOf(diagnostics);
    }

    public static ShaderPackOptions parse(Map<String, String> sources, String propertySource, String language) throws IOException {
        Set<String> referenced = new LinkedHashSet<>();
        for (String source : sources.values()) {
            Matcher matcher = BOOL_REFERENCE.matcher(ShaderSource.maskBlockAndLineComments(source));
            while (matcher.find()) referenced.add(matcher.group(1));
        }
        Map<String, Option> found = new LinkedHashMap<>();
        Set<String> conflicts = new LinkedHashSet<>();
        List<String> diagnostics = new ArrayList<>();
        for (String source : sources.values()) {
            for (String line : ShaderSource.maskBlockComments(source).split("\\R")) {
                Option option = parseLine(line, referenced);
                if (option == null || conflicts.contains(option.name())) continue;
                Option previous = found.putIfAbsent(option.name(), option);
                if (previous != null && (!previous.defaultValue().equals(option.defaultValue())
                        || previous.booleanDefine() != option.booleanDefine() || previous.constant() != option.constant())) {
                    found.remove(option.name()); conflicts.add(option.name());
                    diagnostics.add("Conflicting declarations for " + option.name());
                }
            }
        }
        Properties properties = loadProperties(propertySource);
        for (String property : properties.stringPropertyNames()) {
            if (!property.startsWith("profile.")) continue;
            Matcher program = Pattern.compile("!?program\\.(?:world(?:-1|0|1)/)?[A-Za-z0-9_]+").matcher(properties.getProperty(property));
            while (program.find()) {
                String name = program.group().replace("!", "");
                found.putIfAbsent(name, new Option(name, "true", List.of("false", "true"), true, false, "Profile program toggle"));
            }
        }
        Map<String, Map<String, String>> profiles = new LinkedHashMap<>();
        for (String key : properties.stringPropertyNames().stream().sorted().toList()) {
            if (key.startsWith("profile.")) resolveProfile(key.substring(8), properties, found, profiles, new LinkedHashSet<>(), diagnostics);
        }
        Properties translations = loadProperties(language);
        Map<String, String> labels = new LinkedHashMap<>();
        translations.forEach((key, value) -> labels.put(key.toString(), value.toString()));
        return new ShaderPackOptions(found, properties, profiles, labels, diagnostics);
    }

    private static Option parseLine(String line, Set<String> referenced) {
        Matcher define = DEFINE.matcher(line);
        if (define.matches()) {
            String name = define.group(3), raw = define.group(4) == null ? "" : define.group(4).strip();
            String comment = define.group(5) == null ? "" : define.group(5).strip();
            if (raw.isEmpty() && referenced.contains(name)) {
                return new Option(name, define.group(2) == null ? "true" : "false", List.of("false", "true"), true, false, comment);
            }
            if (define.group(2) != null || !SAFE_VALUE.matcher(raw).matches()) return null;
            return valued(name, raw, comment, false);
        }
        Matcher constant = CONST.matcher(line);
        if (constant.matches()) return valued(constant.group(3), constant.group(4).strip(),
                constant.group(5) == null ? "" : constant.group(5).strip(), true);
        return null;
    }

    private static Option valued(String name, String value, String comment, boolean constant) {
        Matcher list = VALUES.matcher(comment);
        if (!SAFE_VALUE.matcher(value).matches() || !list.find()) return null;
        Set<String> allowed = new LinkedHashSet<>();
        for (String candidate : list.group(1).strip().split("\\s+")) {
            if (!SAFE_VALUE.matcher(candidate).matches()) return null;
            allowed.add(candidate);
        }
        allowed.add(value); // Some packs deliberately use a default outside the enumerated list.
        return new Option(name, value, List.copyOf(allowed), false, constant, VALUES.matcher(comment).replaceAll("").strip());
    }

    private static Map<String, String> resolveProfile(String name, Properties properties, Map<String, Option> options,
            Map<String, Map<String, String>> profiles, Set<String> stack, List<String> diagnostics) {
        if (profiles.containsKey(name)) return profiles.get(name);
        if (!stack.add(name) || stack.size() > 32) { diagnostics.add("Profile cycle/depth limit at " + name); return Map.of(); }
        Map<String, String> values = new LinkedHashMap<>();
        try {
            String definition = properties.getProperty("profile." + name);
            if (definition == null) { diagnostics.add("Unknown profile " + name); return Map.of(); }
            for (String item : definition.strip().split("\\s+")) {
                if (item.startsWith("profile.")) {
                    values.putAll(resolveProfile(item.substring(8), properties, options, profiles, stack, diagnostics));
                    continue;
                }
                int equal = item.indexOf('=');
                String key = equal >= 0 ? item.substring(0, equal) : item.startsWith("!") ? item.substring(1) : item;
                String value = equal >= 0 ? item.substring(equal + 1) : item.startsWith("!") ? "false" : "true";
                Option option = options.get(key);
                if (option == null || !option.values().contains(value)) {
                    diagnostics.add("Unsupported profile entry " + name + ": " + item); continue;
                }
                values.put(key, value);
            }
            Map<String, String> result = Map.copyOf(values);
            profiles.put(name, result);
            return result;
        } finally { stack.remove(name); }
    }

    static Properties loadProperties(String source) throws IOException {
        Properties properties = new Properties();
        try { properties.load(new StringReader(source)); }
        catch (IllegalArgumentException exception) { throw new IOException("Invalid shaderpack properties.", exception); }
        return properties;
    }

    public Map<String, Option> options() { return options; }
    public Map<String, Map<String, String>> profiles() { return profiles; }
    public List<String> diagnostics() { return diagnostics; }
    public String label(String key, String fallback) { return labels.getOrDefault(key, fallback); }
    public List<String> screen(String name) {
        String key = name.isEmpty() ? "screen" : "screen." + name;
        String definition = properties.getProperty(key);
        return definition == null ? name.isEmpty() ? new ArrayList<>(options.keySet()) : List.of()
                : List.of(definition.strip().split("\\s+"));
    }
    public int columns(String screen) {
        String key = screen.isEmpty() ? "screen.columns" : "screen." + screen + ".columns";
        try { return Math.max(1, Math.min(4, Integer.parseInt(properties.getProperty(key, properties.getProperty("screen.columns", "2"))))); }
        catch (NumberFormatException exception) { return 2; }
    }
    public Set<String> sliders() { return Set.copyOf(List.of(properties.getProperty("sliders", "").strip().split("\\s+"))); }
    public Map<String, String> validate(Map<String, String> values) throws IOException {
        Map<String, String> result = new LinkedHashMap<>();
        for (var entry : values.entrySet()) {
            Option option = options.get(entry.getKey());
            if (option == null) throw new IOException("Unknown shaderpack option: " + entry.getKey());
            String value = option.validate(entry.getValue());
            if (!value.equals(option.defaultValue())) result.put(entry.getKey(), value);
        }
        return Map.copyOf(result);
    }
    public Map<String, String> effective(Map<String, String> changed) {
        Map<String, String> result = new LinkedHashMap<>();
        options.values().forEach(option -> result.put(option.name(), changed.getOrDefault(option.name(), option.defaultValue())));
        return result;
    }

    static String apply(String source, Map<String, String> values) {
        if (values.isEmpty()) return source;
        String masked = ShaderSource.maskBlockComments(source);
        String[] originals = source.split("\\n", -1), lines = masked.split("\\n", -1);
        for (int i = 0; i < lines.length; i++) {
            Matcher define = DEFINE.matcher(lines[i].replace("\r", ""));
            Matcher constant = CONST.matcher(lines[i].replace("\r", ""));
            if (define.matches() && values.containsKey(define.group(3))) {
                String value = values.get(define.group(3));
                String raw = define.group(4) == null ? "" : define.group(4).strip();
                int hash = lines[i].indexOf('#');
                if (raw.isEmpty() && (value.equals("true") || value.equals("false"))) {
                    if (value.equals("true") && define.group(2) != null)
                        originals[i] = originals[i].substring(0, define.start(2)) + originals[i].substring(hash);
                    else if (value.equals("false") && define.group(2) == null)
                        originals[i] = originals[i].substring(0, hash) + "//" + originals[i].substring(hash);
                } else if (!raw.isEmpty()) {
                    originals[i] = originals[i].substring(0, define.start(4)) + value + originals[i].substring(define.end(4));
                }
            } else if (constant.matches() && values.containsKey(constant.group(3))) {
                originals[i] = originals[i].substring(0, constant.start(4)) + values.get(constant.group(3))
                        + originals[i].substring(constant.end(4));
            }
        }
        return String.join("\n", originals);
    }
}
