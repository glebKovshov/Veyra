package dev.veyra.vulkan;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.veyra.VeyraClient;
import dev.veyra.shader.ShaderPack;
import dev.veyra.shader.ShaderPackCustomTextureProperties;
import net.vulkanmod.render.engine.VkGpuDevice;
import net.vulkanmod.render.engine.VkGpuTexture;
import net.vulkanmod.render.engine.VkRenderPass;
import net.vulkanmod.render.engine.VkTextureView;
import net.vulkanmod.vulkan.memory.MemoryManager;
import net.vulkanmod.vulkan.shader.GraphicsPipeline;
import net.vulkanmod.vulkan.texture.VulkanImage;
import org.lwjgl.vulkan.VK10;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.OptionalDouble;

/** Uploads and binds shader-pack PNG inputs declared through customTexture.* or texture.<stage>.*. */
public final class VeyraShaderPackTextures {
    private static final Map<Key, UploadedTexture> TEXTURES = new HashMap<>();

    private VeyraShaderPackTextures() {
    }

    /** Load pack-local texture assets before its pipelines can become active. */
    public static synchronized void prepare(ShaderPack pack) throws IOException {
        Path packPath = pack.path().toAbsolutePath().normalize();
        ShaderPackCustomTextureProperties properties = pack.readCustomTextureProperties();
        for (String resourcePath : properties.allTexturePaths()) {
            if (ShaderPackCustomTextureProperties.isMinecraftBlockAtlas(resourcePath)
                    || resourcePath.startsWith("minecraft:")) {
                continue;
            }
            String normalizedPath = normalizeResourcePath(resourcePath);
            byte[] bytes = pack.readShaderAsset(normalizedPath).orElseThrow(
                    () -> new IOException("Shader-pack texture '" + normalizedPath + "' was not found."));
            FilterSettings settings = readFilterSettings(pack, normalizedPath);
            String fingerprint = sha256(bytes) + ":" + settings.blur() + ":" + settings.clamp();
            Key key = new Key(packPath, normalizedPath);
            UploadedTexture current = TEXTURES.get(key);
            if (current != null && current.fingerprint().equals(fingerprint)
                    && !current.texture().isClosed() && !current.view().isClosed()) {
                continue;
            }
            UploadedTexture replacement = upload(normalizedPath, fingerprint, bytes, settings);
            TEXTURES.put(key, replacement);
            if (current != null) retireAfterFrame(current);
        }
    }

    /** Prepare textures lazily restored packs may need before their first pipeline is bound. */
    public static synchronized void ensurePrepared(ShaderPack pack) throws IOException {
        Path packPath = pack.path().toAbsolutePath().normalize();
        for (String resourcePath : pack.readCustomTextureProperties().allTexturePaths()) {
            if (ShaderPackCustomTextureProperties.isMinecraftBlockAtlas(resourcePath)
                    || resourcePath.startsWith("minecraft:")) {
                continue;
            }
            Key key = new Key(packPath, normalizeResourcePath(resourcePath));
            UploadedTexture current = TEXTURES.get(key);
            if (current == null || current.texture().isClosed() || current.view().isClosed()) {
                prepare(pack);
                return;
            }
        }
    }

    public static synchronized void bind(VkRenderPass pass, GraphicsPipeline pipeline,
                                         ShaderPack pack, String fragmentShaderPath) throws IOException {
        String programName = ShaderPackCustomTextureProperties
                .programNameForFragmentShaderPath(fragmentShaderPath);
        if (programName == null) return;
        ShaderPackCustomTextureProperties properties = pack.readCustomTextureProperties();
        for (Map.Entry<String, String> mapping : properties.texturesForProgram(programName).entrySet()) {
            String samplerName = mapping.getKey();
            String bindingName = samplerName;
            if (pipeline.getImageDescriptor(bindingName) == null) {
                bindingName = adaptedSamplerName(programName, samplerName);
            }
            if (pipeline.getImageDescriptor(bindingName) == null) continue;
            String resourcePath = mapping.getValue();
            if (ShaderPackCustomTextureProperties.isMinecraftBlockAtlas(resourcePath)) {
                VeyraBlockAtlasSampler.bind(pass, pipeline, bindingName);
                continue;
            }
            if (resourcePath.startsWith("minecraft:")) {
                throw new IOException("Shader-pack texture '" + resourcePath + "' for " + samplerName
                        + " is not supported by this Vulkan backend.");
            }
            String normalizedPath = normalizeResourcePath(resourcePath);
            Key key = new Key(pack.path().toAbsolutePath().normalize(), normalizedPath);
            UploadedTexture uploaded = TEXTURES.get(key);
            if (uploaded == null || uploaded.texture().isClosed() || uploaded.view().isClosed()) {
                throw new IOException("Shader-pack texture '" + normalizedPath + "' was not prepared.");
            }
            pass.bindTexture(bindingName, uploaded.view(), uploaded.sampler());
        }
    }

    static String adaptedSamplerName(String programName, String samplerName) {
        if (!isPostProcessingProgram(programName)) return samplerName;
        if (samplerName.equals("colortex0")) return "InSampler";
        if (!samplerName.matches("colortex(?:[1-9]|1[0-5])")) return samplerName;
        return "Veyra" + Character.toUpperCase(samplerName.charAt(0)) + samplerName.substring(1) + "Sampler";
    }

    private static boolean isPostProcessingProgram(String programName) {
        return isProgramInStage(programName, "begin") || isProgramInStage(programName, "prepare")
                || isProgramInStage(programName, "deferred") || isProgramInStage(programName, "composite")
                || isProgramInStage(programName, "final");
    }

    private static boolean isProgramInStage(String programName, String stage) {
        return programName.equals(stage) || programName.startsWith(stage + "_")
                || programName.matches(stage + "[0-9]+.*");
    }

    /** Retire textures from packs that are no longer selected after the replacement pipelines are installed. */
    public static synchronized void retainOnly(Path selectedPack) {
        Path keep = selectedPack == null ? null : selectedPack.toAbsolutePath().normalize();
        var iterator = TEXTURES.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Key, UploadedTexture> entry = iterator.next();
            if (!entry.getKey().packPath().equals(keep)) {
                retireAfterFrame(entry.getValue());
                iterator.remove();
            }
        }
    }

    public static synchronized void discard(Path packPath) {
        Path discard = packPath.toAbsolutePath().normalize();
        var iterator = TEXTURES.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Key, UploadedTexture> entry = iterator.next();
            if (entry.getKey().packPath().equals(discard)) {
                retireAfterFrame(entry.getValue());
                iterator.remove();
            }
        }
    }

    public static synchronized void closeAll() {
        TEXTURES.values().forEach(VeyraShaderPackTextures::retire);
        TEXTURES.clear();
    }

    private static UploadedTexture upload(String resourcePath, String fingerprint, byte[] bytes,
                                          FilterSettings settings) throws IOException {
        if (!(RenderSystem.getDevice() instanceof VkGpuDevice device)) {
            throw new IOException("Shader-pack textures require VulkanMod's Vulkan device.");
        }
        try (NativeImage pixels = NativeImage.read(new ByteArrayInputStream(bytes))) {
            if (pixels.getWidth() > device.getMaxTextureSize() || pixels.getHeight() > device.getMaxTextureSize()) {
                throw new IOException("Shader-pack texture '" + resourcePath + "' exceeds VulkanMod's device limit of "
                        + device.getMaxTextureSize() + ".");
            }
            FilterMode filter = settings.blur() ? FilterMode.LINEAR : FilterMode.NEAREST;
            AddressMode address = settings.clamp() ? AddressMode.CLAMP_TO_EDGE : AddressMode.REPEAT;
            GpuSampler sampler = device.createSampler(address, address, filter, filter, 1, OptionalDouble.empty());
            VulkanImage image = null;
            VkGpuTexture texture = null;
            VkTextureView view = null;
            try {
                image = VulkanImage.builder(pixels.getWidth(), pixels.getHeight())
                        .setName("Veyra shader-pack texture " + resourcePath)
                        .setFormat(VK10.VK_FORMAT_R8G8B8A8_UNORM)
                        .setLinearFiltering(settings.blur())
                        .setClamp(settings.clamp())
                        .createVulkanImage();
                texture = device.gpuTextureFromVulkanImage(image);
                image = null;
                view = (VkTextureView) device.createTextureView(texture);
                device.createCommandEncoder().writeToTexture(texture, pixels);
                return new UploadedTexture(fingerprint, texture, view, sampler);
            } catch (RuntimeException | Error failure) {
                closeAfterFailure(view, failure);
                closeAfterFailure(texture, failure);
                if (image != null) {
                    try {
                        image.free();
                    } catch (RuntimeException | Error cleanupFailure) {
                        failure.addSuppressed(cleanupFailure);
                    }
                }
                closeAfterFailure(sampler, failure);
                throw failure;
            }
        }
    }

    private static String normalizeResourcePath(String path) throws IOException {
        String normalized = path.strip().replace('\\', '/');
        while (normalized.startsWith("/")) normalized = normalized.substring(1);
        if (normalized.isEmpty() || normalized.contains(":")
                || java.util.Arrays.asList(normalized.split("/", -1)).contains("..")) {
            throw new IOException("Invalid shader-pack texture path: " + path);
        }
        return normalized;
    }

    private static String sha256(byte[] bytes) throws IOException {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte value : digest) result.append(String.format("%02x", value));
            return result.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IOException("SHA-256 is unavailable for shader-pack texture verification.", impossible);
        }
    }

    private static FilterSettings readFilterSettings(ShaderPack pack, String resourcePath) throws IOException {
        var metadata = pack.readShaderAsset(resourcePath + ".mcmeta");
        if (metadata.isEmpty()) return new FilterSettings(false, false);
        try {
            JsonObject root = JsonParser.parseString(new String(metadata.get(), java.nio.charset.StandardCharsets.UTF_8))
                    .getAsJsonObject();
            JsonObject texture = root.has("texture") && root.get("texture").isJsonObject()
                    ? root.getAsJsonObject("texture") : new JsonObject();
            boolean blur = texture.has("blur") && texture.get("blur").getAsBoolean();
            boolean clamp = texture.has("clamp") && texture.get("clamp").getAsBoolean();
            return new FilterSettings(blur, clamp);
        } catch (RuntimeException exception) {
            VeyraClient.LOGGER.warn("Ignoring invalid shader-pack texture metadata for '{}': {}",
                    resourcePath, exception.getMessage());
            return new FilterSettings(false, false);
        }
    }

    private static void retireAfterFrame(UploadedTexture texture) {
        MemoryManager.getInstance().addFrameOp(() -> retire(texture));
    }

    private static void retire(UploadedTexture texture) {
        if (texture.view() != null && !texture.view().isClosed()) texture.view().close();
        if (texture.texture() != null && !texture.texture().isClosed()) texture.texture().close();
        if (texture.sampler() != null) texture.sampler().close();
    }

    private static void closeAfterFailure(AutoCloseable resource, Throwable failure) {
        if (resource == null) return;
        try {
            resource.close();
        } catch (Exception cleanupFailure) {
            failure.addSuppressed(cleanupFailure);
        }
    }

    private record Key(Path packPath, String resourcePath) { }

    private record FilterSettings(boolean blur, boolean clamp) { }

    private record UploadedTexture(String fingerprint, VkGpuTexture texture, VkTextureView view, GpuSampler sampler) { }
}
