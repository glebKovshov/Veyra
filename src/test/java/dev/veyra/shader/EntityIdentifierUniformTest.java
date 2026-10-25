package dev.veyra.shader;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EntityIdentifierUniformTest {
    private static final String SOURCE = """
            #version 120
            uniform int blockEntityId;
            uniform int entityId;
            uniform int currentRenderedItemId;
            void main() { gl_FragColor = vec4(float(blockEntityId + entityId + currentRenderedItemId)); }
            """;
    private static final String ENTITY_COLOR_SOURCE = """
            #version 120
            uniform vec4 entityColor;
            void main() { gl_FragColor = entityColor; }
            """;

    @Test
    void terrainAndCloudsReceiveIrisFallbackValues() throws IOException {
        String terrain = TerrainShaderAdapter.adaptFragment(SOURCE);
        assertTrue(terrain.contains("const int blockEntityId = -1;"));
        assertTrue(terrain.contains("const int entityId = 0;"));
        assertTrue(terrain.contains("const int currentRenderedItemId = -1;"));
        assertFalse(terrain.contains("uniform int blockEntityId"));
        String clouds = CloudShaderAdapter.adaptFragment(SOURCE);
        assertTrue(clouds.contains("const int blockEntityId = -1;"), clouds);
    }

    @Test
    void entityAttributeProgramsReceivePerDrawIdentifierConstants() throws IOException {
        String unmapped = EntityShaderAdapter.adaptFragment(SOURCE);
        assertTrue(unmapped.contains("const int blockEntityId = -1;"), unmapped);
        assertTrue(unmapped.contains("const int entityId = -1;"), unmapped);
        assertTrue(unmapped.contains("const int currentRenderedItemId = -1;"), unmapped);
        assertFalse(unmapped.contains("uniform int blockEntityId"), unmapped);

        String mapped = EntityShaderAdapter.adaptFragment(
                SOURCE, "gbuffers_entities", false, false, 12, 83, 44);
        assertTrue(mapped.contains("const int entityId = 12;"), mapped);
        assertTrue(mapped.contains("const int blockEntityId = 83;"), mapped);
        assertTrue(mapped.contains("const int currentRenderedItemId = 44;"), mapped);
        assertTrue(EntityShaderAdapter.usesEntityIdentifier(SOURCE, "gbuffers_entities", "entityId"));
        assertTrue(EntityShaderAdapter.usesEntityIdentifier(SOURCE, "gbuffers_block", "blockEntityId"));
        assertTrue(EntityShaderAdapter.usesEntityIdentifier(
                SOURCE, "gbuffers_entities", "currentRenderedItemId"));
        assertTrue(EntityShaderAdapter.usesEntityIdentifier(
                SOURCE, "gbuffers_hand", "currentRenderedItemId"));
        assertTrue(EntityShaderAdapter.usesEntityIdentifier(
                SOURCE, "gbuffers_textured", "currentRenderedItemId"));
        assertTrue(EntityShaderAdapter.usesEntityIdentifier(
                SOURCE, "shadow_entities", "currentRenderedItemId"));
        String shadow = EntityShaderAdapter.adaptFragment(
                SOURCE, "shadow_entities", false, false, -1, -1, 55);
        assertTrue(shadow.contains("const int currentRenderedItemId = 55;"), shadow);
        assertTrue(ShaderPackUniformAdapter.isBoundEntityIdentifier(
                "shadow_block", "int", "currentRenderedItemId"));
        assertFalse(ShaderPackUniformAdapter.isUnboundEntityIdentifier("gbuffers_block", "int", "blockEntityId"));
        assertFalse(ShaderPackUniformAdapter.isUnboundEntityIdentifier("gbuffers_hand_water", "int", "entityId"));
        assertTrue(ShaderPackUniformAdapter.isUnboundEntityIdentifier("gbuffers_basic", "int", "entityId"));
        assertTrue(ShaderPackUniformAdapter.isUnboundEntityIdentifier(
                "gbuffers_basic", "int", "currentRenderedItemId"));
        assertFalse(ShaderPackUniformAdapter.isUnboundEntityIdentifier(
                "gbuffers_entities", "int", "currentRenderedItemId"));
        assertFalse(ShaderPackUniformAdapter.isUnboundEntityIdentifier("gbuffers_basic", "float", "entityId"));
        assertEquals("const vec4 entityColor = vec4(0.0);\n",
                ShaderPackUniformAdapter.unboundEntityDeclaration("gbuffers_terrain", "vec4", "entityColor"));
        assertNull(ShaderPackUniformAdapter.unboundEntityDeclaration("gbuffers_entities", "vec4", "entityColor"));
    }

    @Test
    void entityColorUsesMinecraftOverlayForEntityPipelines() throws IOException {
        String entities = EntityShaderAdapter.adaptFragment(ENTITY_COLOR_SOURCE, "gbuffers_entities");
        assertTrue(entities.contains("#define entityColor " + ShaderPackUniformAdapter.ENTITY_COLOR_FROM_OVERLAY), entities);
        assertTrue(entities.contains("in vec4 overlayColor;"), entities);
        assertFalse(entities.contains("uniform vec4 entityColor"), entities);
        assertFalse(entities.contains("const vec4 entityColor"), entities);

        String handFallback = EntityShaderAdapter.adaptFragment(ENTITY_COLOR_SOURCE, "gbuffers_textured");
        assertTrue(handFallback.contains("#define entityColor " + ShaderPackUniformAdapter.ENTITY_COLOR_FROM_OVERLAY), handFallback);

        String itemEntity = EntityShaderAdapter.adaptFragment(ENTITY_COLOR_SOURCE, "gbuffers_entities",
                false, false, -1, -1, ItemIdMap.UNMAPPED, false);
        assertTrue(itemEntity.contains("const vec4 entityColor = vec4(0.0);"), itemEntity);
        assertFalse(itemEntity.contains("#define entityColor " + ShaderPackUniformAdapter.ENTITY_COLOR_FROM_OVERLAY), itemEntity);
    }

    @Test
    void entityColorDefaultsToTransparentOnPipelinesWithoutEntityOverlay() throws IOException {
        String signText = EntityShaderAdapter.adaptTextFragment(ENTITY_COLOR_SOURCE, "gbuffers_textured");
        assertTrue(signText.contains("const vec4 entityColor = vec4(0.0);"), signText);
        assertFalse(signText.contains("#define entityColor " + ShaderPackUniformAdapter.ENTITY_COLOR_FROM_OVERLAY), signText);

        String text = EntityShaderAdapter.adaptFragment(ENTITY_COLOR_SOURCE, "gbuffers_text");
        assertTrue(text.contains("const vec4 entityColor = vec4(0.0);"), text);
    }

    @Test
    void signTextSuppliesDirectionalVectors() throws Exception {
        String source = """
                #version 120
                uniform sampler2D texture;
                varying vec2 texcoord;
                varying vec4 glcolor;
                varying vec3 upVec;
                varying vec3 sunVec;
                void main() {
                    float light = max(dot(upVec, sunVec), 0.0);
                    gl_FragData[0] = texture2D(texture, texcoord) * glcolor * light;
                }
                """;
        String adapted = EntityShaderAdapter.adaptTextFragment(source, "gbuffers_block");

        org.junit.jupiter.api.Assertions.assertTrue(
                adapted.contains("#define upVec normalize(gbufferModelView[1].xyz)"), adapted);
        org.junit.jupiter.api.Assertions.assertTrue(
                adapted.contains("#define sunVec normalize(sunPosition)"), adapted);
    }

    @Test
    void signTextSuppliesNorthVector() throws Exception {
        String source = """
                #version 120
                uniform sampler2D texture;
                varying vec2 texcoord;
                varying vec4 glcolor;
                varying vec3 northVec;
                void main() {
                    gl_FragData[0] = texture2D(texture, texcoord) * glcolor * max(northVec.z, 0.5);
                }
                """;
        String adapted = EntityShaderAdapter.adaptTextFragment(source, "gbuffers_block");

        org.junit.jupiter.api.Assertions.assertTrue(
                adapted.contains("#define northVec normalize(gbufferModelView[2].xyz)"), adapted);
    }

    @Test
    void signTextDerivesFaceNormal() throws Exception {
        String source = """
                #version 120
                uniform sampler2D texture;
                varying vec2 texcoord;
                varying vec4 glcolor;
                varying vec3 normal;
                void main() {
                    gl_FragData[0] = texture2D(texture, texcoord) * glcolor * max(normal.z, 0.5);
                }
                """;
        String adapted = EntityShaderAdapter.adaptTextFragment(source, "gbuffers_block");

        org.junit.jupiter.api.Assertions.assertTrue(EntityShaderAdapter.requiresTextNormalInput(source));
        org.junit.jupiter.api.Assertions.assertTrue(adapted.contains("in vec3 viewPos;"), adapted);
        org.junit.jupiter.api.Assertions.assertTrue(adapted.contains("normal = faceforward(normal, viewPos, normal);"), adapted);
        org.junit.jupiter.api.Assertions.assertFalse(adapted.contains("in vec3 normal;"), adapted);
    }

    @Test
    void signTextAcceptsLegacyColorInput() throws Exception {
        String source = """
                #version 120
                uniform sampler2D texture;
                varying vec2 texCoord;
                varying vec4 color;
                void main() {
                    gl_FragData[0] = texture2D(texture, texCoord) * color;
                }
                """;
        String adapted = EntityShaderAdapter.adaptTextFragment(source, "gbuffers_block");

        org.junit.jupiter.api.Assertions.assertTrue(adapted.contains("#define color veyraEntityColor"), adapted);
    }

    @Test
    void signTextNormalKeepsNormalParametersValid() throws Exception {
        String source = """
                #version 120
                uniform sampler2D texture;
                varying vec2 texcoord;
                varying vec4 glcolor;
                varying vec3 normal;
                float shade(vec3 normal) { return max(normal.z, 0.5); }
                void main() {
                    gl_FragData[0] = texture2D(texture, texcoord) * glcolor * shade(normal);
                }
                """;
        String adapted = EntityShaderAdapter.adaptTextFragment(source, "gbuffers_block");

        org.junit.jupiter.api.Assertions.assertTrue(adapted.contains("float shade(vec3 normal)"), adapted);
        org.junit.jupiter.api.Assertions.assertFalse(adapted.contains("#define normal"), adapted);
    }

    @Test
    void lightmapInputDetectedInCommaSeparatedVaryings() {
        org.junit.jupiter.api.Assertions.assertTrue(EntityShaderAdapter.requiresLightmapInput(
                "varying vec2 texCoord, lmCoord;\n"
                        + "void main() { gl_FragData[0] = vec4(lmCoord, 0.0, 1.0); }\n"));
    }

    @Test
    void entityColorUsesIrisOverlayConversion() {
        // Pin the generated GLSL, then check its semantics with a Java mirror of that exact expression.
        assertEquals("(vec4(overlayColor.rgb * float(overlayColor.a != 1.0), 1.0 - overlayColor.a))",
                ShaderPackUniformAdapter.ENTITY_COLOR_FROM_OVERLAY);
        // Vanilla's "no overlay" texel is opaque white: Iris exposes no tint at all.
        org.junit.jupiter.api.Assertions.assertArrayEquals(new float[] {0, 0, 0, 0},
                irisEntityColor(1.0F, 1.0F, 1.0F, 1.0F));
        // Vanilla's hurt overlay is red with partial alpha: Iris exposes red at 1 - alpha strength.
        org.junit.jupiter.api.Assertions.assertArrayEquals(new float[] {1.0F, 0.0F, 0.0F, 0.3F},
                irisEntityColor(1.0F, 0.0F, 0.0F, 0.7F), 1.0e-6F);
        org.junit.jupiter.api.Assertions.assertArrayEquals(new float[] {0.25F, 0.5F, 0.75F, 1.0F},
                irisEntityColor(0.25F, 0.5F, 0.75F, 0.0F), 1.0e-6F);
    }

    private static float[] irisEntityColor(float r, float g, float b, float a) {
        float keep = a != 1.0F ? 1.0F : 0.0F;
        return new float[] {r * keep, g * keep, b * keep, 1.0F - a};
    }
}
