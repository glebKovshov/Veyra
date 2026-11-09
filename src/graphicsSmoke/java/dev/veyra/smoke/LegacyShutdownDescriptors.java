package dev.veyra.smoke;

import java.nio.LongBuffer;

/** Copies old native descriptor handles without changing their buffer position. */
public final class LegacyShutdownDescriptors {
    private LegacyShutdownDescriptors() { }

    public static long[] copy(LongBuffer source) {
        if (source == null) return null;
        long[] result = new long[source.remaining()];
        source.duplicate().get(result);
        return result;
    }
}
