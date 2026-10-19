package dev.veyra.screen;

import dev.veyra.shader.ShaderPack;
import dev.veyra.shader.ShaderPackOptions;
import dev.veyra.shader.VeyraRenderer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/** Pack-defined screens, discrete sliders, profiles, and explicitly applied edits. */
public final class ShaderOptionsScreen extends Screen {
    private final Screen parent;
    private final ShaderPack pack;
    private final ShaderPackOptions options;
    private final Map<String, String> changed;
    private final String section;
    private String status = "Changes take effect when you press Apply.";
    private int page;

    public ShaderOptionsScreen(Screen parent, ShaderPack pack, ShaderPackOptions options) {
        this(parent, pack, options, new LinkedHashMap<>(pack.configuration().options()), "");
    }

    private ShaderOptionsScreen(Screen parent, ShaderPack pack, ShaderPackOptions options, Map<String, String> changed, String section) {
        super(Component.literal(section.isEmpty() ? pack.name() + " Options" : options.label("screen." + section, section)));
        this.parent = parent; this.pack = pack; this.options = options; this.changed = changed; this.section = section;
    }

    private List<String> entries() {
        List<String> result = new ArrayList<>();
        var used = new LinkedHashSet<>(options.screen(section));
        for (String entry : options.screen(section)) {
            if (entry.equals("*")) {
                options.options().keySet().stream().filter(name -> !used.contains(name)).forEach(result::add);
            } else result.add(entry);
        }
        return result;
    }

    @Override protected void init() {
        int totalWidth = Math.max(1, Math.min(400, width - 24));
        int left = (width - totalWidth) / 2;
        int columns = options.columns(section);
        int columnWidth = Math.max(1, (totalWidth - 6 * (columns - 1)) / columns);
        int rows = Math.max(1, (height - 162) / 24);
        int count = rows * columns;
        List<String> entries = entries();
        int lastPage = Math.max(0, (entries.size() - 1) / count);
        page = Math.min(page, lastPage);
        var profiles = new ArrayList<>(options.profiles().keySet());
        int profileIndex = -1, matches = -1;
        for (int i = 0; i < profiles.size(); i++) {
            Map<String, String> values = options.profiles().get(profiles.get(i));
            boolean matching = values.entrySet().stream().allMatch(entry -> entry.getValue().equals(changed.getOrDefault(entry.getKey(), options.options().get(entry.getKey()).defaultValue())));
            if (matching && values.size() > matches) { profileIndex = i; matches = values.size(); }
        }
        final int currentProfileIndex = profileIndex;
        String profile = profileIndex >= 0 && profileIndex < profiles.size() ? profiles.get(profileIndex) : "Custom";
        addRenderableWidget(Button.builder(Component.literal("Profile: " + options.label("profile." + profile, profile)), button -> {
            if (profiles.isEmpty()) return;
            int next = (currentProfileIndex + 1) % profiles.size();
            changed.putAll(options.profiles().get(profiles.get(next)));
            status = "Profile staged. Press Apply to compile.";
            rebuildWidgets();
        }).bounds(left, 48, totalWidth, 20).build()).active = !profiles.isEmpty();
        for (int i = 0; i < count && page * count + i < entries.size(); i++) {
            String name = entries.get(page * count + i);
            int x = left + (i % columns) * (columnWidth + 6), y = 78 + (i / columns) * 24;
            if (name.equals("<empty>") || name.equals("<profile>")) continue;
            if (name.startsWith("[") && name.endsWith("]")) {
                String target = name.substring(1, name.length() - 1);
                addRenderableWidget(Button.builder(Component.literal(options.label("screen." + target, target) + "..."), button ->
                        minecraft.setScreen(new ShaderOptionsScreen(this, pack, options, changed, target)))
                        .bounds(x, y, columnWidth, 20).build());
                continue;
            }
            ShaderPackOptions.Option option = options.options().get(name);
            if (option == null) {
                addRenderableWidget(Button.builder(Component.literal(options.label("option." + name, name)), button -> { })
                        .bounds(x, y, columnWidth, 20).build()).active = false;
                continue;
            }
            String comment = options.label("option." + name + ".comment", option.description());
            if (options.sliders().contains(name) && option.values().size() > 1) {
                OptionSlider slider = new OptionSlider(x, y, columnWidth, option);
                slider.setTooltip(Tooltip.create(Component.literal(comment)));
                addRenderableWidget(slider);
            } else {
                Button button = Button.builder(optionMessage(option), ignored -> {
                    int selected = option.values().indexOf(changed.getOrDefault(name, option.defaultValue()));
                    changed.put(name, option.values().get((selected + 1) % option.values().size()));
                    rebuildWidgets();
                }).bounds(x, y, columnWidth, 20).build();
                button.setTooltip(Tooltip.create(Component.literal(comment)));
                addRenderableWidget(button);
            }
        }
        int small = Math.max(1, (totalWidth - 18) / 4);
        addRenderableWidget(Button.builder(Component.literal("Previous"), button -> { page--; rebuildWidgets(); })
                .bounds(left, height - 65, small, 20).build()).active = page > 0;
        addRenderableWidget(Button.builder(Component.literal("Reset"), button -> {
            changed.clear(); status = "Defaults staged. Press Apply to save."; rebuildWidgets();
        }).bounds(left + small + 6, height - 65, small, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Back"), button -> onClose())
                .bounds(left + (small + 6) * 2, height - 65, small, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Next"), button -> { page++; rebuildWidgets(); })
                .bounds(left + (small + 6) * 3, height - 65, small, 20).build()).active = page < lastPage;
        addRenderableWidget(Button.builder(Component.literal("Apply"), button -> status = VeyraRenderer.applyOptions(pack, changed))
                .bounds(left, height - 40, totalWidth, 20).build());
    }

    private Component optionMessage(ShaderPackOptions.Option option) {
        return Component.literal(options.label("option." + option.name(), option.name()) + ": "
                + options.label("value." + option.name() + "." + changed.getOrDefault(option.name(), option.defaultValue()),
                changed.getOrDefault(option.name(), option.defaultValue())));
    }

    private final class OptionSlider extends AbstractSliderButton {
        private final ShaderPackOptions.Option option;
        OptionSlider(int x, int y, int width, ShaderPackOptions.Option option) {
            super(x, y, width, 20, Component.empty(), Math.max(0, option.values().indexOf(changed.getOrDefault(option.name(), option.defaultValue())))
                    / (double) (option.values().size() - 1));
            this.option = option;
            updateMessage();
        }
        @Override protected void updateMessage() { setMessage(optionMessage(option)); }
        @Override protected void applyValue() {
            int selected = (int) Math.round(value * (option.values().size() - 1));
            changed.put(option.name(), option.values().get(selected));
        }
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xEE101010);
        graphics.drawCenteredString(font, title, width / 2, 18, 0xFFFFFFFF);
        super.render(graphics, mouseX, mouseY, partialTick);
        var lines = font.split(Component.literal(status), Math.max(1, width - 16));
        for (int i = 0; i < Math.min(2, lines.size()); i++) graphics.drawCenteredString(font, lines.get(i), width / 2, height - 88 + i * 9, 0xFFCCCCCC);
    }
    @Override public void onClose() { minecraft.setScreen(parent); }
}
