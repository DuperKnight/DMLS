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
        int formWidth = Math.min(360, width - 48);
        int formX = (width - formWidth) / 2;
        ignField = addDrawableChild(new TextFieldWidget(textRenderer, formX, height / 2 - 4, formWidth, 20,
                Text.literal("Player IGN")));
        ignField.setMaxLength(16);
        ignField.setSuggestion("PlayerName");
        ignField.setChangedListener(value -> ignField.setSuggestion(value.isEmpty() ? "PlayerName" : null));
        setInitialFocus(ignField);

        addDrawableChild(ButtonWidget.builder(ScreenTexts.BACK, button -> close())
                .dimensions(leftPairedButtonX(), height - 31, pairedButtonWidth(), 20).build());
        submitButton = addDrawableChild(ButtonWidget.builder(Text.literal("Roll Back"), button -> submit())
                .dimensions(rightPairedButtonX(), height - 31, pairedButtonWidth(), 20).build());
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
        context.drawTextWithShadow(textRenderer, Text.literal("Player IGN:"), ignField.getX(), height / 2 - 20, 0xFFCCCCCC);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal("This rolls back the player's blocks and containers, use with care."),
                width / 2, height / 2 + 23, 0xFFFFAA00);
        if (!validationMessage.getString().isEmpty()) {
            context.drawCenteredTextWithShadow(textRenderer, validationMessage, width / 2, height / 2 + 37, 0xFFFF5555);
        }
        super.render(context, mouseX, mouseY, delta);
    }
}
