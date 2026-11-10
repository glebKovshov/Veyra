package dev.veyra.compat;

import com.mojang.blaze3d.shaders.CompiledShader;
import dev.veyra.mixin.accessor.ShaderSourceKeyAccessor;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.renderer.ShaderManager;
import net.minecraft.client.renderer.ShaderProgramConfig;
import net.minecraft.resources.ResourceLocation;

/** The shader sources and program configs that the 1.21.4 shader manager last loaded. */
public final class ShaderSources {
    private static ShaderManager.Configs configs = ShaderManager.Configs.EMPTY;
    private static final Map<ResourceLocation, String> vertex = new HashMap<>();
    private static final Map<ResourceLocation, String> fragment = new HashMap<>();

    private ShaderSources() { }

    /** Called when the shader manager applies reloaded resources. */
    public static synchronized void loaded(ShaderManager.Configs loaded) {
        configs = loaded;
        vertex.clear();
        fragment.clear();
        for (Map.Entry<?, String> entry : loaded.shaderSources().entrySet()) {
            var key = (ShaderSourceKeyAccessor) entry.getKey();
            (key.veyra$getType() == CompiledShader.Type.VERTEX ? vertex : fragment).put(key.veyra$getId(), entry.getValue());
        }
    }

    public static synchronized ShaderManager.Configs configs() { return configs; }

    public static synchronized ShaderProgramConfig program(ResourceLocation id) { return configs.programs().get(id); }

    /** The GLSL source with imports resolved, or null: the contract of the later ShaderManager.getShader. */
    public static synchronized String get(ShaderManager manager, ResourceLocation id, ShaderType type) {
        return (type == ShaderType.VERTEX ? vertex : fragment).get(id);
    }
}
