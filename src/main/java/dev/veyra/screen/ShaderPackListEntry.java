package dev.veyra.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.input.MouseButtonEvent;

/** Keeps pack row rendering and selection independent of Minecraft's list-event API. */
abstract class ShaderPackListEntry<E extends ShaderPackListEntry<E>> extends ObjectSelectionList.Entry<E> {
    protected abstract void renderPack(GuiGraphics graphics, int x, int y, int width, int height,
                                       int mouseX, int mouseY, boolean hovered, float partialTick);
    protected abstract boolean selectPack(boolean doubleClick);

    protected final void renderIssue(GuiGraphics graphics, Font font, Component issue, int x, int y) {
        graphics.setTooltipForNextFrame(font.split(issue, 280), x, y);
    }

    @Override public void renderContent(GuiGraphics graphics, int mouseX, int mouseY, boolean hovered, float partialTick) {
        renderPack(graphics, getContentX(), getContentY(), getContentWidth(), getContentHeight(),
                mouseX, mouseY, hovered, partialTick);
    }

    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        return selectPack(doubleClick);
    }
}
