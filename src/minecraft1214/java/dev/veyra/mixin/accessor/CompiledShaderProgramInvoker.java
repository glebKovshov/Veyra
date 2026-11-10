package dev.veyra.mixin.accessor;

import net.minecraft.client.renderer.CompiledShaderProgram;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(CompiledShaderProgram.class)
public interface CompiledShaderProgramInvoker {
    @Invoker("<init>") static CompiledShaderProgram veyra$create(int programId) { throw new AssertionError(); }
}
