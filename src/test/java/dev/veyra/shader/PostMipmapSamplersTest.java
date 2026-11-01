package dev.veyra.shader;

import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertEquals;

class PostMipmapSamplersTest {
    @Test
    void readsActiveRequestsWithoutCommentsOrDisabledLevels() throws Exception {
        String source = GlslPreprocessor.process("composite.fsh", """
                #version 120
                const bool colortex0MipmapEnabled = true;
                const bool colortex2MipmapEnabled = false;
                const bool colortex15MipmapEnabled = true;
                // const bool colortex3MipmapEnabled = true;
                /* const bool colortex4MipmapEnabled = true; */
                #if 0
                const bool colortex5MipmapEnabled = true;
                #endif
                void main() {}
                """);
        assertEquals(Set.of(0, 15), PostMipmapSamplers.read(source));
    }
}
