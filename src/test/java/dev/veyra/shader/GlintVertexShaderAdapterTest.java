package dev.veyra.shader;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class GlintVertexShaderAdapterTest {
    private static final String MINECRAFT_GLINT_VERTEX = """
            #version 330
            in vec3 Position;
            in vec2 UV0;
            out vec2 texCoord0;
            out float sphericalVertexDistance;
            out float cylindricalVertexDistance;
            uniform mat4 ProjMat;
            uniform mat4 ModelViewMat;
            uniform mat4 TextureMat;
            void main() {
                gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
                texCoord0 = (TextureMat * vec4(UV0, 0.0, 1.0)).xy;
                sphericalVertexDistance = 0.0;
                cylindricalVertexDistance = 0.0;
            }
            """;

    @Test
    void executesLegacyAndFlatGlintOutputsOnMinecraftVertexInterface() throws IOException {
        String adapted = GlintVertexShaderAdapter.adaptStage(MINECRAFT_GLINT_VERTEX, """
                #version 130
                out vec2 texCoord;
                flat out vec4 glColor;
                void main() {
                    gl_Position = ftransform();
                    texCoord = gl_MultiTexCoord0.xy;
                    glColor = gl_Color;
                }
                """, "gbuffers_armor_glint").source();

        assertTrue(adapted.contains("#define VEYRA_GLINT_COLOR_OUTPUT layout(location = 0) flat out vec4 vertexColor;"));
        assertTrue(adapted.contains("#define VEYRA_GLINT_OUTPUT_1 layout(location = 1) smooth out vec2 texCoord0;"));
        assertTrue(adapted.contains("VEYRA_GLINT_COLOR_OUTPUT\n"));
        assertTrue(adapted.contains("VEYRA_GLINT_OUTPUT_1\n"));
        assertFalse(java.util.regex.Pattern.compile("(?m)^\\s*layout\\s*\\(\\s*location\\s*=").matcher(adapted).find(), adapted);
        assertTrue(adapted.contains("#define glColor vertexColor"));
        assertTrue(adapted.contains("#define gl_Color vec4(1.0)"));
        assertFalse(adapted.contains("layout(location = 2) in vec4 Color;"), adapted);
        assertTrue(adapted.contains("veyraArmorGlintMain();"));
        assertTrue(adapted.contains("void veyraArmorGlintMain()"));
    }

    @Test
    void rejectsVertexInputsAndVaryingsOutsideTheNativeGlintPipeline() {
        IOException attribute = assertThrows(IOException.class, () -> GlintVertexShaderAdapter.validate(
                "gbuffers_armor_glint", "attribute vec3 mc_Entity;\nvoid main(){}\n"));
        assertTrue(attribute.getMessage().contains("mc_Entity"));

        IOException output = assertThrows(IOException.class, () -> GlintVertexShaderAdapter.validate(
                "gbuffers_armor_glint", "varying vec3 customNormal;\nvoid main(){}\n"));
        assertTrue(output.getMessage().contains("customNormal"));
    }

    @Test
    void adaptsSharedLibraryNoiseAndUnusedLightmapHelpers() throws IOException {
        GlintVertexShaderAdapter.AdaptedStage adapted = GlintVertexShaderAdapter.adaptStage(
                MINECRAFT_GLINT_VERTEX, """
                        #version 130
                        uniform sampler2D noisetex;
                        out vec2 texCoord;
                        flat out vec4 glColor;
                        vec2 GetLightMapCoordinates() {
                            return (gl_TextureMatrix[1] * gl_MultiTexCoord1).xy;
                        }
                        float ReadNoise() { return texture2D(noisetex, vec2(0.5)).r; }
                        void main() {
                            gl_Position = ftransform();
                            texCoord = gl_MultiTexCoord0.xy;
                            glColor = gl_Color;
                            float noise = ReadNoise();
                        }
                        """, "gbuffers_armor_glint");

        assertTrue(adapted.usesNoiseSampler());
        assertTrue(adapted.source().contains("uniform sampler2D VeyraNoiseSampler;"));
        assertTrue(adapted.source().contains("#define noisetex VeyraNoiseSampler"));
        assertTrue(adapted.source().contains("#define gl_MultiTexCoord1 vec4(0.0, 0.0, 0.0, 1.0)"));
        assertTrue(adapted.source().contains("textureLod" ) || adapted.source().contains("texture("));
    }

    @Test
    void removesUnreachableSharedLibrarySamplersButRejectsARealSamplerRead() throws IOException {
        String sharedLibrary = """
                uniform sampler2D colortex0;
                uniform int blockEntityId;
                vec4 ReadSceneColor() { return texture2D(colortex0, vec2(0.5)); }
                int ReadBlockEntityId() { return blockEntityId; }
                out vec2 texCoord;
                void main() {
                    gl_Position = ftransform();
                    texCoord = gl_MultiTexCoord0.xy;
                }
                """;
        String adapted = GlintVertexShaderAdapter.adaptStage(
                MINECRAFT_GLINT_VERTEX, sharedLibrary, "gbuffers_armor_glint").source();
        assertFalse(adapted.contains("ReadSceneColor"), adapted);
        assertFalse(adapted.contains("ReadBlockEntityId"), adapted);
        assertFalse(adapted.contains("colortex0"), adapted);
        assertFalse(adapted.contains("blockEntityId"), adapted);

        IOException error = assertThrows(IOException.class, () -> GlintVertexShaderAdapter.adaptStage(
                MINECRAFT_GLINT_VERTEX, sharedLibrary.replace("texCoord = gl_MultiTexCoord0.xy;",
                        "texCoord = gl_MultiTexCoord0.xy; gl_Position.x += ReadSceneColor().x;"),
                "gbuffers_armor_glint"));
        assertTrue(error.getMessage().contains("unsupported sampler 'colortex0'"), error.getMessage());
    }

    @Test
    void linksFlatFragmentColorToTheFlatVertexOutput() throws IOException {
        String adapted = TextureColorDistanceShaderAdapter.adaptFragment("""
                flat in vec4 glColor;
                in vec2 texCoord;
                uniform sampler2D texture;
                void main() { gl_FragColor = texture2D(texture, texCoord) * glColor; }
                """, "gbuffers_armor_glint", true, true, "flat", "smooth", true);

        assertTrue(adapted.contains("#define VEYRA_GLINT_COLOR_INPUT layout(location = 0) flat in vec4 vertexColor;"));
        assertTrue(adapted.contains("#define VEYRA_GLINT_TEXCOORD_INPUT layout(location = 1) smooth in vec2 texCoord0;"));
        assertTrue(adapted.contains("#define VEYRA_ENTITY_OUTPUT_0 layout(location = 0) out vec4 veyraFragColor;"));
        assertFalse(java.util.regex.Pattern.compile("(?m)^\\s*layout\\s*\\(\\s*location\\s*=").matcher(adapted).find(), adapted);

        IOException mismatch = assertThrows(IOException.class, () -> TextureColorDistanceShaderAdapter.adaptFragment("""
                in vec4 glColor;
                in vec2 texCoord;
                void main() { gl_FragColor = glColor; }
                """, "gbuffers_armor_glint", true, true, "flat", "smooth", true));
        assertTrue(mismatch.getMessage().contains("interpolation does not match"));
    }
}
