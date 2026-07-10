package com.duperknight.client.gui;

import com.duperknight.client.modules.XrayRollbackModule;
import com.duperknight.client.utils.ClientUtils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;

/** Form for invoking the xray rollback. */
public final class XrayRollbackScreen extends DMLSMenuScreen {
    private final XrayRollbackModule module;
    private TextFieldWidget ignField;
    private ButtonWidget submitButton;
    private Text validationMessage = Text.empty();

    public XrayRollbackScreen(Screen parent, XrayRollbackModule module) {
        super(Text.literal("Xray Rollback"), parent);
        this.module = module;
    }

    @Override
    protected void init() {
        configureScrollableContent(module, scaled(82));
        int formWidth = Math.min(scaled(360), width - scaled(48));
        int formX = (width - formWidth) / 2;
        ignField = addScrollableChild(new TextFieldWidget(textRenderer, formX, contentY(scaled(14)), formWidth, STANDARD_BUTTON_HEIGHT,
                Text.literal("Player IGN")), scaled(14));
        ignField.setMaxLength(16);
        ignField.setSuggestion("PlayerName");
        ignField.setChangedListener(value -> ignField.setSuggestion(value.isEmpty() ? "PlayerName" : null));
        setInitialFocus(ignField);

        addDrawableChild(ButtonWidget.builder(ScreenTexts.BACK, button -> close())
                .dimensions(leftPairedButtonX(), footerButtonY(), pairedButtonWidth(), STANDARD_BUTTON_HEIGHT).build());
        submitButton = addDrawableChild(ButtonWidget.builder(Text.literal("Roll Back"), button -> submit())
                .dimensions(rightPairedButtonX(), footerButtonY(), pairedButtonWidth(), STANDARD_BUTTON_HEIGHT).build());
        submitButton.active = !ClientUtils.isNotConnected(client);
    }

    private void submit() {
        String input = ignField.getText().trim();
        if (input.isEmpty()) {
            validationMessage = Text.literal("Enter a player IGN.");
            return;
        }
        module.submit(client, input);
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
        int labelY = contentY(0);
        if (isContentVisible(labelY, textRenderer.fontHeight)) {
            context.drawTextWithShadow(textRenderer, Text.literal("Player IGN:"), ignField.getX(), labelY, 0xFFCCCCCC);
        }
        int warningY = contentY(scaled(48));
        if (isContentVisible(warningY, textRenderer.fontHeight)) {
            context.drawCenteredTextWithShadow(textRenderer, Text.literal("This rolls back the player's blocks and containers, use with care."),
                    width / 2, warningY, 0xFFFFAA00);
        }
        int validationY = contentY(scaled(64));
        if (!validationMessage.getString().isEmpty() && isContentVisible(validationY, textRenderer.fontHeight)) {
            context.drawCenteredTextWithShadow(textRenderer, validationMessage, width / 2, validationY, 0xFFFF5555);
        }
        super.render(context, mouseX, mouseY, delta);
    }
}
