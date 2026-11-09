package dev.veyra.shader;

import org.junit.jupiter.api.Test;
import net.vulkanmod.vulkan.shader.converter.GLSLParser;
import net.vulkanmod.vulkan.shader.converter.Lexer;
import net.vulkanmod.vulkan.shader.descriptor.UBO;
import static org.junit.jupiter.api.Assertions.*;

/** The modern native parser uses UBO placeholders; 0.5.6 uses direct storage descriptors instead. */
class NativeStorageParserTest {
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
    void acceptsStrippedBlockComments() {
        String source = "#version 450\n/* comment with *** and " + (char) 92
                + "\n*/\nvoid main() { }\n";
        var parser = new GLSLParser();
        parser.parse(new Lexer(ShaderPackStorageBuffers.stripCommentsForVulkanParser(source)), GLSLParser.Stage.FRAGMENT);
    }
}
