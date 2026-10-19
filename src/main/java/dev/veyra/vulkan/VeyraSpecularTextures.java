package dev.veyra.vulkan;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import dev.veyra.mixin.accessor.TextureAtlasAccessor;
import dev.veyra.mixin.accessor.TextureAtlasSpriteAccessor;
import dev.veyra.mixin.accessor.TextureManagerAccessor;
import dev.veyra.shader.SpecularPixels;
import dev.veyra.shader.VeyraRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.client.resources.metadata.animation.AnimationMetadataSection;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.vulkanmod.render.engine.VkGpuDevice;
import net.vulkanmod.render.engine.VkGpuTexture;
import net.vulkanmod.render.engine.VkRenderPass;
import net.vulkanmod.vulkan.memory.MemoryManager;
import net.vulkanmod.vulkan.shader.GraphicsPipeline;
import net.vulkanmod.vulkan.texture.SamplerManager;
import net.vulkanmod.vulkan.texture.VTextureSelector;
import net.vulkanmod.vulkan.texture.VulkanImage;
import java.io.IOException;
import java.util.*;
import static org.lwjgl.vulkan.VK10.*;

/** Resource-pack specular data with the same sprite rectangles as the live albedo atlas. */
public final class VeyraSpecularTextures {
    public static final String SAMPLER = "VeyraSpecularSampler";
    private static final long MAX_PIXELS = 64L * 1024 * 1024;
    private static final long CACHE_BYTES = 512L * 1024 * 1024;
    private static final LinkedHashMap<GpuTexture, Material> cache = new LinkedHashMap<>(16, .75F, true);
    private static Material fallback;
    private static long bytes;
    private static GpuTextureView terrainBinding;
    private record Material(VkGpuTexture texture, GpuTextureView view, long bytes) { }
    private VeyraSpecularTextures() { }

    public static void prepareTerrain() throws IOException {
        resolve(Minecraft.getInstance().getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS));
    }

    public static void bindTerrain(GraphicsPipeline pipeline) {
        var descriptor = pipeline.getImageDescriptor(SAMPLER);
        if (descriptor == null) return;
        Material material;
        try { material = resolve(Minecraft.getInstance().getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS)); }
        catch (IOException exception) { VeyraRenderer.materialInputFailed(exception); material = missing(); }
        terrainBinding = material.view();
        net.vulkanmod.vulkan.VRenderSystem.setShaderTexture(descriptor.imageIdx, material.view());
        VTextureSelector.bindTexture(descriptor.imageIdx, material.texture().getVulkanImage());
    }

    public static void bindEntity(VkRenderPass pass, GpuTextureView albedo) {
        var pipeline = net.vulkanmod.interfaces.shader.ExtendedRenderPipeline.of(pass.getPipeline()).getPipeline();
        if (pipeline == null || pipeline.getImageDescriptor(SAMPLER) == null || albedo == null) return;
        Material material = null;
        try {
            material = cache.get(albedo.texture());
            if (material == null) {
                for (AbstractTexture candidate : ((TextureManagerAccessor) Minecraft.getInstance().getTextureManager()).veyra$getTextures().values()) {
                    GpuTexture base;
                    try { base = candidate.getTexture(); } catch (IllegalStateException ignored) { continue; }
                    if (base == albedo.texture()) { material = resolve(candidate); break; }
                }
            }
        } catch (IOException exception) { VeyraRenderer.materialInputFailed(exception); }
        if (material == null) material = missing();
        var image = material.texture().getVulkanImage();
        var sampler = RenderSystem.getSamplerCache().getClampToEdge(com.mojang.blaze3d.textures.FilterMode.NEAREST);
        pass.bindTexture(SAMPLER, material.view(), sampler);
        VTextureSelector.bindTexture(pipeline.getImageDescriptor(SAMPLER).imageIdx, image);
    }

    private static Material resolve(AbstractTexture base) throws IOException {
        GpuTexture albedo = base.getTexture();
        Material existing = cache.get(albedo);
        if (existing != null) return existing;
        SpecularPixels.Image pixels = null;
        if (base instanceof TextureAtlas atlas) pixels = atlas(atlas, albedo);
        else if (base instanceof SimpleTexture simple) {
            Identifier id = simple.resourceId().withPath(path -> SpecularPixels.path(path, false));
            var source = load(id);
            if (source != null) {
                pixels = blank(albedo.getWidth(0), albedo.getHeight(0));
                SpecularPixels.tile(pixels, source, 0, 0, pixels.width(), pixels.height(), 0);
            }
        }
        Material material = pixels == null ? missing() : upload(pixels, albedo.getMipLevels(), labPbr());
        cache.put(albedo, material);
        bytes += material == fallback ? 0 : material.bytes();
        while (cache.size() > 256 || bytes > CACHE_BYTES) {
            var iterator = cache.entrySet().iterator();
            Material old = iterator.next().getValue(); iterator.remove();
            if (old != fallback) { bytes -= old.bytes(); retire(old); }
        }
        return material;
    }

    private static SpecularPixels.Image atlas(TextureAtlas atlas, GpuTexture albedo) throws IOException {
        SpecularPixels.Image result = null;
        for (var sprite : ((TextureAtlasAccessor) atlas).veyra$getSprites().values()) {
            Identifier id = sprite.contents().name().withPath(path -> SpecularPixels.path(path, true));
            var image = load(id);
            if (image == null) continue;
            if (result == null) result = blank(albedo.getWidth(0), albedo.getHeight(0));
            SpecularPixels.tile(result, image, sprite.getX(), sprite.getY(), sprite.contents().width(),
                    sprite.contents().height(), ((TextureAtlasSpriteAccessor) sprite).veyra$getPadding());
        }
        return result;
    }

    private static SpecularPixels.Image load(Identifier id) throws IOException {
        Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(id);
        if (resource.isEmpty()) return null; // Iris-compatible zero fallback only for absent data.
        try (var stream = resource.get().open(); NativeImage image = NativeImage.read(stream)) {
            checkSize(image.getWidth(), image.getHeight());
            var animation = resource.get().metadata().getSection(AnimationMetadataSection.TYPE);
            if (animation.isPresent()) throw new IOException("Animated specular material is not implemented: " + id);
            return new SpecularPixels.Image(image.getWidth(), image.getHeight(), image.getPixels());
        }
    }

    private static boolean labPbr() throws IOException {
        var resource = Minecraft.getInstance().getResourceManager().getResource(Identifier.withDefaultNamespace("optifine/texture.properties"));
        if (resource.isEmpty()) return false;
        Properties properties = new Properties();
        try (var stream = resource.get().open()) { properties.load(stream); }
        String format = properties.getProperty("format", "").strip();
        if (format.isEmpty()) return false;
        if (!format.split("/", 2)[0].equals("lab-pbr")) throw new IOException("Unsupported PBR texture format: " + format);
        return true;
    }

    private static SpecularPixels.Image blank(int width, int height) throws IOException {
        checkSize(width, height);
        return new SpecularPixels.Image(width, height, new int[width * height]);
    }
    private static void checkSize(int width, int height) throws IOException {
        if (width < 1 || height < 1 || (long) width * height > MAX_PIXELS) throw new IOException("Specular material exceeds the 64-megapixel data limit.");
    }

    private static Material missing() {
        if (fallback == null) fallback = upload(new SpecularPixels.Image(1, 1, new int[1]), 1, false);
        return fallback;
    }

    private static Material upload(SpecularPixels.Image pixels, int mipLevels, boolean labPbr) {
        var device = (VkGpuDevice) RenderSystem.getDevice();
        VulkanImage image = VulkanImage.builder(pixels.width(), pixels.height()).setName("Veyra specular material")
                .setFormat(VK_FORMAT_R8G8B8A8_UNORM).setMipLevels(mipLevels).setClamp(true).createVulkanImage();
        VkGpuTexture texture = device.gpuTextureFromVulkanImage(image);
        GpuTextureView view = null;
        long size = 0;
        try {
            view = device.createTextureView(texture);
            for (int level = 0; level < mipLevels; level++) {
                try (NativeImage data = new NativeImage(pixels.width(), pixels.height(), false)) {
                    for (int y = 0; y < pixels.height(); y++) for (int x = 0; x < pixels.width(); x++)
                        data.setPixel(x, y, pixels.pixels()[y * pixels.width() + x]);
                    device.createCommandEncoder().writeToTexture(texture, data, level, 0, 0, 0, pixels.width(), pixels.height(), 0, 0);
                }
                size += (long) pixels.width() * pixels.height() * 4;
                if (level + 1 < mipLevels) pixels = SpecularPixels.mip(pixels, labPbr);
            }
            image.setSampler(SamplerManager.getSampler(true, false, mipLevels - 1));
            return new Material(texture, view, size);
        } catch (RuntimeException | Error failure) {
            if (view != null) view.close(); texture.close(); throw failure;
        }
    }

    public static void invalidate() {
        if (terrainBinding != null && net.vulkanmod.vulkan.VRenderSystem.getShaderTexture(1) == terrainBinding)
            net.vulkanmod.vulkan.VRenderSystem.setShaderTexture(1, null);
        terrainBinding = null;
        Set<Material> old = Collections.newSetFromMap(new IdentityHashMap<>());
        old.addAll(cache.values()); if (fallback != null) old.add(fallback);
        cache.clear(); fallback = null; bytes = 0;
        old.forEach(VeyraSpecularTextures::retire);
    }
    private static void retire(Material material) {
        MemoryManager.getInstance().addFrameOp(() -> { material.view().close(); material.texture().close(); });
    }
}
