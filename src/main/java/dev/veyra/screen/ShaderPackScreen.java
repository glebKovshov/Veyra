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
    private String status = "Supports terrain layers, entity fragments, color-only cloud shaders, and Iris post passes; other gbuffers and shadows are unavailable.";
    private int firstVisiblePack;

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
        int lastPageStart = packs.isEmpty() ? 0 : ((packs.size() - 1) / availableRows) * availableRows;
        firstVisiblePack = Math.min(firstVisiblePack, lastPageStart);
        int shown = Math.min(availableRows, packs.size() - firstVisiblePack);
        for (int i = 0; i < shown; i++) {
            ShaderPack pack = packs.get(firstVisiblePack + i);
            String prefix = VeyraRenderer.selectedPack().filter(pack::equals).isPresent() ? "[Active] " : "";
            String suffix = pack.supportsShaderPrograms() ? "" : " (unsupported)";
            Button button = Button.builder(Component.literal(prefix + pack.name() + suffix), ignored -> {
                if (!pack.supportsShaderPrograms()) {
                    status = pack.issue();
                    return;
                }
                status = VeyraRenderer.select(pack);
            }).bounds(center - 160, top + 28 + i * 26, 320, 22).build();
            button.active = pack.supportsShaderPrograms();
            addRenderableWidget(button);
        }

        if (packs.isEmpty()) {
            status = "Put a shader pack folder or .zip in " + VeyraClient.shaderPacks.getDirectory();
        } else if (packs.size() > availableRows) {
            int first = firstVisiblePack + 1;
            int last = firstVisiblePack + shown;
            status = "Showing packs " + first + "-" + last + " of " + packs.size() + ".";
        }

        addRenderableWidget(Button.builder(Component.literal("Previous"), button -> {
            firstVisiblePack = Math.max(0, firstVisiblePack - availableRows);
            rebuildWidgets();
        }).bounds(center - 205, height - 58, 95, 20).build()).active = firstVisiblePack > 0;
        addRenderableWidget(Button.builder(Component.literal("Refresh list"), button -> {
            VeyraClient.shaderPacks.scan();
            status = "Shader pack list refreshed.";
            rebuildWidgets();
        }).bounds(center - 100, height - 58, 95, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Done"), button -> onClose())
                .bounds(center + 5, height - 58, 95, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Next"), button -> {
            firstVisiblePack = Math.min(lastPageStart, firstVisiblePack + availableRows);
            rebuildWidgets();
        }).bounds(center + 110, height - 58, 95, 20).build()).active = firstVisiblePack < lastPageStart;
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
