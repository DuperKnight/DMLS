package com.duperknight.client.gui.modules;

import com.duperknight.client.gui.DMLSMenuScreen;
import com.duperknight.client.modules.EventPowerToolModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Save, load, clear, and delete named /powertool command bindings. */
public final class EventPowerToolScreen extends DMLSMenuScreen {
    private final EventPowerToolModule module;
    private TextFieldWidget nameField;
    private TextFieldWidget commandField;
    private Text status = Text.empty();

    public EventPowerToolScreen(Screen parent, EventPowerToolModule module) {
        super(Text.translatable("dmls.module.event_powertool.name"), parent);
        this.module = module;
    }

    @Override
    protected void init() {
        Map<String, String> saved = module.saved();
        int entryCount = saved.size();
        configureScrollableContent(module, scaled(94) + entryCount * scaled(26));

        int controlWidth = scaled(200);
        int x = width / 2 - controlWidth / 2;

        nameField = new TextFieldWidget(textRenderer, x, contentY(0), controlWidth, STANDARD_BUTTON_HEIGHT,
                Text.translatable("dmls.module.event_powertool.name_field"));
        nameField.setMaxLength(64);
        nameField.setPlaceholder(Text.translatable("dmls.module.event_powertool.name_placeholder"));
        addScrollableChild(nameField, 0);

        commandField = new TextFieldWidget(textRenderer, x, contentY(scaled(24)), controlWidth, STANDARD_BUTTON_HEIGHT,
                Text.translatable("dmls.module.event_powertool.command_field"));
        commandField.setMaxLength(256);
        commandField.setPlaceholder(Text.translatable("dmls.module.event_powertool.command_placeholder"));
        addScrollableChild(commandField, scaled(24));

        addScrollableChild(ButtonWidget.builder(Text.translatable("dmls.button.save"), button -> {
            String name = nameField.getText().trim();
            String command = commandField.getText().trim();
            if (name.isEmpty() || command.isEmpty()) {
                status = Text.translatable("dmls.validation.event_powertool.incomplete");
                return;
            }
            if (module.save(MinecraftClient.getInstance(), name, command)) {
                status = Text.empty();
                clearAndReinit();
            } else {
                status = Text.translatable("dmls.validation.config.save_failed");
            }
        }).dimensions(x, contentY(scaled(48)), controlWidth, STANDARD_BUTTON_HEIGHT).build(), scaled(48));

        addScrollableChild(ButtonWidget.builder(Text.translatable("dmls.button.event_powertool.clear"), button -> {
            if (module.clear(MinecraftClient.getInstance())) {
                status = Text.empty();
            }
        }).dimensions(x, contentY(scaled(72)), controlWidth, STANDARD_BUTTON_HEIGHT).build(), scaled(72));

        int entryY = scaled(102);
        List<Map.Entry<String, String>> entries = new ArrayList<>(saved.entrySet());
        int loadWidth = scaled(90);
        int deleteWidth = scaled(90);
        int gap = scaled(4);
        int rowX = width / 2 - (loadWidth + deleteWidth + gap) / 2;

        for (Map.Entry<String, String> entry : entries) {
            String name = entry.getKey();
            int rowOffset = entryY;
            addScrollableChild(ButtonWidget.builder(
                    Text.translatable("dmls.button.event_powertool.load", name),
                    button -> {
                        if (module.load(MinecraftClient.getInstance(), name)) {
                            status = Text.empty();
                        }
                    }).dimensions(rowX, contentY(rowOffset), loadWidth, STANDARD_BUTTON_HEIGHT).build(), rowOffset);
            addScrollableChild(ButtonWidget.builder(
                    Text.translatable("dmls.button.delete"),
                    button -> {
                        if (module.delete(MinecraftClient.getInstance(), name)) {
                            clearAndReinit();
                        }
                    }).dimensions(rowX + loadWidth + gap, contentY(rowOffset), deleteWidth, STANDARD_BUTTON_HEIGHT).build(), rowOffset);
            entryY += scaled(26);
        }

        addDrawableChild(ButtonWidget.builder(ScreenTexts.BACK, button -> close())
                .dimensions(width / 2 - scaled(75), footerButtonY(), scaled(150), STANDARD_BUTTON_HEIGHT).build());
    }

    private void clearAndReinit() {
        clearChildren();
        init();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderMenuBackground(context);
        renderModuleHeader(context, module);
        beginContentScissor(context);
        int statusY = contentY(scaled(96));
        if (!status.getString().isEmpty() && isContentVisible(statusY, textRenderer.fontHeight)) {
            context.drawCenteredTextWithShadow(textRenderer, status, width / 2, statusY, 0xFFFF5555);
        }
        List<Map.Entry<String, String>> entries = new ArrayList<>(module.saved().entrySet());
        int labelY = scaled(102);
        for (Map.Entry<String, String> entry : entries) {
            int y = contentY(labelY - scaled(12));
            if (isContentVisible(y, textRenderer.fontHeight)) {
                context.drawCenteredTextWithShadow(textRenderer, Text.literal(entry.getKey()),
                        width / 2, y, 0xFFDDDDDD);
            }
            labelY += scaled(26);
        }
        endContentScissor(context);
        super.render(context, mouseX, mouseY, delta);
    }
}