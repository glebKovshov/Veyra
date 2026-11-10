package dev.veyra.compat;

/** Stand-in for the packed-color helpers Minecraft added in 1.21.2. */
public final class ARGB {
    private ARGB() { }

    public static int alpha(int color) { return color >>> 24; }
    public static int red(int color) { return color >> 16 & 255; }
    public static int green(int color) { return color >> 8 & 255; }
    public static int blue(int color) { return color & 255; }
    public static float redFloat(int color) { return red(color) / 255.0F; }
    public static float greenFloat(int color) { return green(color) / 255.0F; }
    public static float blueFloat(int color) { return blue(color) / 255.0F; }
    public static int color(int alpha, int red, int green, int blue) { return alpha << 24 | red << 16 | green << 8 | blue; }
    public static int opaque(int color) { return color | 0xFF000000; }

    /** An opaque color from the 0-1 RGB vector colors were before 1.21.2. */
    public static int fromRgb(net.minecraft.world.phys.Vec3 rgb) {
        return color(255, Math.round((float) rgb.x * 255.0F), Math.round((float) rgb.y * 255.0F), Math.round((float) rgb.z * 255.0F));
    }
}
