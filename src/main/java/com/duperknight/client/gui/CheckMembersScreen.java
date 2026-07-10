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
        configureScrollableContent(module, scaled(64));
        int formWidth = Math.min(scaled(360), width - scaled(48));
        int formX = (width - formWidth) / 2;
        landField = addScrollableChild(new TextFieldWidget(textRenderer, formX, contentY(scaled(14)), formWidth, STANDARD_BUTTON_HEIGHT,
                Text.literal("Land name")), scaled(14));
        landField.setMaxLength(64);
        landField.setSuggestion("LandName");
        landField.setChangedListener(value -> landField.setSuggestion(value.isEmpty() ? "LandName" : null));
        setInitialFocus(landField);

        addDrawableChild(ButtonWidget.builder(ScreenTexts.BACK, button -> close())
                .dimensions(leftPairedButtonX(), footerButtonY(), pairedButtonWidth(), STANDARD_BUTTON_HEIGHT).build());
        submitButton = addDrawableChild(ButtonWidget.builder(Text.literal("Submit"), button -> submit())
                .dimensions(rightPairedButtonX(), footerButtonY(), pairedButtonWidth(), STANDARD_BUTTON_HEIGHT).build());
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
        int labelY = contentY(0);
        if (isContentVisible(labelY, textRenderer.fontHeight)) {
            context.drawTextWithShadow(textRenderer, Text.literal("Land name:"), landField.getX(), labelY, 0xFFCCCCCC);
        }
        int validationY = contentY(scaled(48));
        if (!validationMessage.getString().isEmpty() && isContentVisible(validationY, textRenderer.fontHeight)) {
            context.drawCenteredTextWithShadow(textRenderer, validationMessage, width / 2, validationY, 0xFFFF5555);
        }
        super.render(context, mouseX, mouseY, delta);
    }
}
