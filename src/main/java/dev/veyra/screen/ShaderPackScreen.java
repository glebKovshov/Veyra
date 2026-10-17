package dev.veyra.screen;

import dev.veyra.VeyraClient;
import dev.veyra.shader.ShaderPack;
import dev.veyra.shader.VeyraRenderer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

public final class ShaderPackScreen extends Screen {
    private String status = "F6 opens this screen. Only a single final.fsh pass is applied.";

    public ShaderPackScreen() {
        super(Component.literal("Veyra Shader Packs"));
    }

    @Override
    protected void init() {
        int center = width / 2;
        int top = 77;
        addRenderableWidget(Button.builder(Component.literal("Disable shaders"), button -> {
            status = VeyraRenderer.disable();
        }).bounds(center - 160, top, 320, 22).build());

        List<ShaderPack> packs = VeyraClient.shaderPacks.getPacks();
        int availableRows = Math.max(1, (height - 156) / 26);
        int shown = Math.min(packs.size(), availableRows);
        for (int i = 0; i < shown; i++) {
            ShaderPack pack = packs.get(i);
            String prefix = VeyraRenderer.selectedPack().filter(pack::equals).isPresent() ? "[Active] " : "";
            String suffix = pack.supportsFinalPass() ? "" : " (unsupported)";
            Button button = Button.builder(Component.literal(prefix + pack.name() + suffix), ignored -> {
                if (!pack.supportsFinalPass()) {
                    status = pack.issue();
                    return;
                }
                status = VeyraRenderer.select(pack);
            }).bounds(center - 160, top + 28 + i * 26, 320, 22).build();
            button.active = pack.supportsFinalPass();
            addRenderableWidget(button);
        }

        if (packs.isEmpty()) {
            status = "Put a shader pack folder or .zip in " + VeyraClient.shaderPacks.getDirectory();
        } else if (packs.size() > shown) {
            status = "Showing " + shown + " of " + packs.size() + " packs. Remove or move packs to see the rest.";
        }

        addRenderableWidget(Button.builder(Component.literal("Refresh list"), button -> {
            VeyraClient.shaderPacks.scan();
            status = "Shader pack list refreshed.";
            rebuildWidgets();
        }).bounds(center - 160, height - 58, 155, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Done"), button -> onClose())
                .bounds(center + 5, height - 58, 155, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xD0101010);
        graphics.drawCenteredString(font, title, width / 2, 22, 0xFFFFFFFF);
        graphics.drawCenteredString(font, status, width / 2, 48, 0xFFCCCCCC);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(null);
    }
}
