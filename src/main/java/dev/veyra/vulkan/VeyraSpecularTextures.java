package dev.veyra.vulkan;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import dev.veyra.mixin.accessor.TextureAtlasAccessor;
import dev.veyra.mixin.accessor.TextureAtlasSpriteAccessor;
import dev.veyra.mixin.accessor.TextureManagerAccessor;
import dev.veyra.shader.SpecularPixels;
import dev.veyra.shader.PbrAnimation;
import dev.veyra.shader.PbrAnimationClock;
import dev.veyra.shader.VeyraRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.*;
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
import java.util.*;
import static org.lwjgl.vulkan.VK10.*;

/** Resource-pack specular data with the same sprite rectangles as the live albedo atlas. */
public final class VeyraSpecularTextures {
    public static final String SAMPLER = "VeyraSpecularSampler";
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
    private record LoadedMap(SpecularPixels.Image firstFrame, PbrAnimation animation) { }
    private record TextureData(SpecularPixels.Image pixels, List<AnimatedRegion> animations) { }
    private record Material(VkGpuTexture texture, GpuTextureView view, long bytes, long animationBytes,
                            List<AnimatedRegion> animations, int mipLevels, int width, int height,
                            boolean labPbr) { }

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
        TextureData data = null;
        if (base instanceof TextureAtlas atlas) data = atlas(atlas, albedo);
        else if (base instanceof SimpleTexture simple) {
            Identifier id = simple.resourceId().withPath(path -> SpecularPixels.path(path, false));
            LoadedMap source = load(id);
            if (source != null) {
                SpecularPixels.Image pixels = blank(albedo.getWidth(0), albedo.getHeight(0));
                SpecularPixels.tile(pixels, source.firstFrame(), 0, 0, pixels.width(), pixels.height(), 0);
                List<AnimatedRegion> animations = source.animation() == null ? List.of() : List.of(
                        new AnimatedRegion(null, source.animation(), 0, 0, pixels.width(), pixels.height(), 0,
                                PbrAnimationClock.ticks()));
                data = new TextureData(pixels, animations);
            }
        }
        Material material = data == null ? missing() : upload(data.pixels(), albedo.getMipLevels(), labPbr(), data.animations());
        cache.put(albedo, material);
        bytes += material == fallback ? 0 : material.bytes();
        animationBytes += material == fallback ? 0 : material.animationBytes();
        while (cache.size() > 256 || bytes > CACHE_BYTES || animationBytes > ANIMATION_CACHE_BYTES) {
            var iterator = cache.entrySet().iterator();
            Material old = iterator.next().getValue(); iterator.remove();
            if (old != fallback) { bytes -= old.bytes(); animationBytes -= old.animationBytes(); retire(old); }
        }
        return material;
    }

    private static TextureData atlas(TextureAtlas atlas, GpuTexture albedo) throws IOException {
        SpecularPixels.Image result = null;
        List<AnimatedRegion> animations = new ArrayList<>();
        long animationBytes = 0;
        for (var sprite : ((TextureAtlasAccessor) atlas).veyra$getSprites().values()) {
            Identifier id = sprite.contents().name().withPath(path -> SpecularPixels.path(path, true));
            LoadedMap loaded = load(id);
            if (loaded == null) continue;
            if (result == null) result = blank(albedo.getWidth(0), albedo.getHeight(0));
            SpecularPixels.tile(result, loaded.firstFrame(), sprite.getX(), sprite.getY(), sprite.contents().width(),
                    sprite.contents().height(), ((TextureAtlasSpriteAccessor) sprite).veyra$getPadding());
            if (loaded.animation() != null) {
                animationBytes += (long) loaded.animation().pixelCount() * Integer.BYTES;
                if (animationBytes > MAX_ANIMATION_BYTES_PER_MATERIAL)
                    throw new IOException("Animated specular maps in one atlas exceed the 64-megabyte animation limit.");
                animations.add(new AnimatedRegion(sprite.contents().name(), loaded.animation(), sprite.getX(),
                        sprite.getY(), sprite.contents().width(), sprite.contents().height(),
                        ((TextureAtlasSpriteAccessor) sprite).veyra$getPadding(), PbrAnimationClock.ticks()));
            }
        }
        return result == null ? null : new TextureData(result, List.copyOf(animations));
    }

    private static LoadedMap load(Identifier id) throws IOException {
        Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(id);
        if (resource.isEmpty()) return null; // Iris-compatible zero fallback only for absent data.
        try (var stream = resource.get().open(); NativeImage image = NativeImage.read(stream)) {
            checkSize(image.getWidth(), image.getHeight());
            var animation = resource.get().metadata().getSection(AnimationMetadataSection.TYPE);
            if (animation.isEmpty())
                return new LoadedMap(new SpecularPixels.Image(image.getWidth(), image.getHeight(), image.getPixels()), null);
            if ((long) image.getWidth() * image.getHeight() > MAX_ANIMATION_MAP_PIXELS)
                throw new IOException("Animated specular map exceeds the four-megapixel animation limit: " + id);
            PbrAnimation animated = PbrAnimation.fromSheet(image.getWidth(), image.getHeight(), image.getPixels(),
                    animation.get());
            return new LoadedMap(new SpecularPixels.Image(animated.frameWidth(), animated.frameHeight(),
                    animated.firstFramePixels()), animated.isAnimated() ? animated : null);
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
        if (fallback == null) fallback = upload(new SpecularPixels.Image(1, 1, new int[1]), 1, false, List.of());
        return fallback;
    }

    private static Material upload(SpecularPixels.Image pixels, int mipLevels, boolean labPbr,
                                   List<AnimatedRegion> animations) {
        int textureWidth = pixels.width();
        int textureHeight = pixels.height();
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
                    // Match VulkanMod's upload queue/layout tracking without its encoder's
                    // global slot-0 mutation: entity materials can be created during a draw.
                    SpriteUpdateUtil.addTransitionedLayout(image);
                    image.uploadSubTextureAsync(level, 0, pixels.width(), pixels.height(), 0, 0, 0, 0, pixels.width(), data.getPointer());
                }
                size += (long) pixels.width() * pixels.height() * 4;
                if (level + 1 < mipLevels) pixels = SpecularPixels.mip(pixels, labPbr);
            }
            image.setSampler(SamplerManager.getSampler(true, false, mipLevels - 1));
            long animationBytes = animations.stream().mapToLong(AnimatedRegion::sourceBytes).sum();
            return new Material(texture, view, size, animationBytes, List.copyOf(animations), mipLevels,
                    textureWidth, textureHeight, labPbr);
        } catch (RuntimeException | Error failure) {
            if (view != null) view.close(); texture.close(); throw failure;
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
        } catch (Exception failure) {
            VeyraRenderer.materialInputFailed(failure);
        }
    }

    private static void updateRegion(Material material, AnimatedRegion region, PbrAnimation.Position position)
            throws IOException {
        int[] framePixels = new int[Math.multiplyExact(region.animation.frameWidth(), region.animation.frameHeight())];
        region.animation.copyPixels(position, framePixels, SpecularPixels::interpolate);
        SpecularPixels.Image frame = new SpecularPixels.Image(region.animation.frameWidth(),
                region.animation.frameHeight(), framePixels);
        SpecularPixels.Image patch = blank(region.width + 2 * region.padding,
                region.height + 2 * region.padding);
        SpecularPixels.tile(patch, frame, 0, 0, region.width, region.height, region.padding);

        VulkanImage image = material.texture().getVulkanImage();
        for (int level = 0; level < material.mipLevels(); level++) {
            int x = (region.x - region.padding) >> level;
            int y = (region.y - region.padding) >> level;
            int levelWidth = Math.max(1, material.width() >> level);
            int levelHeight = Math.max(1, material.height() >> level);
            if (x < 0 || y < 0 || x + patch.width() > levelWidth || y + patch.height() > levelHeight)
                throw new IllegalStateException("Animated specular-map sprite patch exceeds mip level " + level + ".");
            try (NativeImage data = new NativeImage(patch.width(), patch.height(), false)) {
                for (int py = 0; py < patch.height(); py++) for (int px = 0; px < patch.width(); px++)
                    data.setPixel(px, py, patch.pixels()[py * patch.width() + px]);
                SpriteUpdateUtil.addTransitionedLayout(image);
                image.uploadSubTextureAsync(level, 0, patch.width(), patch.height(), x, y, 0, 0,
                        patch.width(), data.getPointer());
            }
            if (level + 1 < material.mipLevels()) patch = SpecularPixels.mip(patch, material.labPbr());
        }
    }

    public static void invalidate() {
        if (terrainBinding != null && net.vulkanmod.vulkan.VRenderSystem.getShaderTexture(1) == terrainBinding)
            net.vulkanmod.vulkan.VRenderSystem.setShaderTexture(1, null);
        terrainBinding = null;
        Set<Material> old = Collections.newSetFromMap(new IdentityHashMap<>());
        old.addAll(cache.values()); if (fallback != null) old.add(fallback);
        cache.clear(); fallback = null; bytes = 0; animationBytes = 0;
        old.forEach(VeyraSpecularTextures::retire);
    }
    private static void retire(Material material) {
        MemoryManager.getInstance().addFrameOp(() -> { material.view().close(); material.texture().close(); });
    }
}
