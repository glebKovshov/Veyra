package dev.veyra.shader;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Iris/OptiFine {@code item.properties}: {@code item.<id>=[namespace:]path ...} item ID mappings. */
public final class ItemIdMap {
    public static final ItemIdMap EMPTY = new ItemIdMap(Map.of());
    /** Iris reports -1 for items without an item.properties mapping. */
    public static final int UNMAPPED = -1;
    private static final Pattern ENTRY = Pattern.compile("item\\.(-?\\d+)\\s*[=:]\\s*(.*)", Pattern.DOTALL);
    private static final Pattern ITEM = Pattern.compile("[a-z0-9_.-]+(?::[a-z0-9_./-]+)?");
    private static volatile ItemIdMap active = EMPTY;

    private final Map<String, Integer> ids;

    private ItemIdMap(Map<String, Integer> ids) {
        this.ids = ids;
    }

    public static ItemIdMap active() {
        return active;
    }

    public static void activate(ItemIdMap map) {
        active = map == null ? EMPTY : map;
    }

    /** Parses preprocessed item.properties text. The first id assigned to an item is kept. */
    public static ItemIdMap parse(String properties) {
        Map<String, Integer> ids = new HashMap<>();
        StringBuilder current = new StringBuilder();
        for (String raw : (properties == null ? "" : properties).split("\\r?\\n", -1)) {
            String line = raw.strip();
            if (line.endsWith("\\")) {
                current.append(line, 0, line.length() - 1).append(' ');
                continue;
            }
            current.append(line);
            String entry = current.toString().strip();
            current.setLength(0);
            Matcher matcher = ENTRY.matcher(entry);
            if (entry.startsWith("#") || !matcher.matches()) continue;
            int id;
            try {
                id = Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException exception) {
                continue;
            }
            for (String token : matcher.group(2).trim().split("\\s+")) {
                if (!ITEM.matcher(token).matches()) continue;
                ids.putIfAbsent(token.contains(":") ? token : "minecraft:" + token, id);
            }
        }
        return new ItemIdMap(Map.copyOf(ids));
    }

    /** Looks up a namespaced item id such as {@code minecraft:torch}. */
    public int id(String namespacedItem) {
        return ids.getOrDefault(namespacedItem, UNMAPPED);
    }

    public int size() {
        return ids.size();
    }
}
