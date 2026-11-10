package dev.veyra.compat;

/**
 * Stand-in for the shader manager Minecraft added in 1.21.2. Sources are read from the resource
 * manager by {@link ShaderSources}; this only keeps the shared call sites' shape.
 */
public final class ShaderManager {
    public static final ShaderManager INSTANCE = new ShaderManager();

    private ShaderManager() { }
}
