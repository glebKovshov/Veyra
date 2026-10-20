package dev.veyra.mixin;

import com.mojang.blaze3d.vertex.VertexFormat;
import dev.veyra.shader.TerrainMidTexCoord;
import net.vulkanmod.render.vertex.CustomVertexFormat;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = CustomVertexFormat.class, remap = false)
public abstract class CustomVertexFormatMixin {
    @Shadow @Final @Mutable public static VertexFormat COMPRESSED_TERRAIN;

    @Inject(method = "<clinit>", at = @At("TAIL"), remap = false)
    private static void veyra$extendCompressedTerrain(CallbackInfo ci) {
        COMPRESSED_TERRAIN = TerrainMidTexCoord.VERTEX_FORMAT;
    }
}
