package dev.veyra.vulkan;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * SPIR-V for identical GLSL. Shader-pack selection builds many pipeline variants (entity render types,
 * hand passes, validation) whose final sources often coincide byte for byte; shaderc output depends only on
 * the source, the stage and VulkanMod's fixed compiler options, so repeats reuse the first compilation.
 */
public final class VeyraSpirvCache {
    private static final int MAX_ENTRIES = 512;
    private static final long MAX_BYTES = 64L * 1024 * 1024;

    private record Key(String source, int kind) { }

    private static final Map<Key, byte[]> ENTRIES = Collections.synchronizedMap(new LinkedHashMap<>(MAX_ENTRIES, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Key, byte[]> eldest) {
            if (size() > MAX_ENTRIES || bytes > MAX_BYTES) {
                bytes -= eldest.getValue().length;
                return true;
            }
            return false;
        }
    });
    private static long bytes;
    private static int hits;
    private static int misses;

    private VeyraSpirvCache() { }

    /** A direct copy of cached SPIR-V that the caller owns, or null. */
    public static ByteBuffer get(CharSequence source, int kind) {
        byte[] code = ENTRIES.get(new Key(source.toString(), kind));
        if (code == null) {
            misses++;
            return null;
        }
        hits++;
        return ByteBuffer.allocateDirect(code.length).put(code).flip();
    }

    public static void put(CharSequence source, int kind, ByteBuffer bytecode) {
        if (bytecode == null) return;
        byte[] code = new byte[bytecode.remaining()];
        bytecode.duplicate().get(code);
        synchronized (ENTRIES) {
            byte[] previous = ENTRIES.put(new Key(source.toString(), kind), code);
            bytes += code.length - (previous == null ? 0 : previous.length);
        }
    }

    /** Hits and misses since the previous call, for load diagnostics. */
    public static String takeStatistics() {
        String statistics = hits + " reused / " + misses + " compiled";
        hits = 0;
        misses = 0;
        return statistics;
    }
}
