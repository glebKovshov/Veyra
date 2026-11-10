package dev.veyra.compat;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Stand-in for the shader define set Minecraft added in 1.21.2: valued defines plus flags. */
public record ShaderDefines(Map<String, String> values, Set<String> flags) {
    public static final ShaderDefines EMPTY = new ShaderDefines(Map.of(), Set.of());

    public ShaderDefines {
        values = Map.copyOf(values);
        flags = Set.copyOf(flags);
    }

    public static Builder builder() { return new Builder(); }

    public ShaderDefines withOverrides(ShaderDefines overrides) {
        Map<String, String> mergedValues = new LinkedHashMap<>(values);
        mergedValues.putAll(overrides.values);
        Set<String> mergedFlags = new LinkedHashSet<>(flags);
        mergedFlags.addAll(overrides.flags);
        return new ShaderDefines(mergedValues, mergedFlags);
    }

    public boolean isEmpty() { return values.isEmpty() && flags.isEmpty(); }

    public static final class Builder {
        private final Map<String, String> values = new LinkedHashMap<>();
        private final Set<String> flags = new LinkedHashSet<>();

        private Builder() { }

        public Builder define(String flag) { flags.add(flag); return this; }
        public Builder define(String name, String value) { values.put(name, value); return this; }
        public Builder define(String name, int value) { return define(name, Integer.toString(value)); }
        public Builder define(String name, float value) { return define(name, Float.toString(value)); }

        public ShaderDefines build() { return new ShaderDefines(values, flags); }
    }
}
