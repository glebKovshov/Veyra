package dev.veyra.shader;

public final class LegacyStd140SizeCalculator {
    private int size;
    public int get() { return size; }
    static int aligned(int offset, int alignment) {
        if (alignment <= 0 || (alignment & (alignment - 1)) != 0) {
            throw new IllegalArgumentException("Alignment must be a positive power of two.");
        }
        return Math.addExact(offset, alignment - 1) & -alignment;
    }
    public LegacyStd140SizeCalculator align(int alignment) { size = aligned(size, alignment); return this; }
    private LegacyStd140SizeCalculator add(int alignment, int bytes) {
        align(alignment); size = Math.addExact(size, bytes); return this;
    }
    public LegacyStd140SizeCalculator putFloat() { return add(4, 4); }
    public LegacyStd140SizeCalculator putInt() { return add(4, 4); }
    public LegacyStd140SizeCalculator putVec2() { return add(8, 8); }
    public LegacyStd140SizeCalculator putIVec2() { return add(8, 8); }
    public LegacyStd140SizeCalculator putVec4() { return add(16, 16); }
    public LegacyStd140SizeCalculator putMat4f() { return add(16, 64); }
}
