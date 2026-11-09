package dev.veyra.vulkan;

import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import java.util.function.Function;
import org.lwjgl.vulkan.VkVertexInputAttributeDescription;
import static org.lwjgl.vulkan.VK10.*;

public final class LegacyVertexAttributes {
    private LegacyVertexAttributes() { }

    public static void restoreOffsets(VertexFormat format, it.unimi.dsi.fastutil.ints.IntList offsets) {
        var elements = format.getElements();
        for (int index = 0; index < elements.size(); index++)
            format.getOffsetsByElement()[elements.get(index).id()] = offsets.getInt(index);
    }

    public static VkVertexInputAttributeDescription.Buffer describe(VertexFormat format,
            Function<VertexFormat, VkVertexInputAttributeDescription.Buffer> nativeDescriptions) {
        var elements = format.getElements();
        if (elements.stream().noneMatch(element -> element.usage() == VertexFormatElement.Usage.GENERIC))
            return nativeDescriptions.apply(format);
        var builder = VertexFormat.builder();
        for (var element : elements) {
            if (element.usage() != VertexFormatElement.Usage.GENERIC)
                builder.add(format.getElementName(element), element);
        }
        // Retain native normalization/format rules, then restore original locations and byte offsets.
        try (var nativeAttributes = nativeDescriptions.apply(builder.build())) {
            var result = VkVertexInputAttributeDescription.calloc(elements.size());
            try {
                int nativeIndex = 0;
                for (int index = 0; index < elements.size(); index++) {
                    var element = elements.get(index);
                    var attribute = result.get(index);
                    if (element.usage() == VertexFormatElement.Usage.GENERIC) {
                        if (element.count() != 1) throw new IllegalArgumentException("Legacy generic attributes require one component");
                        attribute.binding(0).format(switch (element.type()) {
                            case FLOAT -> VK_FORMAT_R32_SFLOAT;
                            case INT -> VK_FORMAT_R32_SINT;
                            default -> throw new IllegalArgumentException("Unsupported legacy generic attribute: " + element.type());
                        });
                    } else attribute.set(nativeAttributes.get(nativeIndex++));
                    attribute.location(index).offset(format.getOffset(element));
                }
                return result;
            } catch (RuntimeException | Error failure) {
                result.free();
                throw failure;
            }
        }
    }
}
