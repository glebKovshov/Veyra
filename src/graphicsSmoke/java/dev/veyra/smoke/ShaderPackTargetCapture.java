package dev.veyra.smoke;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import dev.veyra.shader.VeyraRenderer;
import net.vulkanmod.render.engine.VkGpuTexture;
import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.memory.MemoryTypes;
import net.vulkanmod.vulkan.memory.buffer.Buffer;
import net.vulkanmod.vulkan.texture.VulkanImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VkBufferImageCopy;
import org.lwjgl.vulkan.VkMemoryBarrier;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.lwjgl.vulkan.VK10.*;

/** Optional frame-end readback of both sides of each shader-pack colortex target. */
final class ShaderPackTargetCapture {
    private ShaderPackTargetCapture() { }

    static List<Capture> record() throws Exception {
        if (!Renderer.isRecording() || Renderer.getCommandBuffer() == null) {
            throw new IllegalStateException("Shader-pack target capture requires an active Vulkan frame.");
        }
        Map<Integer, ?> targets = staticField("colorTargets");
        Map<Integer, ?> currentTargets = staticField("currentColorTargets");
        List<Capture> captures = new ArrayList<>(targets.size() * 2);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var commands = Renderer.getCommandBuffer();
            for (var entry : targets.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
                int index = entry.getKey();
                Object pair = entry.getValue();
                for (String side : List.of("A", "B")) {
                    Field targetField = pair.getClass().getDeclaredField(side.equals("A") ? "targetA" : "targetB");
                    targetField.setAccessible(true);
                    RenderTarget target = (RenderTarget) targetField.get(pair);
                    Object current = currentTargets.get(index);
                    boolean currentSide = current != null && current.toString().endsWith("colortex" + index + "_" + side.toLowerCase());
                    captures.add(capture(stack, commands, "colortex" + index + "-" + side, target, currentSide));
                }
            }
            for (String side : List.of("A", "B")) {
                Field swapField = VeyraRenderer.class.getDeclaredField("swapTarget" + side);
                swapField.setAccessible(true);
                RenderTarget target = (RenderTarget) swapField.get(null);
                captures.add(capture(stack, commands, "swap" + side, target, false));
            }
            var barriers = VkMemoryBarrier.calloc(1, stack);
            barriers.get(0).sType$Default()
                    .srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT).dstAccessMask(VK_ACCESS_HOST_READ_BIT);
            vkCmdPipelineBarrier(commands, VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_HOST_BIT,
                    0, barriers, null, null);
        } catch (Exception | Error failure) {
            captures.forEach(Capture::close);
            throw failure;
        }
        return List.copyOf(captures);
    }

    private static Capture capture(MemoryStack stack, org.lwjgl.vulkan.VkCommandBuffer commands,
                                   String targetName, RenderTarget target, boolean current) throws IOException {
        if (!(target.getColorTexture() instanceof VkGpuTexture texture)) {
            throw new IOException("Veyra " + targetName + " is not a Vulkan texture.");
        }
        VulkanImage image = texture.getVulkanImage();
        if ((image.usage & VK_IMAGE_USAGE_TRANSFER_SRC_BIT) == 0) {
            throw new IOException("Veyra " + targetName + " cannot be read back.");
        }
        int bytesPerPixel = bytesPerPixel(image.format);
        long size = Math.multiplyExact(Math.multiplyExact((long) image.width, image.height), bytesPerPixel);
        Buffer readback = new Buffer("Veyra " + targetName + " smoke evidence",
                VK_BUFFER_USAGE_TRANSFER_DST_BIT, MemoryTypes.HOST_MEM);
        readback.createBuffer(size);
        int layout = image.getCurrentLayout();
        image.transitionImageLayout(stack, commands, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL);
        var regions = VkBufferImageCopy.calloc(1, stack);
        var region = regions.get(0).bufferOffset(0);
        region.imageSubresource().aspectMask(VK_IMAGE_ASPECT_COLOR_BIT).layerCount(1);
        region.imageExtent().set(image.width, image.height, 1);
        vkCmdCopyImageToBuffer(commands, image.getId(), VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                readback.getId(), regions);
        image.transitionImageLayout(stack, commands, layout);
        LoggerFactory.getLogger("veyra-graphics-smoke").info(
                "Shader-pack target evidence: target={} current={} size={}x{} format={} layout={}",
                targetName, current, image.width, image.height, image.format, layout);
        return new Capture(targetName, image.width, image.height, image.format, readback, current);
    }

    private static Map<Integer, ?> staticField(String name) throws ReflectiveOperationException {
        Field field = VeyraRenderer.class.getDeclaredField(name);
        field.setAccessible(true);
        return (Map<Integer, ?>) field.get(null);
    }

    private static int bytesPerPixel(int format) throws IOException {
        return switch (format) {
            case VK_FORMAT_R8_UNORM -> 1;
            case VK_FORMAT_R8G8B8_UNORM, VK_FORMAT_R8G8B8_SNORM -> 3;
            case VK_FORMAT_R8G8B8A8_UNORM, VK_FORMAT_B8G8R8A8_UNORM, VK_FORMAT_R32_SFLOAT -> 4;
            case VK_FORMAT_R8G8B8A8_SNORM -> 4;
            case VK_FORMAT_R16_SFLOAT -> 2;
            case VK_FORMAT_R16G16B16_SFLOAT -> 6;
            case VK_FORMAT_R16G16B16A16_SFLOAT -> 8;
            default -> throw new IOException("Unsupported Veyra colortex readback format " + format + ".");
        };
    }

    record Capture(String targetName, int width, int height, int format, Buffer buffer,
                   boolean current) implements AutoCloseable {
        void write(Path directory) throws IOException {
            Files.createDirectories(directory);
            try (NativeImage image = new NativeImage(width, height, false)) {
                long data = buffer.getDataPtr();
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        int pixelIndex = y * width + x;
                        long offset = data + (long) pixelIndex * bytesPerPixel(format);
                        int red, green, blue;
                        switch (format) {
                            case VK_FORMAT_R8_UNORM -> red = green = blue = MemoryUtil.memGetByte(offset) & 255;
                            case VK_FORMAT_R8G8B8_UNORM -> {
                                red = MemoryUtil.memGetByte(offset) & 255;
                                green = MemoryUtil.memGetByte(offset + 1) & 255;
                                blue = MemoryUtil.memGetByte(offset + 2) & 255;
                            }
                            case VK_FORMAT_R8G8B8_SNORM -> {
                                red = snormChannel(MemoryUtil.memGetByte(offset));
                                green = snormChannel(MemoryUtil.memGetByte(offset + 1));
                                blue = snormChannel(MemoryUtil.memGetByte(offset + 2));
                            }
                            case VK_FORMAT_R8G8B8A8_UNORM -> {
                                red = MemoryUtil.memGetByte(offset) & 255;
                                green = MemoryUtil.memGetByte(offset + 1) & 255;
                                blue = MemoryUtil.memGetByte(offset + 2) & 255;
                            }
                            case VK_FORMAT_R8G8B8A8_SNORM -> {
                                red = snormChannel(MemoryUtil.memGetByte(offset));
                                green = snormChannel(MemoryUtil.memGetByte(offset + 1));
                                blue = snormChannel(MemoryUtil.memGetByte(offset + 2));
                            }
                            case VK_FORMAT_B8G8R8A8_UNORM -> {
                                blue = MemoryUtil.memGetByte(offset) & 255;
                                green = MemoryUtil.memGetByte(offset + 1) & 255;
                                red = MemoryUtil.memGetByte(offset + 2) & 255;
                            }
                            case VK_FORMAT_R16_SFLOAT -> red = green = blue = channel(halfToFloat(MemoryUtil.memGetShort(offset)));
                            case VK_FORMAT_R16G16B16_SFLOAT -> {
                                red = channel(halfToFloat(MemoryUtil.memGetShort(offset)));
                                green = channel(halfToFloat(MemoryUtil.memGetShort(offset + 2)));
                                blue = channel(halfToFloat(MemoryUtil.memGetShort(offset + 4)));
                            }
                            case VK_FORMAT_R32_SFLOAT -> red = green = blue = channel(MemoryUtil.memGetFloat(offset));
                            case VK_FORMAT_R16G16B16A16_SFLOAT -> {
                                red = channel(halfToFloat(MemoryUtil.memGetShort(offset)));
                                green = channel(halfToFloat(MemoryUtil.memGetShort(offset + 2)));
                                blue = channel(halfToFloat(MemoryUtil.memGetShort(offset + 4)));
                            }
                            default -> throw new IOException("Unsupported Veyra colortex image format " + format + ".");
                        }
                        image.setPixel(x, y, 0xff000000 | (red << 16) | (green << 8) | blue);
                    }
                }
                image.writeToFile(directory.resolve(targetName + (current ? "-current" : "") + ".png"));
            }
        }

        @Override public void close() { buffer.scheduleFree(); }
    }

    private static int channel(float value) {
        if (!Float.isFinite(value)) return 0;
        return Math.round(Math.max(0.0F, Math.min(1.0F, value)) * 255.0F);
    }

    private static int snormChannel(byte value) {
        float normalized = Math.max(-127, value) / 127.0F;
        return channel(normalized * 0.5F + 0.5F);
    }

    private static float halfToFloat(short value) {
        int half = Short.toUnsignedInt(value);
        int sign = (half & 0x8000) << 16;
        int exponent = (half >>> 10) & 0x1f;
        int fraction = half & 0x03ff;
        int bits;
        if (exponent == 0) {
            if (fraction == 0) bits = sign;
            else {
                exponent = 1;
                while ((fraction & 0x0400) == 0) { fraction <<= 1; exponent--; }
                fraction &= 0x03ff;
                bits = sign | ((exponent + 112) << 23) | (fraction << 13);
            }
        } else if (exponent == 31) {
            bits = sign | 0x7f800000 | (fraction << 13);
        } else {
            bits = sign | ((exponent + 112) << 23) | (fraction << 13);
        }
        return Float.intBitsToFloat(bits);
    }
}
