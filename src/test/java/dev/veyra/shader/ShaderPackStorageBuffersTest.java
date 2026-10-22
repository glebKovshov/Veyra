package dev.veyra.shader;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShaderPackStorageBuffersTest {
    @Test
    void stripsAStorageBufferThatOnlyAnUnreachableHelperUses() throws Exception {
        String source = """
                #version 450
                layout(std430, binding = 0) readonly buffer blockDataBuffer {
                    uvec4 data[];
                } blockDataSSBO;
                uvec4 unused() { return blockDataSSBO.data[0]; }
                void main() { }
                """;

        String reachable = UnreachableGlslFunctions.removeUnreachable(source, "main");
        String adapted = ShaderPackStorageBuffers.removeUnusedOrReject(reachable, "test");

        assertFalse(adapted.contains("blockDataBuffer"), adapted);
        assertTrue(adapted.contains("void main()"), adapted);
    }

    @Test
    void reportsAnActiveStorageBufferBeforeNativeCompilation() {
        String source = """
                #version 450
                layout(std430, binding = 0) readonly buffer blockDataBuffer {
                    uvec4 data[];
                } blockDataSSBO;
                void main() { gl_FragColor = vec4(blockDataSSBO.data[0]); }
                """;

        IOException error = assertThrows(IOException.class,
                () -> ShaderPackStorageBuffers.removeUnusedOrReject(source, "composite"));

        assertTrue(error.getMessage().contains("active storage buffer 'blockDataBuffer'"));
        assertTrue(error.getMessage().contains("VulkanMod 0.6.8 native RenderPipeline"));
    }
}
