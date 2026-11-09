package dev.veyra.vulkan;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.textures.TextureFormat;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.List;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VeyraUniformBlocksTest {
    @Test void preservesUniformBlocksAndTexelBufferFormats() {
        var id = Identifier.fromNamespaceAndPath("veyra", "uniform-copy-test");
        var builder = RenderPipeline.builder().withLocation(id).withVertexShader(id).withFragmentShader(id)
                .withVertexFormat(DefaultVertexFormat.POSITION, VertexFormat.Mode.TRIANGLES);
        var block = new RenderPipeline.UniformDescription("Existing", UniformType.UNIFORM_BUFFER);
        var texel = new RenderPipeline.UniformDescription("Texel", TextureFormat.RGBA8);
        VeyraUniformBlocks.copyTo(builder, block);
        VeyraUniformBlocks.copyTo(builder, texel);
        assertSame(builder, VeyraUniformBlocks.declare(builder, "VeyraParameters"));
        assertEquals(List.of(block, texel, new RenderPipeline.UniformDescription("VeyraParameters", UniformType.UNIFORM_BUFFER)),
                builder.build().getUniforms());
    }
}
