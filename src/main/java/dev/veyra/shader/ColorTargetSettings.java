package dev.veyra.shader;

/** Clear policy for one Iris-compatible colortex buffer. Colors use Minecraft's packed ARGB layout. */
record ColorTargetSettings(boolean clearEachFrame, int clearColor) {
    static ColorTargetSettings defaults(int index) {
        return new ColorTargetSettings(true, index == 1 ? 0xFFFFFFFF : 0x00000000);
    }
}
