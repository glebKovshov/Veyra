package dev.veyra.smoke;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import java.util.function.Consumer;
import net.minecraft.client.Screenshot;

/** Minecraft 1.21.4 reads screenshots synchronously; later versions deliver them to a callback. */
public final class LegacyScreenshot {
    private LegacyScreenshot() { }

    public static void take(RenderTarget target, Consumer<NativeImage> consumer) {
        consumer.accept(Screenshot.takeScreenshot(target));
    }
}
