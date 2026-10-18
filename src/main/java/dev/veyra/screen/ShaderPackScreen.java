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
    private String status = "Select a supported shader pack. Refresh reloads the active pack from disk.";
    private int firstVisiblePack;

    public ShaderPackScreen() {
        super(Component.literal("Veyra Shader Packs"));
    }

    @Override
    protected void init() {
        int center = width / 2;
        int top = 120;
        int panelWidth = Math.max(1, Math.min(320, width - 24));
        addRenderableWidget(Button.builder(Component.literal("Disable shaders"), button -> {
            status = VeyraRenderer.disable();
            rebuildWidgets();
        }).bounds(center - panelWidth / 2, top, panelWidth, 22).build());

        addRenderableWidget(Button.builder(Component.literal("Shader options..."), button -> {
            ShaderPack active = VeyraRenderer.selectedPack().orElse(null);
            if (active == null) { status = "Select a shader pack first."; return; }
            try { minecraft.setScreen(new ShaderOptionsScreen(this, active, active.readOptions())); }
            catch (java.io.IOException exception) { status = "Could not read options: " + exception.getMessage(); }
        }).bounds(center - panelWidth / 2, top - 25, panelWidth, 20).build());

        List<ShaderPack> packs = VeyraClient.shaderPacks.getPacks();
        int availableRows = Math.max(0, (height - 210) / 26);
        int pageSize = Math.max(1, availableRows);
        int lastPageStart = packs.isEmpty() ? 0 : ((packs.size() - 1) / pageSize) * pageSize;
        firstVisiblePack = Math.min(firstVisiblePack, lastPageStart);
        int shown = Math.min(availableRows, packs.size() - firstVisiblePack);
        for (int i = 0; i < shown; i++) {
            ShaderPack pack = packs.get(firstVisiblePack + i);
            String prefix = VeyraRenderer.selectedPack().filter(active -> active.path().equals(pack.path())).isPresent() ? "[Active] " : "";
            String suffix = pack.supportsShaderPrograms() ? "" : " (unsupported)";
            Button button = Button.builder(Component.literal(prefix + pack.name() + suffix), ignored -> {
                if (!pack.supportsShaderPrograms()) {
                    status = pack.issue();
                    return;
                }
                status = VeyraRenderer.selectWithSavedOptions(pack);
                rebuildWidgets();
            }).bounds(center - panelWidth / 2, top + 28 + i * 26, panelWidth, 22).build();
            button.active = pack.supportsShaderPrograms();
            addRenderableWidget(button);
        }

        int navigationWidth = Math.max(1, Math.min(95, (width - 30) / 4));
        int navigationLeft = center - (navigationWidth * 4 + 15) / 2;
        addRenderableWidget(Button.builder(Component.literal("Previous"), button -> {
            firstVisiblePack = Math.max(0, firstVisiblePack - pageSize);
            rebuildWidgets();
        }).bounds(navigationLeft, height - 38, navigationWidth, 20).build()).active = firstVisiblePack > 0;
        addRenderableWidget(Button.builder(Component.literal("Refresh list"), button -> {
            status = VeyraRenderer.refreshPacks();
            rebuildWidgets();
        }).bounds(navigationLeft + navigationWidth + 5, height - 38, navigationWidth, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Done"), button -> onClose())
                .bounds(navigationLeft + (navigationWidth + 5) * 2, height - 38, navigationWidth, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Next"), button -> {
            firstVisiblePack = Math.min(lastPageStart, firstVisiblePack + pageSize);
            rebuildWidgets();
        }).bounds(navigationLeft + (navigationWidth + 5) * 3, height - 38, navigationWidth, 20).build()).active = firstVisiblePack < lastPageStart;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xD0101010);
        graphics.drawCenteredString(font, title, width / 2, 22, 0xFFFFFFFF);
        var lines = font.split(Component.literal(status), Math.max(1, width - 24));
        for (int i = 0; i < Math.min(4, lines.size()); i++) {
            graphics.drawCenteredString(font, lines.get(i), width / 2, 42 + i * 10, 0xFFCCCCCC);
        }
        String listStatus = VeyraClient.shaderPacks.getPacks().isEmpty()
                ? "No packs found. Put folders or ZIPs in shaderpacks."
                : "Packs: " + VeyraClient.shaderPacks.getPacks().size();
        graphics.drawCenteredString(font, listStatus, width / 2, height - 54, 0xFFCCCCCC);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(null);
    }
}
