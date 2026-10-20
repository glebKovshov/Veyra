package dev.veyra.shader;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Iris {@code block.properties} state and tag mappings used by block and block-entity IDs. */
public final class BlockIdMap {
    public static final BlockIdMap EMPTY = new BlockIdMap(List.of());
    public static final int UNMAPPED = -1;
    private static final Pattern ENTRY = Pattern.compile("block\\.(-?\\d+)\\s*[=:]\\s*(.*)");
    private static final Pattern IDENTIFIER = Pattern.compile("[a-z0-9_.-]+(?::[a-z0-9_./-]+)?");

    private static volatile BlockIdMap active = EMPTY;

    private final List<Selector> selectors;

    private BlockIdMap(List<Selector> selectors) {
        this.selectors = List.copyOf(selectors);
    }

    public static BlockIdMap active() {
        return active;
    }

    public static void activate(BlockIdMap map) {
        active = map == null ? EMPTY : map;
    }

    /** Parses preprocessed properties in source order; the first matching state wins, as in Iris. */
    public static BlockIdMap parse(String properties) {
        Map<String, String> entries = new LinkedHashMap<>();
        StringBuilder continued = new StringBuilder();
        for (String raw : (properties == null ? "" : properties).split("\\R", -1)) {
            String line = raw.strip();
            if (line.endsWith("\\")) {
                continued.append(line, 0, line.length() - 1).append(' ');
                continue;
            }
            continued.append(line);
            String entry = continued.toString().strip();
            continued.setLength(0);
            if (entry.isEmpty() || entry.startsWith("#") || entry.startsWith("!")) continue;
            Matcher matcher = ENTRY.matcher(entry);
            if (matcher.matches()) entries.put(matcher.group(1), matcher.group(2));
        }

        List<Selector> selectors = new ArrayList<>();
        for (Map.Entry<String, String> entry : entries.entrySet()) {
            int id;
            try {
                id = Integer.parseInt(entry.getKey());
            } catch (NumberFormatException exception) {
                continue;
            }
            for (String token : entry.getValue().trim().split("\\s+")) {
                Selector selector = Selector.parse(id, token);
                if (selector != null) selectors.add(selector);
            }
        }
        return selectors.isEmpty() ? EMPTY : new BlockIdMap(selectors);
    }

    /** Resolves a Minecraft block state against ordered direct-block and tag selectors. */
    public int id(BlockState state) {
        if (state == null || selectors.isEmpty()) return UNMAPPED;
        String blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        Map<String, String> stateProperties = new LinkedHashMap<>();
        for (Property<?> property : state.getValues().keySet()) {
            stateProperties.put(property.getName(), propertyValue(state, property));
        }
        for (Selector selector : selectors) {
            if (!selector.matches(blockId, stateProperties)) continue;
            if (selector.tagId() == null || state.is(TagKey.create(
                    Registries.BLOCK, Identifier.parse(selector.tagId())))) {
                return selector.id();
            }
        }
        return UNMAPPED;
    }

    /** Pure lookup helper for selector tests and diagnostics. */
    public int id(String namespacedBlock, Map<String, String> stateProperties) {
        return id(namespacedBlock, stateProperties, Set.of());
    }

    /** Pure lookup helper including the namespaced tags held by the candidate block. */
    public int id(String namespacedBlock, Map<String, String> stateProperties, Set<String> tags) {
        String normalized = normalize(namespacedBlock);
        for (Selector selector : selectors) {
            if (!selector.matches(normalized, stateProperties)) continue;
            if (selector.tagId() == null || tags.contains(selector.tagId())) return selector.id();
        }
        return UNMAPPED;
    }

    public Set<Integer> ids() {
        Set<Integer> ids = new LinkedHashSet<>();
        for (Selector selector : selectors) ids.add(selector.id());
        return Set.copyOf(ids);
    }

    public int size() {
        return selectors.size();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static String propertyValue(BlockState state, Property property) {
        return property.getName(state.getValue(property));
    }

    private static String normalize(String id) {
        return id.indexOf(':') >= 0 ? id : "minecraft:" + id;
    }

    private record Selector(int id, String blockId, String tagId, Map<String, String> predicates) {
        private static Selector parse(int id, String token) {
            if (token == null || token.isBlank()) return null;
            String[] parts = token.split(":", -1);
            boolean tag = parts[0].startsWith("%");
            if (tag) parts[0] = parts[0].substring(1);
            int identifierParts = parts.length >= 2 && !parts[1].contains("=") ? 2 : 1;
            String namespace;
            String path;
            int predicatesStart;
            if (identifierParts == 2) {
                namespace = parts[0];
                path = parts[1];
                predicatesStart = 2;
            } else {
                namespace = "minecraft";
                path = parts[0];
                predicatesStart = 1;
            }
            String namespacedId = namespace + ':' + path;
            if (!IDENTIFIER.matcher(namespacedId).matches()) return null;
            Map<String, String> predicates = new LinkedHashMap<>();
            for (int index = predicatesStart; index < parts.length; index++) {
                String predicate = parts[index];
                int equals = predicate.indexOf('=');
                if (equals <= 0 || equals == predicate.length() - 1) continue;
                String key = predicate.substring(0, equals);
                String value = predicate.substring(equals + 1);
                if (IDENTIFIER.matcher(key).matches() && IDENTIFIER.matcher(value).matches()) {
                    predicates.put(key, value);
                }
            }
            return new Selector(id, tag ? null : namespacedId, tag ? namespacedId : null,
                    Map.copyOf(predicates));
        }

        private boolean matches(String candidateBlock, Map<String, String> candidateProperties) {
            if (blockId != null && !blockId.equals(candidateBlock)) return false;
            for (Map.Entry<String, String> predicate : predicates.entrySet()) {
                if (!predicate.getValue().equals(candidateProperties.get(predicate.getKey()))) return false;
            }
            return true;
        }
    }
}
