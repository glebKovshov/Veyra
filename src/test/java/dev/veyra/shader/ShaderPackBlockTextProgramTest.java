package dev.veyra.shader;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShaderPackBlockTextProgramTest {
    @Test
    void usesBlockTranslucentForBlockEntityTextEvenWhenTheBlockPassIsDisabled(@TempDir Path root)
            throws Exception {
        Path shaders = root.resolve("shaders");
        Files.createDirectories(shaders);
        Files.writeString(shaders.resolve("gbuffers_block_translucent.fsh"),
                "void main() { gl_FragColor = vec4(1.0); }\n");
        Files.writeString(shaders.resolve("gbuffers_block_translucent.vsh"),
                "void main() { gl_Position = ftransform(); }\n");
        Files.writeString(shaders.resolve("gbuffers_textured.fsh"),
                "void main() { gl_FragColor = vec4(1.0); }\n");

        ShaderPack pack = ShaderPack.inspect(root);
        assertEquals("gbuffers_textured", pack.blockTextProgram(false));
        assertEquals("gbuffers_block_translucent", pack.blockTextProgram(true));

        Files.writeString(shaders.resolve("shaders.properties"),
                "program.gbuffers_block_translucent.enabled=false\n");
        ShaderPack disabledBlockPass = ShaderPack.inspect(root);
        assertEquals("gbuffers_block_translucent", disabledBlockPass.blockTextProgram(true));
        assertEquals("gbuffers_textured", disabledBlockPass.blockTextProgram(false));
        assertFalse(disabledBlockPass.readBlockFragment("gbuffers_block_translucent").isPresent());
        assertTrue(disabledBlockPass.readBlockTextFragment("gbuffers_block_translucent").isPresent());
        assertTrue(disabledBlockPass.readStage("gbuffers_block_translucent.vsh").isPresent());
    }
}
