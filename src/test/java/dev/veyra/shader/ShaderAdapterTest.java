package dev.veyra.shader;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class ShaderAdapterTest {
    @TestFactory
    List<DynamicTest> adaptsTerrainAndEntityExamples() throws IOException {
        try (Stream<Path> files = Files.walk(Path.of("examples"))) {
            return files.filter(Files::isRegularFile).filter(path -> {
                String name = path.getFileName().toString();
                return name.matches("gbuffers_(terrain(?:_solid|_cutout)?|water|entities(?:_translucent)?|clouds)\\.fsh");
            }).sorted().map(path -> DynamicTest.dynamicTest(path.toString(), () -> {
                String name = path.getFileName().toString();
                String source = ShaderIncludes.expand(name, Files.readString(path), included -> {
                    Path file = path.getParent().resolve(included);
                    return Files.exists(file) ? Optional.of(Files.readString(file)) : Optional.empty();
                });
                String adapted;
                if (name.equals("gbuffers_clouds.fsh")) {
                    adapted = CloudShaderAdapter.adaptFragment(source);
                } else if (name.startsWith("gbuffers_entities")) {
                    adapted = EntityShaderAdapter.adaptFragment(source);
                } else {
                    adapted = TerrainShaderAdapter.adaptFragment(source);
                }
                assertTrue(adapted.startsWith("#version 460\n"));
                assertTrue(adapted.contains("void main("));
                assertFalse(adapted.contains("#version 120"));
            })).toList();
        }
    }

    @Test
    void rejectsUnsupportedCustomUniformWithItsName() {
        IOException error = assertThrows(IOException.class, () -> TerrainShaderAdapter.adaptFragment(
                "uniform float customUniform;\nvoid main() { gl_FragColor = vec4(customUniform); }\n"));
        assertTrue(error.getMessage().contains("customUniform"));
    }

    @Test
    void rejectsCloudTextureInputs() {
        assertThrows(IOException.class, () -> CloudShaderAdapter.adaptFragment(
                "uniform sampler2D texture;\nvarying vec2 texcoord;\nvoid main() { gl_FragColor = texture2D(texture, texcoord); }\n"));
    }
}
