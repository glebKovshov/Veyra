package dev.veyra.compat;

import com.mojang.blaze3d.shaders.Uniform;
import net.minecraft.client.renderer.CompiledShaderProgram;

/** CPU uniform and sampler storage of a compiled pipeline: a vanilla program object without a GL program. */
public final class EGlProgram {
    private final CompiledShaderProgram program;

    public EGlProgram(CompiledShaderProgram program) { this.program = program; }

    public CompiledShaderProgram program() { return program; }
    public Uniform getUniform(String name) { return program.getUniform(name); }
}
