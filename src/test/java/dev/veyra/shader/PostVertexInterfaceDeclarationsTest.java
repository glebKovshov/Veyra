package dev.veyra.shader;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PostVertexInterfaceDeclarationsTest {
    @Test
    void expandsIrisCommaSeparatedVaryingDeclarations() {
        String source = "flat out vec3 upVec, sunVec, eastVec;\n"
                + "flat in float vlFactor;\n";

        assertEquals("flat out vec3 upVec;\nflat out vec3 sunVec;\nflat out vec3 eastVec;\n"
                + "flat in float vlFactor;\n", PostVertexInterfaceDeclarations.expand(source));
    }

    @Test
    void advancesExplicitLocationsAcrossACommaSeparatedDeclaration() {
        assertEquals("layout(location = 3) flat out vec3 first;\n"
                + "layout(location = 4) flat out vec3 second;\n",
                PostVertexInterfaceDeclarations.expand(
                        "layout(location = 3) flat out vec3 first, second;\n"));
    }
}
