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
        configureScrollableContent(module, scaled(164));
        int formWidth = Math.min(scaled(360), width - scaled(48));
        int formX = (width - formWidth) / 2;

        ignField = addScrollableChild(new TextFieldWidget(textRenderer, formX, contentY(scaled(14)), formWidth,
                STANDARD_BUTTON_HEIGHT, Text.literal("Player IGN")), scaled(14));
        ignField.setMaxLength(16);
        ignField.setSuggestion("PlayerName");
        ignField.setChangedListener(value -> ignField.setSuggestion(value.isEmpty() ? "PlayerName" : null));
        setInitialFocus(ignField);

        prefixIdField = addScrollableChild(new TextFieldWidget(textRenderer, formX, contentY(scaled(60)), formWidth,
                STANDARD_BUTTON_HEIGHT, Text.literal("Prefix id")), scaled(60));
        prefixIdField.setMaxLength(64);
        prefixIdField.setSuggestion("prefixid");
        prefixIdField.setChangedListener(value -> prefixIdField.setSuggestion(value.isEmpty() ? "prefixid" : null));

        hexCodeField = addScrollableChild(new TextFieldWidget(textRenderer, formX, contentY(scaled(106)), formWidth / 2 - scaled(4),
                STANDARD_BUTTON_HEIGHT, Text.literal("Hex code")), scaled(106));
        hexCodeField.setMaxLength(128);
        hexCodeField.setSuggestion("#FFAA00");
        hexCodeField.setChangedListener(value -> hexCodeField.setSuggestion(value.isEmpty() ? "#FFAA00" : null));

        addScrollableChild(CyclingButtonWidget.builder((String value) -> Text.literal(value), limit)
                .values(PrefixCreateModule.LIMITS)
                .build(formX + formWidth / 2 + scaled(4), contentY(scaled(106)), formWidth / 2 - scaled(4),
                        STANDARD_BUTTON_HEIGHT, Text.literal("Player limit"), (button, value) -> limit = value), scaled(106));

        addDrawableChild(ButtonWidget.builder(ScreenTexts.BACK, button -> close())
                .dimensions(leftPairedButtonX(), footerButtonY(), pairedButtonWidth(), STANDARD_BUTTON_HEIGHT).build());
        submitButton = addDrawableChild(ButtonWidget.builder(Text.literal("Create"), button -> submit())
                .dimensions(rightPairedButtonX(), footerButtonY(), pairedButtonWidth(), STANDARD_BUTTON_HEIGHT).build());
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
        drawContentLabel(context, Text.literal("Player IGN:"), ignField.getX(), contentY(0));
        drawContentLabel(context, Text.literal("Prefix id:"), prefixIdField.getX(), contentY(scaled(46)));
        drawContentLabel(context, Text.literal("Hex code:"), hexCodeField.getX(), contentY(scaled(92)));
        int validationY = contentY(scaled(150));
        if (!validationMessage.getString().isEmpty() && isContentVisible(validationY, textRenderer.fontHeight)) {
            context.drawCenteredTextWithShadow(textRenderer, validationMessage, width / 2, validationY, 0xFFFF5555);
        }
        super.render(context, mouseX, mouseY, delta);
    }

    private void drawContentLabel(DrawContext context, Text label, int x, int y) {
        if (isContentVisible(y, textRenderer.fontHeight)) {
            context.drawTextWithShadow(textRenderer, label, x, y, 0xFFCCCCCC);
        }
    }
}
