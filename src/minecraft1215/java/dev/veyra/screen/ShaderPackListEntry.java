package dev.veyra.screen;

import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.components.ObjectSelectionList;

abstract class ShaderPackListEntry<E extends ShaderPackListEntry<E>> extends ObjectSelectionList.Entry<E> {
    private long lastClick = -1;
    protected abstract void renderPack(GuiGraphics graphics, int x, int y, int width, int height,
                                       int mouseX, int mouseY, boolean hovered, float partialTick);
    protected abstract boolean selectPack(boolean doubleClick);

    protected final void renderIssue(GuiGraphics graphics, Font font, Component issue, int x, int y) {
        graphics.renderTooltip(font, font.split(issue, 280), x, y);
    }

    @Override public void render(GuiGraphics graphics, int index, int y, int x, int width, int height,
                                 int mouseX, int mouseY, boolean hovered, float partialTick) {
        renderPack(graphics, x, y, width, height, mouseX, mouseY, hovered, partialTick);
    }

    @Override public boolean mouseClicked(double x, double y, int button) {
        long now = Util.getMillis();
        boolean doubleClick = isDoubleClick(lastClick, now);
        lastClick = now;
        return selectPack(doubleClick);
    }

    static boolean isDoubleClick(long previous, long now) {
        return previous >= 0 && now >= previous && now - previous < 250;
    }
}
