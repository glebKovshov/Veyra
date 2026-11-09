package dev.veyra.smoke;

import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.vulkanmod.vulkan.framebuffer.SwapChain;

public final class SmokeEnvironment {
    private SmokeEnvironment() { }

    public static void freezeTime(ServerLevel world, MinecraftServer server) {
        world.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DAYLIGHT).set(false, server);
    }
    public static boolean isAcquired(SwapChain surface) {
        return ((dev.veyra.vulkan.LegacyAcquisitionState) (Object) net.vulkanmod.vulkan.Renderer.getInstance())
                .veyra$hasAcquired();
    }
    public static float cloudHeight(Minecraft client, float partialTick) { return dimensionCloudHeight(client); }
    public static float dimensionCloudHeight(Minecraft client) {
        return client.level.dimensionType().cloudHeight().orElse(192);
    }
}
