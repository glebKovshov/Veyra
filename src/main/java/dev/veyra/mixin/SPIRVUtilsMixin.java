package dev.veyra.mixin;

import dev.veyra.vulkan.VeyraSpirvCache;
import net.vulkanmod.vulkan.shader.SPIRVUtils;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.shaderc.Shaderc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.nio.ByteBuffer;

/** Keeps large shader-pack sources off LWJGL's small per-thread MemoryStack during shaderc compilation. */
@Mixin(value = SPIRVUtils.class, remap = false)
public abstract class SPIRVUtilsMixin {
    /** Reuse SPIR-V for byte-identical sources; VulkanMod only reads bytecode() from the result. */
    @Inject(method = "compileShader", at = @At("HEAD"), cancellable = true, remap = false)
    private static void veyra$reuseCompiledSpirv(String filename, String source, SPIRVUtils.ShaderKind shaderKind,
                                                 CallbackInfoReturnable<SPIRVUtils.SPIRV> callback) {
        if (source == null) return;
        ByteBuffer cached = VeyraSpirvCache.get(source, shaderKind.ordinal());
        if (cached != null) callback.setReturnValue(new SPIRVUtils.SPIRV(0L, cached));
    }

    @Inject(method = "compileShader", at = @At("RETURN"), remap = false)
    private static void veyra$rememberCompiledSpirv(String filename, String source, SPIRVUtils.ShaderKind shaderKind,
                                                    CallbackInfoReturnable<SPIRVUtils.SPIRV> callback) {
        SPIRVUtils.SPIRV spirv = callback.getReturnValue();
        if (source != null && spirv != null) VeyraSpirvCache.put(source, shaderKind.ordinal(), spirv.bytecode());
    }

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
