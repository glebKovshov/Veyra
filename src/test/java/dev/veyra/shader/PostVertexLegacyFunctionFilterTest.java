package dev.veyra.shader;

import org.junit.jupiter.api.Test;

import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class PostVertexLegacyFunctionFilterTest {
    private static final Pattern LEGACY_INPUT = Pattern.compile("\\bgl_MultiTexCoord1\\b");

    @Test
    void removesAnUnreferencedHelperThatUsesAnUnavailableInput() {
        String source = "vec2 GetLightMapCoordinates() {\n"
                + "    return gl_MultiTexCoord1.xy;\n}\n"
                + "void main() { gl_Position = vec4(0.0); }\n";

        String result = PostVertexLegacyFunctionFilter.removeUnreferencedDefinitions(source, LEGACY_INPUT);

        assertFalse(result.contains("GetLightMapCoordinates"), result);
        assertFalse(result.contains("gl_MultiTexCoord1"), result);
        assertTrue(result.contains("void main()"), result);
        assertEquals(source.lines().count(), result.lines().count());
    }

    @Test
    void keepsTheHelperWhenMainCallsIt() {
        String source = "vec2 GetLightMapCoordinates() { return gl_MultiTexCoord1.xy; }\n"
                + "void main() { vec2 lm = GetLightMapCoordinates(); }\n";

        String result = PostVertexLegacyFunctionFilter.removeUnreferencedDefinitions(source, LEGACY_INPUT);

        assertEquals(source, result);
    }

    @Test
    void ignoresCommentsMaskedByTheCaller() {
        String source = "vec2 GetLightMapCoordinates() { return vec2(0.0); }\n"
                + "void main() { /* gl_MultiTexCoord1 */ }\n";
        String uncommented = ShaderSource.maskBlockAndLineComments(source);

        assertEquals(uncommented,
                PostVertexLegacyFunctionFilter.removeUnreferencedDefinitions(uncommented, LEGACY_INPUT));
    }
}
