package dev.veyra.screen;

import dev.veyra.VeyraClient;
import dev.veyra.shader.ShaderPack;
import dev.veyra.shader.VeyraRenderer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Iris-style shader pack selection that uses the whole screen: an on/off toggle, a scrolling list of packs, and
 * Apply/Cancel/Done. A click only stages a pack; nothing compiles until Apply, Done or a double-click.
 */
public final class ShaderPackScreen extends Screen {
    private static final int MARGIN = 16;
    private static final int ROW_HEIGHT = 24;
    private static final int BUTTON_HEIGHT = 20;
    private static final int GAP = 6;

    private final Screen parent;
    private final DeferredShaderWork loading = new DeferredShaderWork();
    private Component status;
    private boolean statusIsError;
    /** The staged pack, or null when shaders are staged off. */
    private ShaderPack staged;
    private boolean stagedEnabled;
    private PackList list;
    private Button toggle;

    public ShaderPackScreen() {
        this(null);
    }

    public ShaderPackScreen(Screen parent) {
        super(Component.translatable("veyra.shaders.title"));
        this.parent = parent;
        Optional<ShaderPack> active = VeyraRenderer.selectedPack();
        staged = active.orElse(null);
        stagedEnabled = active.isPresent();
        String error = VeyraRenderer.getLastError();
        if (!error.isEmpty()) setStatus(Component.literal(error), true);
        else setStatus(Component.translatable("veyra.shaders.hint"), false);
    }

    private void setStatus(Component text, boolean error) {
        status = text;
        statusIsError = error;
    }

    private static boolean samePack(ShaderPack a, ShaderPack b) {
        return a != null && b != null && a.path().equals(b.path());
    }

    private static boolean isActive(ShaderPack pack) {
        return VeyraRenderer.selectedPack().filter(active -> samePack(active, pack)).isPresent();
    }

    @Override
    protected void init() {
        int contentWidth = width - 2 * MARGIN;
        int top = 44;

        toggle = addRenderableWidget(Button.builder(toggleLabel(), button -> {
            stagedEnabled = !stagedEnabled;
            if (stagedEnabled && staged == null) {
                staged = VeyraClient.shaderPacks.getPacks().stream()
                        .filter(ShaderPack::supportsShaderPrograms).findFirst().orElse(null);
            }
            button.setMessage(toggleLabel());
            if (list != null) list.syncSelection();
        }).bounds(MARGIN, top, contentWidth, BUTTON_HEIGHT).build());

        int listTop = top + BUTTON_HEIGHT + GAP;
        int listBottom = height - (BUTTON_HEIGHT * 2 + GAP * 2 + MARGIN);
        list = new PackList(MARGIN, listTop, contentWidth, Math.max(ROW_HEIGHT, listBottom - listTop));
        for (ShaderPack pack : VeyraClient.shaderPacks.getPacks()) list.addPack(new PackEntry(pack));
        list.syncSelection();
        addRenderableWidget(list);

        int thirdWidth = (contentWidth - 2 * GAP) / 3;
        int upperRow = height - MARGIN - BUTTON_HEIGHT * 2 - GAP;
        int lowerRow = height - MARGIN - BUTTON_HEIGHT;

        addRenderableWidget(Button.builder(Component.translatable("veyra.shaders.open_folder"),
                button -> Util.getPlatform().openPath(VeyraClient.shaderPacks.getDirectory()))
                .bounds(MARGIN, upperRow, thirdWidth, BUTTON_HEIGHT).build());
        addRenderableWidget(Button.builder(Component.translatable("veyra.shaders.refresh"), button ->
                run(Component.translatable("veyra.shaders.refreshing"), VeyraRenderer::refreshPacks, false))
                .bounds(MARGIN + thirdWidth + GAP, upperRow, thirdWidth, BUTTON_HEIGHT).build());
        Button settings = addRenderableWidget(Button.builder(Component.translatable("veyra.shaders.pack_settings"),
                button -> openPackSettings())
                .bounds(MARGIN + 2 * (thirdWidth + GAP), upperRow, contentWidth - 2 * (thirdWidth + GAP), BUTTON_HEIGHT)
                .build());
        boolean settingsAvailable = stagedEnabled && isActive(staged);
        settings.active = settingsAvailable;
        if (!settingsAvailable) settings.setTooltip(Tooltip.create(Component.translatable("veyra.shaders.settings_need_active")));

        addRenderableWidget(Button.builder(Component.translatable("veyra.shaders.apply"), button -> apply(false))
                .bounds(MARGIN, lowerRow, thirdWidth, BUTTON_HEIGHT).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> close())
                .bounds(MARGIN + thirdWidth + GAP, lowerRow, thirdWidth, BUTTON_HEIGHT).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> apply(true))
                .bounds(MARGIN + 2 * (thirdWidth + GAP), lowerRow, contentWidth - 2 * (thirdWidth + GAP), BUTTON_HEIGHT)
                .build());
    }

    private Component toggleLabel() {
        return Component.translatable(stagedEnabled ? "veyra.shaders.enabled" : "veyra.shaders.disabled");
    }

    private void openPackSettings() {
        ShaderPack active = VeyraRenderer.selectedPack().orElse(null);
        if (active == null) return;
        try {
            minecraft.setScreen(new ShaderOptionsScreen(this, active, active.readOptions()));
        } catch (java.io.IOException exception) {
            setStatus(Component.literal("Could not read options: " + exception.getMessage()), true);
        }
    }

    /** Applies the staged state; with {@code closeAfter}, returns to the parent once it is applied. */
    private void apply(boolean closeAfter) {
        if (loading.pending()) return;
        if (!stagedEnabled || staged == null) {
            if (VeyraRenderer.selectedPack().isEmpty()) {
                if (closeAfter) close();
                else setStatus(Component.translatable("veyra.shaders.unchanged"), false);
                return;
            }
            run(Component.translatable("veyra.shaders.disabling"), VeyraRenderer::disable, closeAfter);
            return;
        }
        if (!staged.supportsShaderPrograms()) {
            setStatus(Component.literal(staged.issue()), true);
            return;
        }
        if (isActive(staged)) {
            if (closeAfter) close();
            else setStatus(Component.translatable("veyra.shaders.unchanged"), false);
            return;
        }
        ShaderPack pack = staged;
        run(Component.translatable("veyra.shaders.loading", pack.name()),
                () -> VeyraRenderer.selectWithSavedOptions(pack), closeAfter);
    }

    private void run(Component progress, Supplier<String> work, boolean closeAfter) {
        if (loading.pending()) return;
        setStatus(progress, false);
        loading.schedule(work, result -> {
            boolean failed = !VeyraRenderer.getLastError().isEmpty();
            setStatus(Component.literal(result), failed);
            if (closeAfter && !failed) {
                close();
                return;
            }
            Optional<ShaderPack> active = VeyraRenderer.selectedPack();
            stagedEnabled = active.isPresent() || stagedEnabled && staged != null;
            if (active.isPresent()) staged = active.get();
            rebuildWidgets();
        });
    }

    private void close() {
        minecraft.setScreen(parent);
    }

    @Override
    public void onClose() {
        close();
    }

    @Override
    public void tick() {
        super.tick();
        loading.tick();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 10, 0xFFFFFFFF);
        var lines = font.split(status, Math.max(1, width - 2 * MARGIN));
        for (int i = 0; i < Math.min(2, lines.size()); i++) {
            graphics.drawCenteredString(font, lines.get(i), width / 2, 23 + i * 10,
                    statusIsError ? 0xFFFF7070 : 0xFFBBBBBB);
        }
        if (VeyraClient.shaderPacks.getPacks().isEmpty() && list != null) {
            var empty = font.split(Component.translatable("veyra.shaders.empty"), Math.max(1, list.getWidth() - 40));
            for (int i = 0; i < empty.size(); i++) {
                graphics.drawCenteredString(font, empty.get(i), width / 2, list.getY() + 20 + i * 10, 0xFFAAAAAA);
            }
        }
        loading.frameRendered();
    }

    private final class PackList extends ObjectSelectionList<PackEntry> {
        PackList(int x, int y, int listWidth, int listHeight) {
            super(ShaderPackScreen.this.minecraft, listWidth, listHeight, y, ROW_HEIGHT);
            setX(x);
        }

        void addPack(PackEntry entry) {
            addEntry(entry);
        }

        void syncSelection() {
            PackEntry match = null;
            for (PackEntry entry : children()) if (stagedEnabled && samePack(entry.pack, staged)) match = entry;
            setSelected(match);
        }

        @Override
        public int getRowWidth() {
            return getWidth() - 14;
        }

        @Override
        public int getRowLeft() {
            return getX() + 2;
        }

        @Override
        protected int scrollBarX() {
            return getRight() - 6;
        }
    }

    private final class PackEntry extends ObjectSelectionList.Entry<PackEntry> {
        private final ShaderPack pack;

        PackEntry(ShaderPack pack) {
            this.pack = pack;
        }

        @Override
        public Component getNarration() {
            return Component.literal(pack.name());
        }

        @Override
        public void renderContent(GuiGraphics graphics, int mouseX, int mouseY, boolean hovered, float partialTick) {
            boolean supported = pack.supportsShaderPrograms();
            boolean active = isActive(pack);
            Component tag = !supported ? Component.translatable("veyra.shaders.unsupported")
                    : active ? Component.translatable("veyra.shaders.active") : null;
            int tagWidth = tag == null ? 0 : font.width(tag) + 12;
            int textY = getContentY() + (getContentHeight() - font.lineHeight) / 2 + 1;
            String name = font.plainSubstrByWidth(pack.name(), Math.max(1, getContentWidth() - tagWidth - 16));
            int color = !supported ? 0xFF8A8A8A : active ? 0xFFFFE070 : 0xFFFFFFFF;
            graphics.drawString(font, name, getContentX() + 8, textY, color);
            if (tag != null) {
                graphics.drawString(font, tag, getContentRight() - font.width(tag) - 8, textY,
                        supported ? 0xFF70E070 : 0xFFE07070);
            }
            if (hovered && !supported && pack.issue() != null) {
                graphics.setTooltipForNextFrame(font.split(Component.literal(pack.issue()), 280), mouseX, mouseY);
            }
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            list.setSelected(this);
            staged = pack;
            stagedEnabled = true;
            if (!pack.supportsShaderPrograms()) {
                setStatus(Component.literal(pack.issue()), true);
            } else if (doubleClick) {
                apply(false);
            } else {
                setStatus(isActive(pack) ? Component.translatable("veyra.shaders.active")
                        : Component.translatable("veyra.shaders.hint"), false);
            }
            // The toggle reflects the staged state immediately.
            rebuildToggle();
            return true;
        }
    }

    private void rebuildToggle() {
        if (toggle != null) toggle.setMessage(toggleLabel());
    }
}
