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
import dev.veyra.shader.PbrAnimation;
import dev.veyra.shader.PbrAnimationClock;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.lwjgl.vulkan.VK10.VK_FORMAT_R8G8B8A8_UNORM;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;

/** Resource-pack normal maps for terrain atlases and entity SimpleTextures. */
public final class VeyraNormalTextures {
    public static final String SAMPLER = "VeyraNormalSampler";
    public static final int IMAGE_SLOT = 6;
    private static final long MAX_PIXELS = 64L * 1024 * 1024;
    private static final long CACHE_BYTES = 512L * 1024 * 1024;
    private static final long MAX_ANIMATION_MAP_PIXELS = 4L * 1024 * 1024;
    private static final long MAX_ANIMATION_BYTES_PER_MATERIAL = 64L * 1024 * 1024;
    private static final long ANIMATION_CACHE_BYTES = 128L * 1024 * 1024;
    private static final LinkedHashMap<GpuTexture, Material> cache = new LinkedHashMap<>(16, .75F, true);
    private static Material fallback;
    private static long bytes;
    private static long animationBytes;
    private static GpuTextureView terrainBinding;

    private record LoadedMap(NormalPixels.Image firstFrame, PbrAnimation animation) { }
    private record TextureData(NormalPixels.Image pixels, List<AnimatedRegion> animations) { }
    private record Material(VkGpuTexture texture, GpuTextureView view, long bytes, long animationBytes,
                            List<AnimatedRegion> animations, int mipLevels, int width, int height) { }

    private static final class AnimatedRegion {
        private final Identifier sprite;
        private final PbrAnimation animation;
        private final int x, y, width, height, padding;
        private final long startedAt;
        private PbrAnimation.Position position;

        private AnimatedRegion(Identifier sprite, PbrAnimation animation, int x, int y, int width, int height,
                               int padding, long startedAt) {
            this.sprite = sprite;
            this.animation = animation;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.padding = padding;
            this.startedAt = startedAt;
            this.position = animation.positionAt(0);
        }

        private long sourceBytes() { return (long) animation.pixelCount() * Integer.BYTES; }
    }

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
        TextureData data = null;
        if (base instanceof TextureAtlas atlas) {
            data = atlas(atlas, albedo);
        } else if (base instanceof SimpleTexture simple) {
            Identifier id = simple.resourceId().withPath(path -> NormalPixels.path(path, false));
            LoadedMap source = load(id);
            if (source != null) {
                NormalPixels.Image pixels = flat(albedo.getWidth(0), albedo.getHeight(0));
                NormalPixels.tile(pixels, source.firstFrame(), 0, 0, pixels.width(), pixels.height(), 0);
                List<AnimatedRegion> animations = source.animation() == null ? List.of() : List.of(
                        new AnimatedRegion(null, source.animation(), 0, 0, pixels.width(), pixels.height(), 0,
                                PbrAnimationClock.ticks()));
                data = new TextureData(pixels, animations);
            }
        }
        Material material = data == null ? missing() : upload(data.pixels(), mipLevels(data.pixels(), albedo), data.animations());
        cache.put(albedo, material);
        bytes += material == fallback ? 0 : material.bytes();
        animationBytes += material == fallback ? 0 : material.animationBytes();
        while (cache.size() > 256 || bytes > CACHE_BYTES || animationBytes > ANIMATION_CACHE_BYTES) {
            var iterator = cache.entrySet().iterator();
            Material old = iterator.next().getValue();
            iterator.remove();
            if (old != fallback) {
                bytes -= old.bytes();
                animationBytes -= old.animationBytes();
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

    private static TextureData atlas(TextureAtlas atlas, GpuTexture albedo) throws IOException {
        NormalPixels.Image result = null;
        List<AnimatedRegion> animations = new ArrayList<>();
        long animationBytes = 0;
        for (TextureAtlasSprite sprite : ((TextureAtlasAccessor) atlas).veyra$getSprites().values()) {
            Identifier id = sprite.contents().name().withPath(path -> NormalPixels.path(path, true));
            LoadedMap loaded = load(id);
            if (loaded == null) continue;
            if (result == null) result = flat(albedo.getWidth(0), albedo.getHeight(0));
            NormalPixels.tile(result, loaded.firstFrame(), sprite.getX(), sprite.getY(), sprite.contents().width(),
                    sprite.contents().height(), ((TextureAtlasSpriteAccessor) sprite).veyra$getPadding());
            if (loaded.animation() != null) {
                animationBytes += (long) loaded.animation().pixelCount() * Integer.BYTES;
                if (animationBytes > MAX_ANIMATION_BYTES_PER_MATERIAL)
                    throw new IOException("Animated normal maps in one atlas exceed the 64-megabyte animation limit.");
                animations.add(new AnimatedRegion(sprite.contents().name(), loaded.animation(), sprite.getX(),
                        sprite.getY(), sprite.contents().width(), sprite.contents().height(),
                        ((TextureAtlasSpriteAccessor) sprite).veyra$getPadding(), PbrAnimationClock.ticks()));
            }
        }
        return result == null ? null : new TextureData(result, List.copyOf(animations));
    }

    private static LoadedMap load(Identifier id) throws IOException {
        Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(id);
        if (resource.isEmpty()) return null;
        try (var stream = resource.get().open(); NativeImage image = NativeImage.read(stream)) {
            checkSize(image.getWidth(), image.getHeight());
            var metadata = resource.get().metadata().getSection(AnimationMetadataSection.TYPE);
            if (metadata.isEmpty())
                return new LoadedMap(new NormalPixels.Image(image.getWidth(), image.getHeight(), image.getPixels()), null);
            if ((long) image.getWidth() * image.getHeight() > MAX_ANIMATION_MAP_PIXELS)
                throw new IOException("Animated normal map exceeds the four-megapixel animation limit: " + id);
            PbrAnimation animation = PbrAnimation.fromSheet(image.getWidth(), image.getHeight(), image.getPixels(),
                    metadata.get());
            return new LoadedMap(new NormalPixels.Image(animation.frameWidth(), animation.frameHeight(),
                    animation.firstFramePixels()), animation.isAnimated() ? animation : null);
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
        if (fallback == null) fallback = upload(NormalPixels.flat(1, 1), 1, List.of());
        return fallback;
    }

    private static Material upload(NormalPixels.Image pixels, int mipLevels, List<AnimatedRegion> animations) {
        int textureWidth = pixels.width();
        int textureHeight = pixels.height();
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
            long animationBytes = animations.stream().mapToLong(AnimatedRegion::sourceBytes).sum();
            return new Material(texture, view, size, animationBytes, List.copyOf(animations), mipLevels,
                    textureWidth, textureHeight);
        } catch (RuntimeException | Error failure) {
            if (view != null) view.close();
            texture.close();
            throw failure;
        }
    }

    public static void tickAnimations(long tick, Map<Identifier, PbrAnimationClock.BaseFrame> spriteFrames) {
        try {
            for (Material material : cache.values()) {
                for (AnimatedRegion region : material.animations()) {
                    PbrAnimationClock.BaseFrame baseFrame = region.sprite == null ? null : spriteFrames.get(region.sprite);
                    PbrAnimation.Position position = baseFrame == null
                            ? region.animation.positionAt(Math.max(0, tick - region.startedAt))
                            : region.animation.positionForFrameSlot(baseFrame.frameSlot(), baseFrame.elapsedTicks());
                    if (!position.visuallyDiffersFrom(region.position)) continue;
                    updateRegion(material, region, position);
                    region.position = position;
                }
            }
        } catch (RuntimeException failure) {
            VeyraRenderer.materialInputFailed(failure);
        }
    }

    private static void updateRegion(Material material, AnimatedRegion region, PbrAnimation.Position position) {
        int[] framePixels = new int[Math.multiplyExact(region.animation.frameWidth(), region.animation.frameHeight())];
        region.animation.copyPixels(position, framePixels, NormalPixels::interpolate);
        NormalPixels.Image frame = new NormalPixels.Image(region.animation.frameWidth(),
                region.animation.frameHeight(), framePixels);
        NormalPixels.Image patch = NormalPixels.flat(region.width + 2 * region.padding,
                region.height + 2 * region.padding);
        NormalPixels.tile(patch, frame, 0, 0, region.width, region.height, region.padding);

        VulkanImage image = material.texture().getVulkanImage();
        for (int level = 0; level < material.mipLevels(); level++) {
            int x = (region.x - region.padding) >> level;
            int y = (region.y - region.padding) >> level;
            int levelWidth = Math.max(1, material.width() >> level);
            int levelHeight = Math.max(1, material.height() >> level);
            if (x < 0 || y < 0 || x + patch.width() > levelWidth || y + patch.height() > levelHeight)
                throw new IllegalStateException("Animated normal-map sprite patch exceeds mip level " + level + ".");
            try (NativeImage data = new NativeImage(patch.width(), patch.height(), false)) {
                for (int py = 0; py < patch.height(); py++) for (int px = 0; px < patch.width(); px++)
                    data.setPixel(px, py, patch.pixels()[py * patch.width() + px]);
                SpriteUpdateUtil.addTransitionedLayout(image);
                image.uploadSubTextureAsync(level, 0, patch.width(), patch.height(), x, y, 0, 0,
                        patch.width(), data.getPointer());
            }
            if (level + 1 < material.mipLevels()) patch = NormalPixels.mip(patch);
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
        animationBytes = 0;
        old.forEach(VeyraNormalTextures::retire);
    }

    private static void retire(Material material) {
        MemoryManager.getInstance().addFrameOp(() -> {
            material.view().close();
            material.texture().close();
        });
    }
}
