package dev.veyra.shader;

import net.minecraft.client.Minecraft;

/** Legacy sky rotation uses time-of-day turns, rather than environment attributes. */
final class VeyraEnvironment {
    private VeyraEnvironment() { }

    static int moonPhase(Minecraft minecraft) { return minecraft.level.getMoonPhase(); }

    static float sunAngle(Minecraft minecraft, float partialTick) {
        return minecraft.level.getTimeOfDay(partialTick) * 360.0F;
    }

    static float moonAngle(Minecraft minecraft, float partialTick) {
        return (sunAngle(minecraft, partialTick) + 180.0F) % 360.0F;
    }

    static float cloudHeight(Minecraft minecraft, float partialTick) {
        return minecraft.level.dimensionType().cloudHeight().orElse(192);
    }

    static int skyColor(Minecraft minecraft, float partialTick) {
        return minecraft.level.getSkyColor(minecraft.gameRenderer.getMainCamera().getPosition(), partialTick);
    }
}
