package dev.veyra.shader;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.vulkanmod.render.chunk.WorldRenderer;
import java.util.HashSet;

/** The old backend renders into Minecraft's borrowed buffers without a feature dispatcher. */
final class VeyraShadowEntities {
    static void render(Camera camera, DeltaTracker deltaTracker, Frustum frustum,
                       Vec3 cameraPosition, WorldRenderer worldRenderer, boolean entities, boolean blockEntities) {
        Minecraft minecraft = Minecraft.getInstance();
        var buffers = minecraft.renderBuffers().bufferSource();
        var dispatcher = minecraft.getEntityRenderDispatcher();
        dispatcher.prepare(minecraft.level, camera, minecraft.crosshairPickEntity);
        PoseStack poses = new PoseStack();
        float tickDelta = deltaTracker.getGameTimeDeltaPartialTick(false);
        var rendered = new HashSet<Entity>();
        if (entities) {
            for (Entity entity : minecraft.level.entitiesForRendering()) {
                if (!dispatcher.shouldRender(entity, frustum, cameraPosition.x, cameraPosition.y, cameraPosition.z)) continue;
                render(entity, poses, tickDelta, cameraPosition);
                rendered.add(entity);
            }
            var player = minecraft.player;
            if (player != null && rendered.add(player)
                    && dispatcher.shouldRender(player, frustum, cameraPosition.x, cameraPosition.y, cameraPosition.z)) {
                render(player, poses, tickDelta, cameraPosition);
            }
        }
        if (blockEntities) {
            minecraft.getBlockEntityRenderDispatcher().prepare(minecraft.level, camera, minecraft.hitResult);
            worldRenderer.renderBlockEntities(poses, cameraPosition.x, cameraPosition.y, cameraPosition.z,
                    new it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<>(), tickDelta);
        }
        buffers.endBatch();
    }

    private static void render(Entity entity, PoseStack poses, float partialTick, Vec3 camera) {
        Minecraft minecraft = Minecraft.getInstance();
        var dispatcher = minecraft.getEntityRenderDispatcher();
        dispatcher.render(entity, Mth.lerp(partialTick, entity.xOld, entity.getX()) - camera.x,
                Mth.lerp(partialTick, entity.yOld, entity.getY()) - camera.y,
                Mth.lerp(partialTick, entity.zOld, entity.getZ()) - camera.z, partialTick,
                poses, minecraft.renderBuffers().bufferSource(), dispatcher.getPackedLightCoords(entity, partialTick));
    }

    static void close() { /* No owned feature dispatcher on this backend. */ }
}
