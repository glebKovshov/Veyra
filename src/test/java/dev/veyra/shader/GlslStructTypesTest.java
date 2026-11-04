package dev.veyra.shader;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GlslStructTypesTest {
    @Test
    void countsNestedMembersArraysAndMatrixColumns() {
        var types = GlslStructTypes.parse("""
                // struct Ignored { vec4 x; };
                struct Fog { vec3 color; mat3 basis; float density[2], distance; };
                struct Frame { Fog fog; uint flags; };
                """);
        assertEquals(7, types.locationSpan("Fog"));
        assertEquals(8, types.locationSpan("Frame"));
        assertFalse(types.hasIntegerMembers("Fog"));
        assertTrue(types.hasIntegerMembers("Frame"));
        assertFalse(types.contains("Ignored"));
    }

    @Test
    void rejectsMembersWhoseLocationSpanCannotBeResolved() {
        for (String member : new String[]{"vec4 x[SIZE];", "vec4 x[0];", "vec4 x[99999999999999999999999];",
                "Unknown x;", "vec4 x[2][3];", "mat4 x[20];"}) {
            assertFalse(GlslStructTypes.parse("struct Fog { float valid; " + member + " };").contains("Fog"), member);
        }
    }
}
