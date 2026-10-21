package dev.veyra.shader;

import net.minecraft.world.level.block.state.BlockState;

/** Per chunk-build-thread values appended to VulkanMod's compressed terrain vertices. */
public final class TerrainMaterialContext {
    private static final ThreadLocal<Attributes> CURRENT = ThreadLocal.withInitial(
            () -> new Attributes(BlockIdMap.UNMAPPED, -1.0F, 0));
    private static final ThreadLocal<float[]> ORIGIN = ThreadLocal.withInitial(() -> new float[3]);

    private TerrainMaterialContext() {
    }

    public static void setBlock(BlockState state) {
        CURRENT.set(new Attributes(BlockIdMap.active().id(state), -1.0F, state.getLightEmission()));
    }

    public static void setFluid(BlockState state) {
        CURRENT.set(new Attributes(BlockIdMap.active().id(state), 1.0F, state.getLightEmission()));
    }

    public static void setOrigin(float x, float y, float z) {
        float[] origin = ORIGIN.get();
        origin[0] = x;
        origin[1] = y;
        origin[2] = z;
    }

    public static float midBlockOffset(int axis, float vertexPosition) {
        return TerrainMidTexCoord.midBlockOffset(ORIGIN.get()[axis], vertexPosition);
    }

    public static int emission() {
        return CURRENT.get().emission();
    }

    public static int materialId() {
        return CURRENT.get().materialId();
    }

    public static float renderType() {
        return CURRENT.get().renderType();
    }

    public static void clear() {
        CURRENT.remove();
        ORIGIN.remove();
    }

    private record Attributes(int materialId, float renderType, int emission) {
    }
}
