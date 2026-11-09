package dev.veyra.shader;

import net.minecraft.client.Minecraft;
import net.minecraft.world.attribute.EnvironmentAttributes;

/** Version-specific environment values consumed by shader uniforms. */
final class VeyraEnvironment {
    private VeyraEnvironment() { }

    static int moonPhase(Minecraft minecraft) {
        return minecraft.level.environmentAttributes().getDimensionValue(EnvironmentAttributes.MOON_PHASE).index();
    }

    static float sunAngle(Minecraft minecraft, float partialTick) {
        return minecraft.gameRenderer.getMainCamera().attributeProbe().getValue(EnvironmentAttributes.SUN_ANGLE, partialTick);
    }

    static float moonAngle(Minecraft minecraft, float partialTick) {
        return minecraft.gameRenderer.getMainCamera().attributeProbe().getValue(EnvironmentAttributes.MOON_ANGLE, partialTick);
    }

    static float cloudHeight(Minecraft minecraft, float partialTick) {
        return minecraft.gameRenderer.getMainCamera().attributeProbe().getValue(EnvironmentAttributes.CLOUD_HEIGHT, partialTick);
    }

    static int skyColor(Minecraft minecraft, float partialTick) {
        return minecraft.gameRenderer.getMainCamera().attributeProbe().getValue(EnvironmentAttributes.SKY_COLOR, partialTick);
    }
}
