package dev.veyra.mixin.accessor;

import net.vulkanmod.vulkan.shader.converter.Lexer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = Lexer.class, remap = false)
public interface LexerAccessor {
    @Accessor("input")
    String veyra$getInput();
}
