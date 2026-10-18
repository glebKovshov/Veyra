package dev.veyra.smoke;

import com.mojang.blaze3d.platform.NativeImage;
import dev.veyra.shader.VeyraRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;

/** Exercise a real client/world and measure the fixture's auxiliary color output against a baseline. */
public final class GraphicsSmoke {
    private static int ticks, worldTicks, stage, activePixels, baselineAt;
    private static boolean pending, finished;
    private static final Path output = Path.of(System.getProperty("veyra.graphicsSmoke.output", "graphics-smoke-output"));
    private GraphicsSmoke() { }

    public static void tick(Minecraft client) {
        if (!Boolean.getBoolean("veyra.graphicsSmoke") || finished) return;
        try {
            if (++ticks > 2400) throw new IllegalStateException("Timed out waiting for the validation world/readback; screen=" + client.screen);
            if (client.level == null || client.player == null || client.screen != null || pending) return;
            worldTicks++;
            client.player.setXRot(35.0F);
            client.player.setYRot(45.0F);
            if (stage == 0 && worldTicks >= 160) {
                if (!VeyraRenderer.isRenderingEnabled() || !VeyraRenderer.getLastError().isEmpty())
                    throw new IllegalStateException("Shader pipeline failed: " + VeyraRenderer.getLastError());
                pending = true;
                Screenshot.takeScreenshot(client.getMainRenderTarget(), image -> capture(client, image, true));
            } else if (stage == 1 && worldTicks >= baselineAt) {
                pending = true;
                Screenshot.takeScreenshot(client.getMainRenderTarget(), image -> capture(client, image, false));
            }
        } catch (Throwable failure) { finish(client, failure, -1); }
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
            client.execute(() -> {
                if (active) {
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
            });
        } catch (Throwable failure) { client.execute(() -> finish(client, failure, -1)); }
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
