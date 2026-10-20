package dev.veyra.shader;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class TextureColorDistanceShaderAdapterTest {
    @Test
    void removesUnreachableSharedSamplerAndUniformDeclarations() throws IOException {
        String adapted = TextureColorDistanceShaderAdapter.adaptFragment("""
                uniform sampler2D colortex0;
                uniform int blockEntityId;
                vec4 ReadSharedSceneColor() { return texture2D(colortex0, vec2(0.5)); }
                int ReadSharedBlockId() { return blockEntityId; }
                uniform sampler2D texture;
                varying vec2 texcoord;
                void main() { gl_FragColor = texture2D(texture, texcoord); }
                """, "gbuffers_armor_glint", false);

        assertFalse(adapted.contains("ReadSharedSceneColor"), adapted);
        assertFalse(adapted.contains("ReadSharedBlockId"), adapted);
        assertFalse(adapted.contains("colortex0"), adapted);
        assertFalse(adapted.contains("blockEntityId"), adapted);
    }

    @Test
    void keepsRejectingSamplersReadFromTheFragmentEntrypoint() {
        IOException error = assertThrows(IOException.class, () ->
                TextureColorDistanceShaderAdapter.adaptFragment("""
                        uniform sampler2D colortex0;
                        varying vec2 texcoord;
                        void main() { gl_FragColor = texture2D(colortex0, texcoord); }
                        """, "gbuffers_armor_glint", false));
        assertTrue(error.getMessage().contains("unsupported sampler 'colortex0'"), error.getMessage());
    }

    @Test
    void mapsIrisTextureAtlasAliasToMinecraftGlintSampler() throws IOException {
        String adapted = TextureColorDistanceShaderAdapter.adaptFragment("""
                uniform sampler2D tex;
                varying vec2 texcoord;
                void main() { gl_FragColor = texture2D(tex, texcoord); }
                """, "gbuffers_armor_glint", false);

        assertTrue(adapted.contains("#define tex Sampler0"), adapted);
        assertTrue(adapted.contains("veyraTextureSample(tex, texCoord0)"), adapted);
        assertFalse(adapted.contains("uniform sampler2D tex;"), adapted);
    }

    @Test
    void mapsIrisFogColorThroughTheMinecraftFogUniformBlock() throws IOException {
        String adapted = TextureColorDistanceShaderAdapter.adaptFragment("""
                uniform vec3 fogColor;
                void main() { gl_FragColor = vec4(fogColor, 1.0); }
                """, "gbuffers_armor_glint", false);

        assertTrue(adapted.contains("uniform Fog"), adapted);
        assertTrue(adapted.contains("#define fogColor (FogColor.rgb)"), adapted);
        assertFalse(adapted.contains("uniform vec3 fogColor"), adapted);
    }
}
