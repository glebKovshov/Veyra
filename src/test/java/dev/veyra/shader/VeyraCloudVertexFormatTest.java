package dev.veyra.shader;

import org.junit.jupiter.api.Test;

import static com.mojang.blaze3d.vertex.VertexFormatElement.NORMAL;
import static org.junit.jupiter.api.Assertions.assertEquals;

class VeyraCloudVertexFormatTest {
    @Test
    void alignsCloudVertexStrideForVulkanFloatAttributes() {
        int stride = VeyraCloudVertexFormat.VERTEX_FORMAT.getVertexSize();

        assertEquals(28, stride);
        assertEquals(0, stride % Float.BYTES);
        assertEquals(24, VeyraCloudVertexFormat.VERTEX_FORMAT.getOffset(NORMAL));
    }
}
