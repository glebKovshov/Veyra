package dev.veyra.mixin;

import net.vulkanmod.vulkan.shader.SPIRVUtils;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.shaderc.Shaderc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.nio.ByteBuffer;

/** Keeps large shader-pack sources off LWJGL's small per-thread MemoryStack during shaderc compilation. */
@Mixin(value = SPIRVUtils.class, remap = false)
public abstract class SPIRVUtilsMixin {
    @Redirect(
            method = "compileShader",
            at = @At(value = "INVOKE", target = "Lorg/lwjgl/util/shaderc/Shaderc;shaderc_compile_into_spv(JLjava/lang/CharSequence;ILjava/lang/CharSequence;Ljava/lang/CharSequence;J)J"),
            remap = false)
    private static long veyra$compileWithDirectSourceBuffer(long compiler, CharSequence source, int kind,
                                                             CharSequence sourceName, CharSequence entryPoint,
                                                             long options) {
        ByteBuffer sourceBuffer = MemoryUtil.memUTF8(source, false);
        ByteBuffer sourceNameBuffer = null;
        ByteBuffer entryPointBuffer = null;
        try {
            sourceNameBuffer = MemoryUtil.memUTF8(sourceName, true);
            entryPointBuffer = MemoryUtil.memUTF8(entryPoint, true);
            return Shaderc.shaderc_compile_into_spv(compiler, sourceBuffer, kind,
                    sourceNameBuffer, entryPointBuffer, options);
        } finally {
            if (entryPointBuffer != null) MemoryUtil.memFree(entryPointBuffer);
            if (sourceNameBuffer != null) MemoryUtil.memFree(sourceNameBuffer);
            MemoryUtil.memFree(sourceBuffer);
        }
    }
}
