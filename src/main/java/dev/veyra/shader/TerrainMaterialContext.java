package dev.veyra.shader;

import net.minecraft.world.level.block.state.BlockState;

/** Per chunk-build-thread values appended to VulkanMod's compressed terrain vertices. */
public final class TerrainMaterialContext {
    private static final ThreadLocal<Attributes> CURRENT = ThreadLocal.withInitial(
            () -> new Attributes(BlockIdMap.UNMAPPED, -1.0F));

    private TerrainMaterialContext() {
    }

    public static void setBlock(BlockState state) {
        CURRENT.set(new Attributes(BlockIdMap.active().id(state), -1.0F));
    }

    public static void setFluid(BlockState state) {
        CURRENT.set(new Attributes(BlockIdMap.active().id(state), 1.0F));
    }

    public static int materialId() {
        return CURRENT.get().materialId();
    }

    public static float renderType() {
        return CURRENT.get().renderType();
    }

    public static void clear() {
        CURRENT.remove();
    }

    private record Attributes(int materialId, float renderType) {
    }
}
