package dev.veyra.mixin;

import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import dev.veyra.vulkan.LegacyVertexAttributes;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(VertexFormat.class)
public abstract class LegacyVertexFormatOffsetsMixin {
    @Inject(method = "<init>", at = @At("RETURN"))
    private void veyra$retainCustomOffsets(List<VertexFormatElement> elements, List<String> names,
            IntList offsets, int size, CallbackInfo callback) {
        // 1.21.5 otherwise discards offsets for elements outside Mojang's global registry.
        LegacyVertexAttributes.restoreOffsets((VertexFormat) (Object) this, offsets);
    }
}
