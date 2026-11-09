package dev.veyra.shader;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.veyra.mixin.accessor.LevelRendererAccessor;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.world.phys.Vec3;
import net.vulkanmod.render.chunk.WorldRenderer;
import java.util.HashSet;
import java.util.Set;

/** Version-specific entity submission; matrices and shadow-pass ownership stay with the caller. */
final class VeyraShadowEntities {
    private static RenderBuffers shadowRenderBuffers;
    private static SubmitNodeStorage shadowSubmitNodeStorage;
    private static FeatureRenderDispatcher shadowFeatureRenderDispatcher;

    static void render(Camera camera, DeltaTracker deltaTracker, Frustum entityShadowFrustum,
                       Vec3 cameraPosition, WorldRenderer worldRenderer, boolean entities, boolean blockEntities) {
        Minecraft minecraft = Minecraft.getInstance();
        RenderBuffers buffers = minecraft.renderBuffers();
        ensureFeatureDispatcher(minecraft, buffers);
        var levelRenderState = ((LevelRendererAccessor) minecraft.levelRenderer).veyra$getLevelRenderState();
        PoseStack poseStack = new PoseStack();
        SubmitNodeStorage submits = shadowSubmitNodeStorage;
        Set<Object> submittedEntities = new HashSet<>();
        try {
            float tickDelta = deltaTracker.getGameTimeDeltaPartialTick(false);
            var entityDispatcher = minecraft.getEntityRenderDispatcher();
            entityDispatcher.prepare(camera, minecraft.crosshairPickEntity);
            for (var entity : entities
                    ? minecraft.level.entitiesForRendering() : java.util.List.<net.minecraft.world.entity.Entity>of()) {
                if (!entityDispatcher.shouldRender(entity, entityShadowFrustum,
                        cameraPosition.x, cameraPosition.y, cameraPosition.z)) {
                    continue;
                }
                EntityRenderState state = entityDispatcher.extractEntity(entity, tickDelta);
                entityDispatcher.submit(state, levelRenderState.cameraRenderState,
                        state.x - cameraPosition.x, state.y - cameraPosition.y, state.z - cameraPosition.z,
                        poseStack, submits);
                submittedEntities.add(entity);
            }

            var player = minecraft.player;
            if (entities && player != null && submittedEntities.add(player)
                    && entityDispatcher.shouldRender(player, entityShadowFrustum,
                    cameraPosition.x, cameraPosition.y, cameraPosition.z)) {
                EntityRenderState state = entityDispatcher.extractEntity(player, tickDelta);
                entityDispatcher.submit(state, levelRenderState.cameraRenderState,
                        state.x - cameraPosition.x, state.y - cameraPosition.y, state.z - cameraPosition.z,
                        poseStack, submits);
            }

            if (blockEntities) {
                worldRenderer.renderBlockEntities(poseStack, levelRenderState, submits,
                        new it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<>());
            }
            shadowFeatureRenderDispatcher.renderAllFeatures();
            buffers.bufferSource().endBatch();
            shadowFeatureRenderDispatcher.endFrame();
        } finally { submits.clear(); }
    }

    private static void ensureFeatureDispatcher(Minecraft minecraft, RenderBuffers buffers) {
        if (shadowFeatureRenderDispatcher != null && shadowRenderBuffers == buffers) {
            return;
        }
        close();
        shadowRenderBuffers = buffers;
        shadowSubmitNodeStorage = new SubmitNodeStorage();
        shadowFeatureRenderDispatcher = new FeatureRenderDispatcher(shadowSubmitNodeStorage,
                minecraft.getBlockRenderer(), buffers.bufferSource(), minecraft.getAtlasManager(),
                buffers.outlineBufferSource(), buffers.crumblingBufferSource(), minecraft.font);
    }

    static void close() {
        if (shadowFeatureRenderDispatcher != null) {
            shadowFeatureRenderDispatcher.close();
            shadowFeatureRenderDispatcher = null;
        }
        if (shadowSubmitNodeStorage != null) {
            shadowSubmitNodeStorage.clear();
            shadowSubmitNodeStorage = null;
        }
        shadowRenderBuffers = null;
    }

}
