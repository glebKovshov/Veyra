package dev.veyra.shader;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
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

    @Test
    void reportsTheSelectedBufferObjectBindingAndResolvedAllocation() throws Exception {
        String source = """
                #version 450
                layout(std430, binding = 0) readonly buffer blockDataBuffer {
                    uvec4 data[];
                } blockDataSSBO;
                void main() { gl_FragColor = vec4(blockDataSSBO.data[0]); }
                """;
        var properties = ShaderPackStorageBufferProperties.parse("bufferObject.0 = 64\n");

        IOException error = assertThrows(IOException.class, () -> ShaderPackStorageBuffers.removeUnusedOrReject(
                source, "composite", properties, 800, 450));

        assertTrue(error.getMessage().contains("binding 0 (64 bytes)"), error.getMessage());
    }

    @Test
    void reportsThisBlocksSizeWithoutBeingAffectedByAnUnrelatedInvalidSlot() throws Exception {
        String source = """
                #version 450
                layout(std430, binding = 0) readonly buffer blockDataBuffer {
                    uvec4 data[];
                } blockDataSSBO;
                void main() { gl_FragColor = vec4(blockDataSSBO.data[0]); }
                """;
        var properties = ShaderPackStorageBufferProperties.parse("""
                bufferObject.0 = 64
                bufferObject.8 = 2147483648
                """);

        IOException error = assertThrows(IOException.class, () -> ShaderPackStorageBuffers.removeUnusedOrReject(
                source, "composite", properties, 800, 450));

        assertTrue(error.getMessage().contains("binding 0 (64 bytes)"), error.getMessage());
    }

    @Test
    void resolvesFixedAndScreenRelativeIrisAllocations() throws Exception {
        var properties = ShaderPackStorageBufferProperties.parse("""
                bufferObject.0 = 40
                bufferObject.8 = 4 true 0.5 0.25
                """);

        var sizes = properties.resolveSizes(800, 600);

        assertEquals(40L, sizes.get(0));
        assertEquals(240_000L, sizes.get(8));
    }

    @Test
    void rejectsUnsupportedBindingsMalformedRelativePropertiesAndDescriptorRangeOverflow() {
        assertThrows(IOException.class,
                () -> ShaderPackStorageBufferProperties.parse("bufferObject.9 = 16\n"));
        assertThrows(IOException.class,
                () -> ShaderPackStorageBufferProperties.parse("bufferObject.0 = 16 yes 1 1\n"));
        assertThrows(IOException.class,
                () -> ShaderPackStorageBufferProperties.parse("bufferObject.0 = 2147483648\n")
                        .resolveSizes(800, 600));
    }
}
