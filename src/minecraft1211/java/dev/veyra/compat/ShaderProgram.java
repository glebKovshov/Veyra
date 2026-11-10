package dev.veyra.compat;

import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.function.Supplier;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;

/**
 * Stand-in for the shader program key Minecraft added in 1.21.2: the program config id, vertex
 * format and defines. On 1.21.1 a core shader is a {@link ShaderInstance} named after its config.
 */
public record ShaderProgram(ResourceLocation configId, VertexFormat vertexFormat, ShaderDefines defines) {
    /** The program of a core shader called {@code name}, such as {@code position}. */
    public static ShaderProgram core(String name, VertexFormat format) {
        return new ShaderProgram(configId(name), format, ShaderDefines.EMPTY);
    }

    /** The program a loaded shader instance was made from, or null when it is not loaded. */
    public static ShaderProgram of(ShaderInstance shader) {
        return shader == null ? null
                : new ShaderProgram(configId(shader.getName()), shader.getVertexFormat(), ShaderDefines.EMPTY);
    }

    public static ShaderProgram of(Supplier<ShaderInstance> shader) { return shader == null ? null : of(shader.get()); }

    // A 1.21.1 shader name is "name" or "namespace:name"; its config is shaders/core/<name>.json.
    static ResourceLocation configId(String name) {
        int colon = name.indexOf(':');
        return colon < 0 ? ResourceLocation.withDefaultNamespace("core/" + name)
                : ResourceLocation.fromNamespaceAndPath(name.substring(0, colon), "core/" + name.substring(colon + 1));
    }
}
