package dev.veyra.smoke;

import com.mojang.blaze3d.platform.NativeImage;
import dev.veyra.shader.VeyraRenderer;
import net.minecraft.client.Minecraft;
import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.memory.MemoryManager;
import net.vulkanmod.vulkan.memory.MemoryTypes;
import net.vulkanmod.vulkan.memory.buffer.Buffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VkBufferImageCopy;
import org.lwjgl.vulkan.VkMemoryBarrier;
import static org.lwjgl.vulkan.VK10.*;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;

/** Exercise a real client/world and measure the fixture's auxiliary color output against a baseline. */
public final class GraphicsSmoke {
    private static int ticks, worldTicks, stage, activePixels, baselineAt;
    private static boolean pending, finished, requested, captureActive;
    private static Runnable completion;
    private static final Path output = Path.of(System.getProperty("veyra.graphicsSmoke.output", "graphics-smoke-output"));
    private static boolean entityFixtureStarted, vanillaBaseline;
    private static volatile Throwable entityFixtureFailure;
    private GraphicsSmoke() { }

    public static void tick(Minecraft client) {
        if (!Boolean.getBoolean("veyra.graphicsSmoke") || finished) return;
        try {
            if (completion != null) {
                Runnable ready = completion; completion = null; ready.run();
                if (finished) return;
            }
            if (++ticks > 2400) throw new IllegalStateException("Timed out waiting for the validation world/readback; screen=" + client.screen);
            if (client.level == null || client.player == null || client.screen != null || pending) return;
            worldTicks++;
            String fixture = Files.readString(client.gameDirectory.toPath().resolve("veyra/graphics-fixture.txt"));
            vanillaBaseline = fixture.equals("vanilla-baseline");
            boolean cloudFixture = fixture.equals("cloud-colortex0");
            boolean entityFixture = fixture.equals("specular-entity") || fixture.equals("shadow-color-entity") || fixture.equals("water-shadow-depth-entity");
            if (entityFixture && !entityFixtureStarted && client.getSingleplayerServer() != null) {
                entityFixtureStarted = true;
                var server = client.getSingleplayerServer();
                // Initial player spawn is randomized around the world's spawn point.
                // Place the subject along the fixed 45-degree camera direction instead.
                double cowX = client.player.getX() - 4.0, cowY = client.player.getY(), cowZ = client.player.getZ() + 4.0;
                server.execute(() -> {
                    try {
                        var cow = net.minecraft.world.entity.EntityType.COW.create(server.overworld(), net.minecraft.world.entity.EntitySpawnReason.COMMAND);
                        if (cow == null) throw new IllegalStateException("Could not create material fixture cow");
                        cow.setPos(cowX, cowY, cowZ);
                        cow.setNoAi(true);
                        java.util.Objects.requireNonNull(cow.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.SCALE)).setBaseValue(2.0);
                        server.overworld().addFreshEntity(cow);
                    } catch (Throwable failure) { entityFixtureFailure = failure; }
                });
            }
            if (entityFixtureFailure != null) throw new IllegalStateException("Entity material fixture setup failed", entityFixtureFailure);
            client.options.hideGui = true;
            client.player.setXRot(entityFixture ? 15.0F : cloudFixture ? -60.0F : 35.0F);
            client.player.setYRot(45.0F);
            if (vanillaBaseline && worldTicks >= 240) {
                if (VeyraRenderer.isRenderingEnabled() || VeyraRenderer.selectedPack().isPresent())
                    throw new IllegalStateException("Vanilla baseline unexpectedly selected a shaderpack");
                pending = true; requested = true; captureActive = false;
            } else if (!vanillaBaseline && stage == 0 && worldTicks >= 160) {
                if (!VeyraRenderer.isRenderingEnabled() || !VeyraRenderer.getLastError().isEmpty())
                    throw new IllegalStateException("Shader pipeline failed: " + VeyraRenderer.getLastError());
                if (cloudFixture && !VeyraRenderer.hasCapturedCloudSceneColor())
                    throw new IllegalStateException("The cloud scene-color sampler was not captured before drawing clouds");
                pending = true;
                requested = true; captureActive = true;
            } else if (!vanillaBaseline && stage == 1 && worldTicks >= baselineAt) {
                pending = true;
                requested = true; captureActive = false;
            }
        } catch (Throwable failure) { finish(client, failure, -1); }
    }

    /** Record after drawing, before present; consume mapped bytes only after this frame's fence. */
    public static void renderedFrame() {
        if (!requested || finished) return;
        requested = false;
        Minecraft client = Minecraft.getInstance();
        Buffer readback = null;
        try {
            Renderer renderer = Renderer.getInstance();
            renderer.endRenderPass();
            var source = renderer.getMainPass().getMainFramebuffer().getColorAttachment();
            if ((source.usage & VK_IMAGE_USAGE_TRANSFER_SRC_BIT) == 0)
                throw new IllegalStateException("Surface image does not support frame readback");
            var auxiliary = VeyraRenderer.isRenderingEnabled() ? VeyraRenderer.geometryColorAttachment(1) : null;
            for (var custom : dev.veyra.shader.CustomUniforms.active().uniforms()) {
                LoggerFactory.getLogger("veyra-graphics-smoke").info("Custom uniform {} ({}) = {} / {}", custom.name(), custom.type(),
                        dev.veyra.shader.CustomUniforms.active().floatValue(custom.name()), dev.veyra.shader.CustomUniforms.active().intValue(custom.name()));
            }
            LoggerFactory.getLogger("veyra-graphics-smoke").info("Frame evidence: active={} main={} auxiliary={}",
                    captureActive, Long.toHexString(source.getId()), auxiliary == null ? "disabled" : Long.toHexString(auxiliary.getId()));
            int width = source.width, height = source.height, format = source.format;
            if (format != VK_FORMAT_B8G8R8A8_UNORM && format != VK_FORMAT_B8G8R8A8_SRGB
                    && format != VK_FORMAT_R8G8B8A8_UNORM && format != VK_FORMAT_R8G8B8A8_SRGB)
                throw new IllegalStateException("Unsupported readback format " + format);
            readback = new Buffer("Veyra graphics evidence", VK_BUFFER_USAGE_TRANSFER_DST_BIT, MemoryTypes.HOST_MEM);
            readback.createBuffer((long) width * height * 4);
            try (MemoryStack stack = MemoryStack.stackPush()) {
                var commands = Renderer.getCommandBuffer();
                int layout = source.getCurrentLayout();
                source.transitionImageLayout(stack, commands, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL);
                var region = VkBufferImageCopy.calloc(1, stack);
                region.imageSubresource().aspectMask(VK_IMAGE_ASPECT_COLOR_BIT).layerCount(1);
                region.imageExtent().set(width, height, 1);
                vkCmdCopyImageToBuffer(commands, source.getId(), VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL, readback.getId(), region);
                var barrier = VkMemoryBarrier.calloc(1, stack).sType$Default()
                        .srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT).dstAccessMask(VK_ACCESS_HOST_READ_BIT);
                vkCmdPipelineBarrier(commands, VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_HOST_BIT,
                        0, barrier, null, null);
                source.transitionImageLayout(stack, commands, layout);
            }
            Buffer completed = readback;
            boolean active = captureActive;
            MemoryManager.getInstance().addFrameOp(() -> {
                try {
                    var bytes = MemoryUtil.memByteBuffer(completed.getDataPtr(), width * height * 4);
                    NativeImage image = new NativeImage(width, height, false);
                    boolean bgra = format == VK_FORMAT_B8G8R8A8_UNORM || format == VK_FORMAT_B8G8R8A8_SRGB;
                    for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
                        int offset = (y * width + x) * 4;
                        int first = bytes.get(offset) & 255, green = bytes.get(offset + 1) & 255;
                        int third = bytes.get(offset + 2) & 255, alpha = bytes.get(offset + 3) & 255;
                        image.setPixel(x, y, (alpha << 24) | ((bgra ? third : first) << 16)
                                | (green << 8) | (bgra ? first : third));
                    }
                    capture(client, image, active);
                } catch (Throwable failure) { finish(client, failure, -1); }
                finally { completed.scheduleFree(); }
            });
        } catch (Throwable failure) {
            if (readback != null) readback.scheduleFree();
            finish(client, failure, -1);
        }
    }

    private static void capture(Minecraft client, NativeImage image, boolean active) {
        int colored = 0;
        try (image) {
            Files.createDirectories(output);
            image.writeToFile(output.resolve(active ? "mrt-active.png" : "shaders-disabled.png"));
            for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
                int pixel = image.getPixel(x, y);
                int red = (pixel >>> 16) & 255, green = (pixel >>> 8) & 255, blue = pixel & 255;
                if (red > 90 && blue > 75 && red > green * 1.4 && blue > green * 1.3) colored++;
            }
            int count = colored;
            completion = () -> {
                if (vanillaBaseline) {
                    finish(client, count >= 2000 ? new IllegalStateException("Unexpected shader signal in vanilla baseline: " + count) : null, count);
                } else if (active) {
                    activePixels = count;
                    VeyraRenderer.disable();
                    baselineAt = worldTicks + 80;
                    stage = 1;
                    pending = false;
                } else {
                    Throwable failure = activePixels < 2000 || count * 4 >= activePixels
                            ? new IllegalStateException("Auxiliary MRT signal absent or unchanged: active=" + activePixels + ", disabled=" + count) : null;
                    finish(client, failure, count);
                }
            };
        } catch (Throwable failure) { completion = () -> finish(client, failure, -1); }
    }

    private static void finish(Minecraft client, Throwable failure, int baseline) {
        if (finished) return;
        finished = true;
        try {
            Files.createDirectories(output);
            String error = failure == null ? "" : failure.toString().replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
            Files.writeString(output.resolve("result.json"), "{\"status\":\"" + (failure == null ? "passed" : "failed")
                    + "\",\"renderer\":\"Lavapipe/software Vulkan\",\"activeMagentaPixels\":" + activePixels
                    + ",\"disabledMagentaPixels\":" + baseline + ",\"error\":\"" + error + "\"}\n");
        } catch (Exception writeFailure) { LoggerFactory.getLogger("veyra-graphics-smoke").error("Could not save graphics result", writeFailure); }
        if (failure != null) LoggerFactory.getLogger("veyra-graphics-smoke").error("Graphics integration check failed", failure);
        client.stop();
    }
}
