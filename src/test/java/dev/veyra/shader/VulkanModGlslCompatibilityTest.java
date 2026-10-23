package dev.veyra.shader;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VulkanModGlslCompatibilityTest {
    @Test
    void removesOnlyExplicitInputFunctionParameterQualifiers() {
        String source = """
                vec3 adjust(
                    in vec3 position,
                    in float scale
                ) {
                    return position * scale;
                }
                layout(location = 0) in vec3 Position;
                in vec2 TexCoord;
                // in vec4 commentOnly;
                """;

        String adapted = VulkanModGlslCompatibility.stripExplicitInputParameterQualifiers(source);

        assertFalse(adapted.contains("in vec3 position"), adapted);
        assertFalse(adapted.contains("in float scale"), adapted);
        assertTrue(adapted.contains("layout(location = 0) in vec3 Position;"), adapted);
        assertTrue(adapted.contains("in vec2 TexCoord;"), adapted);
        assertTrue(adapted.contains("// in vec4 commentOnly;"), adapted);
        assertEquals(source.length(), adapted.length());
    }

    @Test
    void returnsSourcesWithoutExplicitFunctionInputQualifiersUnchanged() {
        String source = "void main() { vec3 value = texture(sampler, uv).xyz; }\n";

        assertSame(source, VulkanModGlslCompatibility.stripExplicitInputParameterQualifiers(source));
    }
}
