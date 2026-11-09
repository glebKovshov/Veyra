package dev.veyra.vulkan;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/** Classpath streams also work before Fabric has mounted the dependency JAR as a filesystem. */
public final class LegacyShaderLoadUtil {
    private LegacyShaderLoadUtil() { }
    public static String resolveShaderPath(String path) { return "/assets/vulkanmod/shaders/" + path; }
    public static String loadShader(String path, String file) {
        String resource = path + "/" + file;
        try (var stream = LegacyShaderLoadUtil.class.getResourceAsStream(resource)) {
            if (stream == null) throw new IllegalArgumentException("Missing VulkanMod shader " + resource);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException failure) { throw new UncheckedIOException("Could not read " + resource, failure); }
    }
    public static JsonObject getJsonConfig(String path, String name) {
        String nested = path + "/" + name + "/" + name + ".json";
        String resource = LegacyShaderLoadUtil.class.getResource(nested) == null ? name + ".json" : name + "/" + name + ".json";
        return JsonParser.parseString(loadShader(path, resource)).getAsJsonObject();
    }
}
