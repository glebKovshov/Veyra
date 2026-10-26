package dev.veyra.vulkan;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VeyraGeometryTargetsTest {
    @Test
    void bindsTerrainShadowDepthOnlyForSceneTerrain() {
        assertTrue(VeyraGeometryTargets.shouldBindTerrainShadowSampler(true, false));
        assertFalse(VeyraGeometryTargets.shouldBindTerrainShadowSampler(true, true));
        assertFalse(VeyraGeometryTargets.shouldBindTerrainShadowSampler(false, false));
    }

    @Test
    void acceptsSceneAndShaderPackAttachmentsWithMatchingDimensions() {
        var extent = new VeyraGeometryTargets.AttachmentExtent(800, 450);

        assertTrue(VeyraGeometryTargets.hasMatchingDimensions(List.of(extent, extent), extent));
    }

    @Test
    void rejectsTemporarySmallSceneAgainstFullSizeShaderPackTargets() {
        var temporaryScene = new VeyraGeometryTargets.AttachmentExtent(10, 10);
        var shaderPackTarget = new VeyraGeometryTargets.AttachmentExtent(800, 450);

        assertFalse(VeyraGeometryTargets.hasMatchingDimensions(List.of(temporaryScene, shaderPackTarget), temporaryScene));
    }

    @Test
    void rejectsDepthAttachmentWithDifferentDimensions() {
        var color = new VeyraGeometryTargets.AttachmentExtent(800, 450);
        var depth = new VeyraGeometryTargets.AttachmentExtent(10, 10);

        assertFalse(VeyraGeometryTargets.hasMatchingDimensions(List.of(color), depth));
    }
}
