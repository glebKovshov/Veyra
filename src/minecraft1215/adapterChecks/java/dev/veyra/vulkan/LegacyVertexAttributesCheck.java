package dev.veyra.vulkan;

import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import org.lwjgl.vulkan.VkVertexInputAttributeDescription;
import static org.lwjgl.vulkan.VK10.*;

final class LegacyVertexAttributesCheck {
    static void run() {
        var scalar = new VertexFormatElement(29, 0, VertexFormatElement.Type.FLOAT, VertexFormatElement.Usage.GENERIC, 1);
        var integer = new VertexFormatElement(30, 0, VertexFormatElement.Type.INT, VertexFormatElement.Usage.GENERIC, 1);
        var format = VertexFormat.builder().add("Position", VertexFormatElement.POSITION).padding(4)
                .add("Scalar", scalar).add("Color", VertexFormatElement.COLOR).add("Integer", integer).build();
        assert format.getOffset(scalar) == -1 : "Pinned native constructor behavior changed";
        LegacyVertexAttributes.restoreOffsets(format, new it.unimi.dsi.fastutil.ints.IntArrayList(new int[]{0, 16, 20, 24}));
        try (var attributes = LegacyVertexAttributes.describe(format, base -> {
            assert base.getElements().equals(java.util.List.of(VertexFormatElement.POSITION, VertexFormatElement.COLOR));
            var nativeValues = VkVertexInputAttributeDescription.calloc(2);
            nativeValues.get(0).binding(0).location(0).offset(0).format(VK_FORMAT_R32G32B32_SFLOAT);
            nativeValues.get(1).binding(0).location(1).offset(12).format(VK_FORMAT_R8G8B8A8_UNORM);
            return nativeValues;
        })) {
            int[] offsets = {0, 16, 20, 24};
            int[] formats = {VK_FORMAT_R32G32B32_SFLOAT, VK_FORMAT_R32_SFLOAT, VK_FORMAT_R8G8B8A8_UNORM, VK_FORMAT_R32_SINT};
            for (int i = 0; i < 4; i++) {
                assert attributes.get(i).binding() == 0 && attributes.get(i).location() == i;
                assert attributes.get(i).offset() == offsets[i] && attributes.get(i).format() == formats[i]
                        : "Attribute " + i + ": offset=" + attributes.get(i).offset() + ", format=" + attributes.get(i).format();
            }
        }
        var plain = VertexFormat.builder().add("Position", VertexFormatElement.POSITION).build();
        try (var nativeValues = VkVertexInputAttributeDescription.calloc(1)) {
            assert LegacyVertexAttributes.describe(plain, input -> {
                assert input == plain;
                return nativeValues;
            }) == nativeValues;
        }
        var vector = new VertexFormatElement(29, 0, VertexFormatElement.Type.FLOAT, VertexFormatElement.Usage.GENERIC, 2);
        var unsupported = VertexFormat.builder().add("Position", VertexFormatElement.POSITION).add("Vector", vector).build();
        try {
            LegacyVertexAttributes.describe(unsupported, input -> VkVertexInputAttributeDescription.calloc(1));
            throw new AssertionError("Unsupported generic vector was accepted");
        } catch (IllegalArgumentException expected) { }
        System.out.println("Legacy vertex attributes retain native formats, padding and locations");
    }
}
