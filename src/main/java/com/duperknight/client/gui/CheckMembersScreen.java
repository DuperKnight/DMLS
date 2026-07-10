package com.duperknight.client.gui;

import com.duperknight.client.modules.CheckMembersModule;
import com.duperknight.client.utils.ClientUtils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;

/** Form for invoking Check Members' land member listing. */
public final class CheckMembersScreen extends DMLSMenuScreen {
    private final CheckMembersModule module;
    private TextFieldWidget landField;
    private ButtonWidget submitButton;
    private Text validationMessage = Text.empty();

    public CheckMembersScreen(Screen parent, CheckMembersModule module) {
        super(Text.literal("Check Members"), parent);
        this.module = module;
    }

    @Override
    protected void init() {
        int formWidth = Math.min(360, width - 48);
        int formX = (width - formWidth) / 2;
        landField = addDrawableChild(new TextFieldWidget(textRenderer, formX, height / 2 - 4, formWidth, 20,
                Text.literal("Land name")));
        landField.setMaxLength(64);
        landField.setSuggestion("LandName");
        landField.setChangedListener(value -> landField.setSuggestion(value.isEmpty() ? "LandName" : null));
        setInitialFocus(landField);

        addDrawableChild(ButtonWidget.builder(ScreenTexts.BACK, button -> close())
                .dimensions(leftPairedButtonX(), height - 31, pairedButtonWidth(), 20).build());
        submitButton = addDrawableChild(ButtonWidget.builder(Text.literal("Submit"), button -> submit())
                .dimensions(rightPairedButtonX(), height - 31, pairedButtonWidth(), 20).build());
        submitButton.active = !ClientUtils.isNotConnected(client);
    }

    private void submit() {
        String input = landField.getText().trim();
        if (input.isEmpty()) {
            validationMessage = Text.literal("Enter a land name.");
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
        context.drawTextWithShadow(textRenderer, Text.literal("Land name:"), landField.getX(), height / 2 - 20, 0xFFCCCCCC);
        if (!validationMessage.getString().isEmpty()) {
            context.drawCenteredTextWithShadow(textRenderer, validationMessage, width / 2, height / 2 + 23, 0xFFFF5555);
        }
        super.render(context, mouseX, mouseY, delta);
    }
}
