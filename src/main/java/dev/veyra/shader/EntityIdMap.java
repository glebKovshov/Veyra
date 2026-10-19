package dev.veyra.shader;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Iris {@code entity.properties} mappings used by the entityId shader uniform. */
public final class EntityIdMap {
    public static final EntityIdMap EMPTY = new EntityIdMap(Map.of());
    public static final int UNMAPPED = -1;
    private static final Pattern ENTRY = Pattern.compile("entity\\.(-?\\d+)\\s*[=:]\\s*(.*)");
    private static final Pattern IDENTIFIER = Pattern.compile("[a-z0-9_.-]+(?::[a-z0-9_./-]+)?");
    private static volatile EntityIdMap active = EMPTY;

    private final Map<String, Integer> ids;

    private EntityIdMap(Map<String, Integer> ids) {
        this.ids = ids;
    }

    public static EntityIdMap active() {
        return active;
    }

    public static void activate(EntityIdMap map) {
        active = map == null ? EMPTY : map;
    }

    /** Parses preprocessed Iris properties. Later entries replace earlier duplicate identifiers. */
    public static EntityIdMap parse(String properties) {
        Map<String, Integer> ids = new LinkedHashMap<>();
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
            if (!matcher.matches()) continue;
            int id;
            try {
                id = Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException exception) {
                continue;
            }
            for (String token : matcher.group(2).trim().split("\\s+")) {
                if (!IDENTIFIER.matcher(token).matches()) continue;
                ids.put(normalize(token), id);
            }
        }
        return ids.isEmpty() ? EMPTY : new EntityIdMap(Map.copyOf(ids));
    }

    public int id(String namespacedEntity) {
        return ids.getOrDefault(normalize(namespacedEntity), UNMAPPED);
    }

    public Set<Integer> ids() {
        return Set.copyOf(new LinkedHashSet<>(ids.values()));
    }

    public int size() {
        return ids.size();
    }

    private static String normalize(String id) {
        return id.indexOf(':') >= 0 ? id : "minecraft:" + id;
    }
}
