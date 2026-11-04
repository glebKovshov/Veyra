package dev.veyra.shader;

import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ShaderSourceTest {
    @Test
    void distinguishesLocallyDeclaredLegacyNamesFromMissingInputs() {
        Pattern inputs = Pattern.compile("\\b(?:tangent|at_tangent)\\b");
        assertNull(ShaderSource.firstUndeclaredUse(inputs, "vec3 tangent = vec3(1); return tangent;"));
        assertEquals("at_tangent", ShaderSource.firstUndeclaredUse(inputs,
                "vec3 tangent = vec3(1); return tangent + at_tangent.xyz;"));
        assertEquals("tangent", ShaderSource.firstUndeclaredUse(inputs, "vec3 tangentOther; return tangent;"));
    }
}
