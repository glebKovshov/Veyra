package dev.veyra.shader;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PostSamplerAdapterTest {
    @Test
    void routesLegacyTexToThePrimaryPostInputWithoutCreatingAnUnboundDescriptor() {
        String source = "uniform sampler2D tex;\n"
                + "void main() { vec4 color = texture(tex, texcoord); }\n";

        String adapted = PostSamplerAdapter.bindPrimaryTexture(source);

        assertFalse(adapted.contains("sampler2D tex"), adapted);
        assertTrue(adapted.contains("texture(InSampler, texcoord)"), adapted);
    }

    @Test
    void doesNotRewriteTextureBuiltinsOrTexcoordIdentifiers() {
        String source = "uniform sampler2D tex;\n"
                + "vec2 texcoord = textureSize(tex, 0);\n"
                + "void main() { vec4 color = texture(tex, texcoord); }\n";

        String adapted = PostSamplerAdapter.bindPrimaryTexture(source);

        assertTrue(adapted.contains("textureSize(InSampler, 0)"), adapted);
        assertTrue(adapted.contains("texture(InSampler, texcoord)"), adapted);
        assertFalse(adapted.contains("textureSize(InSamplercoord"), adapted);
    }
}
