package dev.veyra.shader;

/** Resolves a shader-pack compute dispatch from fixed groups or the current render extent. */
public record ComputeWorkGroupDispatch(Mode mode, int x, int y, int z, float scaleX, float scaleY) {
    public enum Mode { FIXED, RENDER_RELATIVE }

    public ComputeWorkGroupDispatch {
        if (mode == null) throw new IllegalArgumentException("A compute dispatch mode is required.");
        if (mode == Mode.FIXED && (x < 0 || y < 0 || z < 0)) {
            throw new IllegalArgumentException("Fixed compute work-group counts cannot be negative.");
        }
        if (mode == Mode.RENDER_RELATIVE
                && (!Float.isFinite(scaleX) || !Float.isFinite(scaleY) || scaleX < 0.0f || scaleY < 0.0f)) {
            throw new IllegalArgumentException("Render-relative compute scales must be finite and non-negative.");
        }
    }

    public static ComputeWorkGroupDispatch fixed(int x, int y, int z) {
        return new ComputeWorkGroupDispatch(Mode.FIXED, x, y, z, 0.0f, 0.0f);
    }

    public static ComputeWorkGroupDispatch renderRelative(float scaleX, float scaleY) {
        return new ComputeWorkGroupDispatch(Mode.RENDER_RELATIVE, 0, 0, 1, scaleX, scaleY);
    }

    public boolean isFixed() {
        return mode == Mode.FIXED;
    }

    public int[] resolve(int renderWidth, int renderHeight, int localSizeX, int localSizeY) {
        if (isFixed()) return new int[]{x, y, z};
        if (renderWidth < 0 || renderHeight < 0 || localSizeX <= 0 || localSizeY <= 0) {
            throw new IllegalArgumentException("Render extent and local work-group sizes must be non-negative/positive.");
        }
        return new int[]{
                relativeCount(renderWidth, scaleX, localSizeX),
                relativeCount(renderHeight, scaleY, localSizeY),
                1
        };
    }

    private static int relativeCount(int extent, float scale, int localSize) {
        if (extent == 0 || scale == 0.0f) return 0;
        // Iris stores workGroupsRender in a Vector2f and dispatches using float render dimensions.
        // Preserve that multiplication precision before rounding the scaled extent up to pixels.
        float scaledExtent = extent * scale;
        if (!Float.isFinite(scaledExtent)) {
            throw new IllegalArgumentException("Render-relative compute work-group count exceeds integer range.");
        }
        double scaledPixels = Math.ceil(scaledExtent);
        double count = Math.ceil(scaledPixels / localSize);
        if (count > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Render-relative compute work-group count exceeds integer range.");
        }
        return (int) count;
    }
}
