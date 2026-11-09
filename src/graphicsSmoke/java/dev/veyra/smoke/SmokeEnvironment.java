package dev.veyra.smoke;

import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.vulkanmod.vulkan.framebuffer.SwapChain;

public final class SmokeEnvironment {
    private SmokeEnvironment() { }

    public static void freezeTime(ServerLevel world, MinecraftServer server) {
        world.getGameRules().set(net.minecraft.world.level.gamerules.GameRules.ADVANCE_TIME, false, server);
    }
    public static boolean isAcquired(SwapChain surface) { return surface.isAcquired(); }
    public static float cloudHeight(Minecraft client, float partialTick) {
        return client.gameRenderer.getMainCamera().attributeProbe().getValue(
                net.minecraft.world.attribute.EnvironmentAttributes.CLOUD_HEIGHT, partialTick);
    }
    public static float dimensionCloudHeight(Minecraft client) {
        return client.level.environmentAttributes().getDimensionValue(
                net.minecraft.world.attribute.EnvironmentAttributes.CLOUD_HEIGHT);
    }
}
