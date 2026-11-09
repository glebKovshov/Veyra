package dev.veyra.shader;

import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.world.phys.Vec3;

public final class VeyraEnvironmentCheck {
    public static void run() {
        assert VeyraEnvironment.cloudHeight(new DimensionSpecialEffects.OverworldEffects()) == 192;
        assert VeyraEnvironment.cloudHeight(new DimensionSpecialEffects.NetherEffects()) == 192;
        assert VeyraEnvironment.cloudHeight(new DimensionSpecialEffects.EndEffects()) == 192;
        assert VeyraEnvironment.cloudHeight(effects(320)) == 320;
        assert VeyraEnvironment.cloudHeight(effects(-24)) == -24;
        assert VeyraEnvironment.cloudHeight(effects(Float.POSITIVE_INFINITY)) == 192;
        assert VeyraEnvironment.cloudHeight(effects(Float.NaN)) == 192;
        System.out.println("Legacy dimension cloud-height checks passed");
    }
    private static DimensionSpecialEffects effects(float height) {
        return new DimensionSpecialEffects(height, false, DimensionSpecialEffects.SkyType.NONE, false, false) {
            public Vec3 getBrightnessDependentFogColor(Vec3 color, float brightness) { return color; }
            public boolean isFoggyAt(int x, int z) { return false; }
        };
    }
}
