package dev.veyra.compat;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.FileUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceProvider;
import org.apache.commons.io.IOUtils;

/**
 * The core shader sources and configs of Minecraft 1.21.1, read the way the 1.21.2 shader manager
 * loads them: imports resolved, configs parsed, and both refreshed when shaders reload.
 */
public final class ShaderSources {
    private static final Object MISSING = new Object();
    private static final Map<ResourceLocation, Object> programs = new HashMap<>();
    private static final Map<String, Object> sources = new HashMap<>();
    private static Object configs = new Object();

    private ShaderSources() { }

    /** Called after Minecraft reloads its core shaders. */
    public static synchronized void reloaded() {
        configs = new Object();
        programs.clear();
        sources.clear();
    }

    /** A token that changes whenever the loaded configs change. */
    public static synchronized Object configs() { return configs; }

    private static ResourceProvider resources() { return Minecraft.getInstance().getResourceManager(); }

    /** The config of core shader {@code id}, such as {@code minecraft:core/position}, or null. */
    public static synchronized ShaderProgramConfig program(ResourceLocation id) {
        Object cached = programs.get(id);
        if (cached == null) {
            ShaderProgramConfig parsed = parse(id);
            cached = parsed == null ? MISSING : parsed;
            programs.put(id, cached);
        }
        return cached == MISSING ? null : (ShaderProgramConfig) cached;
    }

    private static ShaderProgramConfig parse(ResourceLocation id) {
        ResourceLocation location = id.withPath("shaders/" + id.getPath() + ".json");
        Optional<Resource> resource = resources().getResource(location);
        if (resource.isEmpty()) return null;
        try (Reader reader = resource.get().openAsReader()) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            List<ShaderProgramConfig.Sampler> samplers = new ArrayList<>();
            for (JsonElement sampler : array(json, "samplers")) {
                samplers.add(new ShaderProgramConfig.Sampler(sampler.getAsJsonObject().get("name").getAsString()));
            }
            List<ShaderProgramConfig.Uniform> uniforms = new ArrayList<>();
            for (JsonElement element : array(json, "uniforms")) {
                JsonObject uniform = element.getAsJsonObject();
                List<Float> values = new ArrayList<>();
                for (JsonElement value : array(uniform, "values")) values.add(value.getAsFloat());
                uniforms.add(new ShaderProgramConfig.Uniform(uniform.get("name").getAsString(),
                        uniform.get("type").getAsString(), uniform.get("count").getAsInt(), values));
            }
            // Stage names are "name" or "namespace:name" under shaders/core, like the config itself.
            return new ShaderProgramConfig(ShaderProgram.configId(json.get("vertex").getAsString()),
                    ShaderProgram.configId(json.get("fragment").getAsString()), samplers, uniforms, ShaderDefines.EMPTY);
        } catch (IOException | RuntimeException failure) {
            throw new IllegalStateException("Could not read shader config " + location, failure);
        }
    }

    private static JsonArray array(JsonObject json, String name) {
        return json.has(name) ? json.getAsJsonArray(name) : new JsonArray();
    }

    /** The GLSL source with imports resolved, or null: the contract of the later ShaderManager.getShader. */
    public static synchronized String get(ShaderManager manager, ResourceLocation id, ShaderType type) {
        ResourceLocation location = id.withPath("shaders/" + id.getPath() + (type == ShaderType.VERTEX ? ".vsh" : ".fsh"));
        String key = location.toString();
        Object cached = sources.get(key);
        if (cached == null) {
            String loaded = load(location);
            // Post-effect stages lived in shaders/program until 1.21.2 moved them to shaders/post.
            if (loaded == null && id.getPath().startsWith("post/")) {
                loaded = load(location.withPath("shaders/program/" + location.getPath().substring("shaders/post/".length())));
            }
            cached = loaded == null ? MISSING : loaded;
            sources.put(key, cached);
        }
        return cached == MISSING ? null : (String) cached;
    }

    private static String load(ResourceLocation location) {
        ResourceProvider provider = resources();
        Optional<Resource> resource = provider.getResource(location);
        if (resource.isEmpty()) return null;
        String source;
        try (Reader reader = resource.get().openAsReader()) {
            source = IOUtils.toString(reader);
        } catch (IOException failure) {
            throw new IllegalStateException("Could not read shader " + location, failure);
        }
        String directory = FileUtil.getFullResourcePath(location.getPath());
        Set<String> imported = new HashSet<>();
        // The import rules of the 1.21.1 ShaderInstance: <name> from shaders/include, "name" beside the shader.
        var preprocessor = new com.mojang.blaze3d.preprocessor.GlslPreprocessor() {
            @Override public String applyImport(boolean relative, String path) {
                String full = FileUtil.normalizeResourcePath((relative ? directory : "shaders/include/") + path);
                if (!imported.add(full)) return null;
                ResourceLocation include = full.indexOf(':') < 0
                        ? location.withPath(full) : ResourceLocation.parse(full);
                try (Reader reader = provider.openAsReader(include)) {
                    return IOUtils.toString(reader);
                } catch (IOException failure) {
                    return "#error " + failure.getMessage();
                }
            }
        };
        return String.join("", preprocessor.process(source));
    }
}
