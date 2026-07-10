package com.duperknight.client.gui;

import com.duperknight.client.modules.PrefixCreateModule;
import com.duperknight.client.utils.ClientUtils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;

/** Form for invoking the prefix creation. */
public final class PrefixCreateScreen extends DMLSMenuScreen {
    private final PrefixCreateModule module;
    private TextFieldWidget ignField;
    private TextFieldWidget prefixIdField;
    private TextFieldWidget hexCodeField;
    private String limit = PrefixCreateModule.LIMITS.get(0);
    private ButtonWidget submitButton;
    private Text validationMessage = Text.empty();

    public PrefixCreateScreen(Screen parent, PrefixCreateModule module) {
        super(Text.literal("Prefix Creation"), parent);
        this.module = module;
    }

    @Override
    protected void init() {
        int formWidth = Math.min(360, width - 48);
        int formX = (width - formWidth) / 2;
        int y = height / 2 - 46;

        ignField = addDrawableChild(new TextFieldWidget(textRenderer, formX, y, formWidth, 20, Text.literal("Player IGN")));
        ignField.setMaxLength(16);
        ignField.setSuggestion("PlayerName");
        ignField.setChangedListener(value -> ignField.setSuggestion(value.isEmpty() ? "PlayerName" : null));
        setInitialFocus(ignField);

        prefixIdField = addDrawableChild(new TextFieldWidget(textRenderer, formX, y + 34, formWidth, 20, Text.literal("Prefix id")));
        prefixIdField.setMaxLength(64);
        prefixIdField.setSuggestion("prefixid");
        prefixIdField.setChangedListener(value -> prefixIdField.setSuggestion(value.isEmpty() ? "prefixid" : null));

        hexCodeField = addDrawableChild(new TextFieldWidget(textRenderer, formX, y + 68, formWidth / 2 - 4, 20, Text.literal("Hex code")));
        hexCodeField.setMaxLength(7);
        hexCodeField.setSuggestion("#FFAA00");
        hexCodeField.setChangedListener(value -> hexCodeField.setSuggestion(value.isEmpty() ? "#FFAA00" : null));

        addDrawableChild(CyclingButtonWidget.builder((String value) -> Text.literal(value), limit)
                .values(PrefixCreateModule.LIMITS)
                .build(formX + formWidth / 2 + 4, y + 68, formWidth / 2 - 4, 20,
                        Text.literal("Player limit"), (button, value) -> limit = value));

        addDrawableChild(ButtonWidget.builder(ScreenTexts.BACK, button -> close())
                .dimensions(leftPairedButtonX(), height - 31, pairedButtonWidth(), 20).build());
        submitButton = addDrawableChild(ButtonWidget.builder(Text.literal("Create"), button -> submit())
                .dimensions(rightPairedButtonX(), height - 31, pairedButtonWidth(), 20).build());
        submitButton.active = !ClientUtils.isNotConnected(client);
    }

    private void submit() {
        if (ignField.getText().trim().isEmpty() || prefixIdField.getText().trim().isEmpty() || hexCodeField.getText().trim().isEmpty()) {
            validationMessage = Text.literal("Fill in the IGN, prefix id and hex code.");
            return;
        }
        module.submit(client, ignField.getText().trim(), limit, prefixIdField.getText().trim(), hexCodeField.getText().trim());
        close();
    }

    @Override
    public void tick() {
        submitButton.active = !ClientUtils.isNotConnected(client);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderMenuBackground(context);
        renderModuleHeader(context, module);
        context.drawTextWithShadow(textRenderer, Text.literal("Player IGN:"), ignField.getX(), ignField.getY() - 12, 0xFFCCCCCC);
        context.drawTextWithShadow(textRenderer, Text.literal("Prefix id:"), prefixIdField.getX(), prefixIdField.getY() - 12, 0xFFCCCCCC);
        context.drawTextWithShadow(textRenderer, Text.literal("Hex code:"), hexCodeField.getX(), hexCodeField.getY() - 12, 0xFFCCCCCC);
        if (!validationMessage.getString().isEmpty()) {
            context.drawCenteredTextWithShadow(textRenderer, validationMessage, width / 2, height - 45, 0xFFFF5555);
        }
        super.render(context, mouseX, mouseY, delta);
    }
}
