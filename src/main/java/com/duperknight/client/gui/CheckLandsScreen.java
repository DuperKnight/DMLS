package com.duperknight.client.gui;

import com.duperknight.client.modules.CheckLandsModule;
import com.duperknight.client.utils.ClientUtils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;

/** Form for invoking Check Lands' existing single or batch checker. */
public final class CheckLandsScreen extends DMLSMenuScreen {
    private final CheckLandsModule module;
    private TextFieldWidget ignField;
    private ButtonWidget submitButton;
    private Text validationMessage = Text.empty();

    public CheckLandsScreen(Screen parent, CheckLandsModule module) {
        super(Text.literal("Check Lands"), parent);
        this.module = module;
    }

    @Override
    protected void init() {
        int formWidth = Math.min(360, width - 48);
        int formX = (width - formWidth) / 2;
        ignField = addDrawableChild(new TextFieldWidget(textRenderer, formX, height / 2 - 4, formWidth, 20,
                Text.literal("Player IGN(s)")));
        ignField.setMaxLength(512);
        ignField.setSuggestion("PlayerOne PlayerTwo");
        ignField.setChangedListener(value -> ignField.setSuggestion(value.isEmpty() ? "PlayerOne PlayerTwo" : null));
        setInitialFocus(ignField);

        addDrawableChild(ButtonWidget.builder(ScreenTexts.BACK, button -> close())
                .dimensions(leftPairedButtonX(), height - 31, pairedButtonWidth(), 20).build());
        submitButton = addDrawableChild(ButtonWidget.builder(Text.literal("Submit"), button -> submit())
                .dimensions(rightPairedButtonX(), height - 31, pairedButtonWidth(), 20).build());
        submitButton.active = !ClientUtils.isNotConnected(client);
    }

    private void submit() {
        String input = ignField.getText().trim();
        if (input.isEmpty()) {
            validationMessage = Text.literal("Enter at least one player IGN.");
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
        context.drawTextWithShadow(textRenderer, Text.literal("Player IGN(s):"), ignField.getX(), height / 2 - 20, 0xFFCCCCCC);
        if (!validationMessage.getString().isEmpty()) {
            context.drawCenteredTextWithShadow(textRenderer, validationMessage, width / 2, height / 2 + 23, 0xFFFF5555);
        }
        super.render(context, mouseX, mouseY, delta);
    }
}
