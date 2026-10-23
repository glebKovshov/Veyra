package dev.veyra.shader;

import org.junit.jupiter.api.Test;
import net.vulkanmod.vulkan.shader.converter.GLSLParser;
import net.vulkanmod.vulkan.shader.converter.Lexer;
import net.vulkanmod.vulkan.shader.descriptor.UBO;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShaderPackStorageBuffersTest {
    @Test
    void detectsOnlyReachableWritableStorageBuffers() throws Exception {
        String writable = """
                layout(std430, binding = 0) buffer Data { uint value; } dataBuffer;
                void writeData() { dataBuffer.value = 1u; }
                void main() { writeData(); }
                """;
        String readonly = """
                layout(std430, binding = 0) readonly buffer Data { uint value; } dataBuffer;
                void main() { gl_Position = vec4(float(dataBuffer.value)); }
                """;
        String inactive = """
                layout(std430, binding = 0) buffer Data { uint value; } dataBuffer;
                void unused() { dataBuffer.value = 1u; }
                void main() { }
                """;

        assertTrue(ShaderPackStorageBuffers.hasWritableActiveBuffer(
                UnreachableGlslFunctions.removeUnreachable(writable, "main")));
        assertFalse(ShaderPackStorageBuffers.hasWritableActiveBuffer(readonly));
        assertFalse(ShaderPackStorageBuffers.hasWritableActiveBuffer(
                UnreachableGlslFunctions.removeUnreachable(inactive, "main")));
    }

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
    void parsesAndValidatesIndirectDispatchBufferAndByteOffset() throws Exception {
        var properties = ShaderPackStorageBufferProperties.parse("""
                bufferObject.0 = 16
                indirect.shadowcomp_a = 0 4
                """);

        assertEquals(new ShaderPackStorageBufferProperties.IndirectDispatch(0, 4),
                properties.indirectDispatches().get("shadowcomp_a"));
        assertEquals(new ShaderPackStorageBufferProperties.IndirectDispatch(0, 4),
                properties.validateIndirectDispatch("shadowcomp_a", 800, 450));
    }

    @Test
    void validatesIndirectDispatchAgainstResolvedRelativeBufferSize() throws Exception {
        var properties = ShaderPackStorageBufferProperties.parse("""
                bufferObject.0 = 4 true 0.5 0.5
                indirect.shadowcomp_a = 0 4
                """);

        assertEquals(16L, properties.resolveSizes(4, 4).get(0));
        assertEquals(new ShaderPackStorageBufferProperties.IndirectDispatch(0, 4),
                properties.validateIndirectDispatch("shadowcomp_a", 4, 4));
        IOException error = assertThrows(IOException.class,
                () -> properties.validateIndirectDispatch("shadowcomp_a", 2, 2));
        assertTrue(error.getMessage().contains("beyond the 4-byte bufferObject.0"), error.getMessage());
    }

    @Test
    void rejectsMalformedOrOutOfBoundsIndirectDispatchProperties() throws Exception {
        assertThrows(IOException.class,
                () -> ShaderPackStorageBufferProperties.parse("indirect.shadowcomp_a = 0\n"));
        assertThrows(IOException.class,
                () -> ShaderPackStorageBufferProperties.parse("indirect.shadowcomp_a = 9 0\n"));
        assertThrows(IOException.class,
                () -> ShaderPackStorageBufferProperties.parse("indirect.shadowcomp_a = 0 -4\n"));
        assertThrows(IOException.class,
                () -> ShaderPackStorageBufferProperties.parse("indirect.shadowcomp_a = 0 2\n"));

        var missingBuffer = ShaderPackStorageBufferProperties.parse("indirect.shadowcomp_a = 0 0\n");
        IOException missing = assertThrows(IOException.class,
                () -> missingBuffer.validateIndirectDispatch("shadowcomp_a", 800, 450));
        assertTrue(missing.getMessage().contains("without a matching allocation"), missing.getMessage());

        var tooSmall = ShaderPackStorageBufferProperties.parse("""
                bufferObject.0 = 8
                indirect.shadowcomp_a = 0 0
                """);
        IOException range = assertThrows(IOException.class,
                () -> tooSmall.validateIndirectDispatch("shadowcomp_a", 800, 450));
        assertTrue(range.getMessage().contains("reads 12 dispatch bytes"), range.getMessage());
    }

    @Test
    void bridgesAnActiveStorageBlockThroughAParserCompatibleUniformBlock() throws Exception {
        String source = """
                #version 450
                layout(std430, binding = 0) readonly buffer blockDataBuffer {
                    uvec4 data[];
                } blockDataSSBO;
                void main() { gl_FragColor = vec4(blockDataSSBO.data[3]); }
                """;
        var properties = ShaderPackStorageBufferProperties.parse("bufferObject.0 = 128\n");

        var prepared = ShaderPackStorageBuffers.prepareForVulkanParser(source, properties, 800, 450);

        assertTrue(prepared.source().contains("uniform VeyraStorageBuffer_0_"), prepared.source());
        assertTrue(prepared.source().contains("blockDataSSBO"), prepared.source());
        assertFalse(prepared.source().contains("buffer blockDataBuffer"), prepared.source());
        assertEquals(1, prepared.storageBlocks().size());
        var block = prepared.storageBlocks().values().iterator().next();
        assertTrue(prepared.source().contains("layout(binding = "
                + ShaderPackStorageBuffers.VULKAN_DESCRIPTOR_BINDING_BASE + ") uniform VeyraStorageBuffer_0_"), prepared.source());
        assertEquals(ShaderPackStorageBuffers.VULKAN_DESCRIPTOR_BINDING_BASE, block.descriptorBinding());
        assertEquals("layout(std430, binding = 23) readonly buffer blockDataBuffer {\n    uvec4 data[];\n} blockDataSSBO;",
                block.declaration(23));
        assertEquals(128L, block.allocationBytes());
    }

    @Test
    void validatesScreenRelativeComputeStorageBuffersWithoutAssumingAViewport() throws Exception {
        String source = """
                #version 450
                layout(std430, binding = 2) buffer computeState {
                    uint value;
                } computeStateBuffer;
                void main() { computeStateBuffer.value = 7u; }
                """;
        var properties = ShaderPackStorageBufferProperties.parse("bufferObject.2 = 8 true 0.5 0.5\n");

        var prepared = ShaderPackStorageBuffers.validateForVulkanParser(source, properties);

        assertEquals(1, prepared.storageBlocks().size());
        assertEquals(0L, prepared.storageBlocks().values().iterator().next().allocationBytes());
    }

    @Test
    void rewritesAnActiveStorageBlockForDirectShadercCompilation() throws Exception {
        String source = """
                #version 450
                layout(std430, binding = 0) buffer blockDataBuffer {
                    uint value;
                } blockDataSSBO;
                void main() {
                    atomicAdd(blockDataSSBO.value, 1u);
                    gl_Position = vec4(0.0);
                }
                """;
        var properties = ShaderPackStorageBufferProperties.parse("bufferObject.0 = 128\n");

        var prepared = ShaderPackStorageBuffers.prepareForVulkanParser(source, properties, 800, 450);

        assertTrue(prepared.source().contains("uniform VeyraStorageBuffer_0_"), prepared.source());
        assertTrue(prepared.directSource().contains("layout(std430, binding = 1024) buffer blockDataBuffer"),
                prepared.directSource());
        assertTrue(prepared.directSource().contains("atomicAdd(blockDataSSBO.value, 1u)"), prepared.directSource());
        assertFalse(prepared.directSource().contains("VeyraStorageBuffer_0_"), prepared.directSource());
        assertEquals(1, prepared.storageBlocks().size());
    }

    @Test
    void vulkanModParserKeepsTheStoragePlaceholderAsAContiguousDescriptor() throws Exception {
        String source = """
                #version 450
                layout(std430, binding = 0) readonly buffer blockDataBuffer {
                    uvec4 data[];
                } blockDataSSBO;
                void main() { gl_FragColor = vec4(blockDataSSBO.data[0]); }
                """;
        var prepared = ShaderPackStorageBuffers.prepareForVulkanParser(source,
                ShaderPackStorageBufferProperties.parse("bufferObject.0 = 128\n"), 800, 450);
        String parserSource = prepared.source();
        GLSLParser parser = new GLSLParser();
        parser.parse(new Lexer(parserSource), GLSLParser.Stage.FRAGMENT);

        UBO[] descriptors = parser.createUBOs();

        assertEquals(1, descriptors.length);
        assertEquals(prepared.storageBlocks().keySet().iterator().next(), descriptors[0].name);
        assertEquals(0, descriptors[0].getBinding());
        assertTrue(parser.getOutput(GLSLParser.Stage.FRAGMENT).contains("uniform " + descriptors[0].name));
    }

    @Test
    void stripsBlockCommentsBeforeVulkanModLexing() {
        String source = "#version 450\n/*\n  comment with *** and " + (char) 92
                + "\n*/\nvoid main() { }\n";

        String parserSource = ShaderPackStorageBuffers.stripCommentsForVulkanParser(source);

        assertEquals(source.length(), parserSource.length());
        assertEquals(source.chars().filter(value -> value == '\n').count(),
                parserSource.chars().filter(value -> value == '\n').count());
        assertFalse(parserSource.contains(String.valueOf((char) 92)));
        assertTrue(parserSource.contains("void main()"));
        GLSLParser parser = new GLSLParser();
        parser.parse(new Lexer(parserSource), GLSLParser.Stage.FRAGMENT);
    }

    @Test
    void assignsDifferentVulkanBindingsToDifferentIrisBufferObjects() throws Exception {
        String source = """
                #version 450
                layout(std430, binding = 0) buffer First { uint value; } firstBuffer;
                layout(std430, binding = 1) readonly buffer Second { uint value; } secondBuffer;
                void main() { gl_Position = vec4(float(firstBuffer.value + secondBuffer.value)); }
                """;
        var prepared = ShaderPackStorageBuffers.prepareForVulkanParser(source,
                ShaderPackStorageBufferProperties.parse("bufferObject.0 = 4\nbufferObject.1 = 4\n"), 800, 450);

        assertTrue(prepared.source().contains("layout(binding = "
                + ShaderPackStorageBuffers.VULKAN_DESCRIPTOR_BINDING_BASE + ") uniform VeyraStorageBuffer_0_"), prepared.source());
        assertTrue(prepared.source().contains("layout(binding = "
                + (ShaderPackStorageBuffers.VULKAN_DESCRIPTOR_BINDING_BASE + 1) + ") uniform VeyraStorageBuffer_1_"), prepared.source());
        assertEquals(2, prepared.storageBlocks().size());
    }

    @Test
    void leavesInactiveStorageBlocksOutOfTheParserBridge() throws Exception {
        String source = """
                #version 450
                layout(std430, binding = 0) readonly buffer blockDataBuffer {
                    uvec4 data[];
                } blockDataSSBO;
                void unused() { gl_FragColor = vec4(blockDataSSBO.data[0]); }
                void main() { }
                """;
        var properties = ShaderPackStorageBufferProperties.parse("bufferObject.0 = 128\n");

        var prepared = ShaderPackStorageBuffers.prepareForVulkanParser(
                UnreachableGlslFunctions.removeUnreachable(source, "main"), properties, 800, 450);

        assertTrue(prepared.storageBlocks().isEmpty());
        assertFalse(prepared.source().contains("blockDataBuffer"), prepared.source());
    }

    @Test
    void rejectsActiveStorageBlocksWithoutAnAllocationOrBinding() throws Exception {
        String missingAllocation = """
                layout(std430, binding = 0) buffer Data { uvec4 values[]; } dataBuffer;
                void main() { gl_Position = vec4(dataBuffer.values[0]); }
                """;
        var noAllocation = ShaderPackStorageBufferProperties.parse("");
        IOException allocationError = assertThrows(IOException.class,
                () -> ShaderPackStorageBuffers.prepareForVulkanParser(missingAllocation, noAllocation, 640, 360));
        assertTrue(allocationError.getMessage().contains("no bufferObject.0 allocation"));

        String missingBinding = """
                buffer Data { uvec4 values[]; } dataBuffer;
                void main() { gl_Position = vec4(dataBuffer.values[0]); }
                """;
        IOException bindingError = assertThrows(IOException.class,
                () -> ShaderPackStorageBuffers.prepareForVulkanParser(missingBinding,
                        ShaderPackStorageBufferProperties.parse("bufferObject.0 = 128\n"), 640, 360));
        assertTrue(bindingError.getMessage().contains("no numeric layout(binding = N)"));
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
