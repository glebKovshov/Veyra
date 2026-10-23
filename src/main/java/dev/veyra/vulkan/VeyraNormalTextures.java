package dev.veyra.vulkan;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.GpuSampler;
import dev.veyra.mixin.accessor.TextureAtlasAccessor;
import dev.veyra.mixin.accessor.TextureAtlasSpriteAccessor;
import dev.veyra.mixin.accessor.TextureManagerAccessor;
import dev.veyra.shader.NormalPixels;
import dev.veyra.shader.VeyraRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.SimpleTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.metadata.animation.AnimationMetadataSection;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.vulkanmod.render.engine.VkGpuDevice;
import net.vulkanmod.render.engine.VkGpuTexture;
import net.vulkanmod.render.engine.VkRenderPass;
import net.vulkanmod.render.texture.SpriteUpdateUtil;
import net.vulkanmod.vulkan.memory.MemoryManager;
import net.vulkanmod.vulkan.shader.GraphicsPipeline;
import net.vulkanmod.vulkan.texture.SamplerManager;
import net.vulkanmod.vulkan.texture.VTextureSelector;
import net.vulkanmod.vulkan.texture.VulkanImage;

import java.io.IOException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Optional;
import java.util.Set;

import static org.lwjgl.vulkan.VK10.VK_FORMAT_R8G8B8A8_UNORM;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;

/** Static resource-pack normal maps for terrain atlases and entity SimpleTextures. */
public final class VeyraNormalTextures {
    public static final String SAMPLER = "VeyraNormalSampler";
    public static final int IMAGE_SLOT = 6;
    private static final long MAX_PIXELS = 64L * 1024 * 1024;
    private static final long CACHE_BYTES = 512L * 1024 * 1024;
    private static final LinkedHashMap<GpuTexture, Material> cache = new LinkedHashMap<>(16, .75F, true);
    private static Material fallback;
    private static long bytes;
    private static GpuTextureView terrainBinding;

    private record Material(VkGpuTexture texture, GpuTextureView view, long bytes) { }

    private VeyraNormalTextures() { }

    public static void prepareTerrain() throws IOException {
        resolve(Minecraft.getInstance().getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS));
    }

    public static void bindTerrain(GraphicsPipeline pipeline) {
        var descriptor = pipeline.getImageDescriptor(SAMPLER);
        if (descriptor == null) return;
        Material material;
        try {
            material = resolve(Minecraft.getInstance().getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS));
        } catch (IOException exception) {
            VeyraRenderer.materialInputFailed(exception);
            material = missing();
        }
        terrainBinding = material.view();
        net.vulkanmod.vulkan.VRenderSystem.setShaderTexture(descriptor.imageIdx, material.view());
        VTextureSelector.bindTexture(descriptor.imageIdx, material.texture().getVulkanImage());
        descriptor.setLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
    }

    /** Bind the static _n.png paired with the entity's live albedo texture. */
    public static void bindEntity(VkRenderPass pass, GpuTextureView albedo, GpuSampler sampler) {
        if (albedo == null || pass.getPipeline() == null) return;
        GraphicsPipeline pipeline = net.vulkanmod.interfaces.shader.ExtendedRenderPipeline
                .of(pass.getPipeline()).getPipeline();
        var descriptor = pipeline == null ? null : pipeline.getImageDescriptor(SAMPLER);
        if (descriptor == null) return;
        Material material = null;
        try {
            material = cache.get(albedo.texture());
            if (material == null) {
                for (AbstractTexture candidate : ((TextureManagerAccessor)
                        Minecraft.getInstance().getTextureManager()).veyra$getTextures().values()) {
                    GpuTexture base;
                    try {
                        base = candidate.getTexture();
                    } catch (IllegalStateException ignored) {
                        continue;
                    }
                    if (base == albedo.texture()) {
                        material = resolve(candidate);
                        break;
                    }
                }
            }
        } catch (IOException exception) {
            VeyraRenderer.materialInputFailed(exception);
        }
        if (material == null) material = missing();
        if (descriptor instanceof VeyraEntityNormalDescriptor entityDescriptor) {
            entityDescriptor.bind(material.texture().getVulkanImage());
        }
        descriptor.setLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
        descriptor.isReadOnlyLayout = false;
        pass.bindTexture(SAMPLER, material.view(), sampler);
        VTextureSelector.bindTexture(descriptor.imageIdx, material.texture().getVulkanImage());
    }

    private static Material resolve(AbstractTexture base) throws IOException {
        GpuTexture albedo = base.getTexture();
        Material existing = cache.get(albedo);
        if (existing != null) return existing;
        NormalPixels.Image pixels = null;
        if (base instanceof TextureAtlas atlas) {
            pixels = atlas(atlas, albedo);
        } else if (base instanceof SimpleTexture simple) {
            Identifier id = simple.resourceId().withPath(path -> NormalPixels.path(path, false));
            NormalPixels.Image source = load(id);
            if (source != null) {
                pixels = flat(albedo.getWidth(0), albedo.getHeight(0));
                NormalPixels.tile(pixels, source, 0, 0, pixels.width(), pixels.height(), 0);
            }
        }
        Material material = pixels == null ? missing() : upload(pixels, mipLevels(pixels, albedo));
        cache.put(albedo, material);
        bytes += material == fallback ? 0 : material.bytes();
        while (cache.size() > 256 || bytes > CACHE_BYTES) {
            var iterator = cache.entrySet().iterator();
            Material old = iterator.next().getValue();
            iterator.remove();
            if (old != fallback) {
                bytes -= old.bytes();
                retire(old);
            }
        }
        return material;
    }

    private static int mipLevels(NormalPixels.Image pixels, GpuTexture albedo) {
        int maximum = Math.max(pixels.width(), pixels.height());
        int imageLevels = 32 - Integer.numberOfLeadingZeros(maximum);
        return Math.max(1, Math.min(Math.max(1, albedo.getMipLevels()), imageLevels));
    }

    private static NormalPixels.Image atlas(TextureAtlas atlas, GpuTexture albedo) throws IOException {
        NormalPixels.Image result = null;
        for (TextureAtlasSprite sprite : ((TextureAtlasAccessor) atlas).veyra$getSprites().values()) {
            Identifier id = sprite.contents().name().withPath(path -> NormalPixels.path(path, true));
            NormalPixels.Image image = load(id);
            if (image == null) continue;
            if (result == null) result = flat(albedo.getWidth(0), albedo.getHeight(0));
            NormalPixels.tile(result, image, sprite.getX(), sprite.getY(), sprite.contents().width(),
                    sprite.contents().height(), ((TextureAtlasSpriteAccessor) sprite).veyra$getPadding());
        }
        return result;
    }

    private static NormalPixels.Image load(Identifier id) throws IOException {
        Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(id);
        if (resource.isEmpty()) return null;
        try (var stream = resource.get().open(); NativeImage image = NativeImage.read(stream)) {
            checkSize(image.getWidth(), image.getHeight());
            if (resource.get().metadata().getSection(AnimationMetadataSection.TYPE).isPresent())
                throw new IOException("Animated normal maps are not implemented: " + id);
            return new NormalPixels.Image(image.getWidth(), image.getHeight(), image.getPixels());
        }
    }

    private static NormalPixels.Image flat(int width, int height) throws IOException {
        checkSize(width, height);
        return NormalPixels.flat(width, height);
    }

    private static void checkSize(int width, int height) throws IOException {
        if (width < 1 || height < 1 || (long) width * height > MAX_PIXELS)
            throw new IOException("Normal map exceeds the 64-megapixel data limit.");
    }

    private static Material missing() {
        if (fallback == null) fallback = upload(NormalPixels.flat(1, 1), 1);
        return fallback;
    }

    private static Material upload(NormalPixels.Image pixels, int mipLevels) {
        var device = (VkGpuDevice) RenderSystem.getDevice();
        VulkanImage image = VulkanImage.builder(pixels.width(), pixels.height()).setName("Veyra normal map")
                .setFormat(VK_FORMAT_R8G8B8A8_UNORM).setMipLevels(mipLevels).setClamp(true).createVulkanImage();
        VkGpuTexture texture = device.gpuTextureFromVulkanImage(image);
        GpuTextureView view = null;
        long size = 0;
        try {
            view = device.createTextureView(texture);
            for (int level = 0; level < mipLevels; level++) {
                try (NativeImage data = new NativeImage(pixels.width(), pixels.height(), false)) {
                    for (int y = 0; y < pixels.height(); y++) {
                        for (int x = 0; x < pixels.width(); x++)
                            data.setPixel(x, y, pixels.pixels()[y * pixels.width() + x]);
                    }
                    SpriteUpdateUtil.addTransitionedLayout(image);
                    image.uploadSubTextureAsync(level, 0, pixels.width(), pixels.height(), 0, 0, 0, 0,
                            pixels.width(), data.getPointer());
                }
                size += (long) pixels.width() * pixels.height() * 4;
                if (level + 1 < mipLevels) pixels = NormalPixels.mip(pixels);
            }
            image.setSampler(SamplerManager.getSampler(true, false, mipLevels - 1));
            return new Material(texture, view, size);
        } catch (RuntimeException | Error failure) {
            if (view != null) view.close();
            texture.close();
            throw failure;
        }
    }

    public static void invalidate() {
        if (terrainBinding != null && net.vulkanmod.vulkan.VRenderSystem.getShaderTexture(IMAGE_SLOT) == terrainBinding)
            net.vulkanmod.vulkan.VRenderSystem.setShaderTexture(IMAGE_SLOT, null);
        terrainBinding = null;
        Set<Material> old = Collections.newSetFromMap(new IdentityHashMap<>());
        old.addAll(cache.values());
        if (fallback != null) old.add(fallback);
        cache.clear();
        fallback = null;
        bytes = 0;
        old.forEach(VeyraNormalTextures::retire);
    }

    private static void retire(Material material) {
        MemoryManager.getInstance().addFrameOp(() -> {
            material.view().close();
            material.texture().close();
        });
    }
}
