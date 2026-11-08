package dev.veyra.smoke;

import dev.veyra.screen.ShaderOptionsScreen;
import dev.veyra.screen.ShaderPackScreen;
import dev.veyra.shader.VeyraRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.vulkanmod.config.gui.VOptionScreen;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

/**
 * Diagnostic for {@code -Dveyra.graphicsSmoke.guiProbe=true}: opens VulkanMod's video settings, Veyra's shader pack
 * screen and the active pack's option screen in turn, and saves a screenshot of each with the GUI visible.
 */
public final class GuiProbe {
    private static final boolean ENABLED = Boolean.getBoolean("veyra.graphicsSmoke.guiProbe");
    private static final int SETTLE_TICKS = 15;
    private static final Logger LOGGER = LoggerFactory.getLogger("veyra-gui-probe");
    private static int step = -1;
    private static int ticks;
    private static boolean writing;
    private static VOptionScreen video;

    private GuiProbe() {}

    /** True while the probe owns the client, from its first step until every screenshot is written. */
    static boolean holding() {
        return ENABLED && step < 3;
    }

    static void tick(Minecraft client, Path output) {
        if (writing) return;
        if (step < 0) {
            client.options.hideGui = false;
            video = new VOptionScreen(Component.literal("Video Settings"), null);
            client.setScreen(video);
            step = 0;
            ticks = 0;
            return;
        }
        if (++ticks < SETTLE_TICKS) return;
        String name = switch (step) {
            case 0 -> "gui-video-settings.png";
            case 1 -> "gui-shader-packs.png";
            default -> "gui-shader-options.png";
        };
        writing = true;
        GraphicsSmoke.requestGuiCapture(name, () -> {
            LOGGER.info("GUI_PROBE wrote {} screen={}", name, client.screen == null ? "none" : client.screen.getClass().getSimpleName());
            advance(client);
        });
    }

    private static void advance(Minecraft client) {
        writing = false;
        ticks = 0;
        step++;
        try {
            switch (step) {
                case 1 -> client.setScreen(new ShaderPackScreen(video));
                case 2 -> {
                    var pack = VeyraRenderer.selectedPack().orElseThrow();
                    client.setScreen(new ShaderOptionsScreen(client.screen, pack, pack.readOptions()));
                }
                default -> {
                    client.setScreen(null);
                    client.options.hideGui = true;
                }
            }
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Could not open the shader option screen", exception);
        }
    }
}
