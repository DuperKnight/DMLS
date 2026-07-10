package com.duperknight.client.gui;

import com.duperknight.client.modules.DonorPetModule;
import com.duperknight.client.utils.ClientUtils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;

/** Form for giving a donor their pet permission. */
public final class DonorPetScreen extends DMLSMenuScreen {
    private final DonorPetModule module;
    private TextFieldWidget ignField;
    private String pet = DonorPetModule.pets().get(0);
    private ButtonWidget submitButton;
    private Text validationMessage = Text.empty();

    public DonorPetScreen(Screen parent, DonorPetModule module) {
        super(Text.literal("Donor Pets"), parent);
        this.module = module;
    }

    @Override
    protected void init() {
        configureScrollableContent(module, scaled(94));
        int formWidth = Math.min(scaled(360), width - scaled(48));
        int formX = (width - formWidth) / 2;
        ignField = addScrollableChild(new TextFieldWidget(textRenderer, formX, contentY(scaled(14)), formWidth, STANDARD_BUTTON_HEIGHT,
                Text.literal("Player IGN")), scaled(14));
        ignField.setMaxLength(16);
        ignField.setSuggestion("PlayerName");
        ignField.setChangedListener(value -> ignField.setSuggestion(value.isEmpty() ? "PlayerName" : null));
        setInitialFocus(ignField);

        addScrollableChild(CyclingButtonWidget.builder((String value) -> Text.literal(value), pet)
                .values(DonorPetModule.pets())
                .build(formX, contentY(scaled(48)), formWidth, STANDARD_BUTTON_HEIGHT,
                        Text.literal("Pet"), (button, value) -> pet = value), scaled(48));

        addDrawableChild(ButtonWidget.builder(ScreenTexts.BACK, button -> close())
                .dimensions(leftPairedButtonX(), footerButtonY(), pairedButtonWidth(), STANDARD_BUTTON_HEIGHT).build());
        submitButton = addDrawableChild(ButtonWidget.builder(Text.literal("Give Pet"), button -> submit())
                .dimensions(rightPairedButtonX(), footerButtonY(), pairedButtonWidth(), STANDARD_BUTTON_HEIGHT).build());
        submitButton.active = !ClientUtils.isNotConnected(client);
    }

    private void submit() {
        String input = ignField.getText().trim();
        if (input.isEmpty()) {
            validationMessage = Text.literal("Enter a player IGN.");
            return;
        }
        module.submit(client, input, pet);
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
        int validationY = contentY(scaled(80));
        if (!validationMessage.getString().isEmpty() && isContentVisible(validationY, textRenderer.fontHeight)) {
            context.drawCenteredTextWithShadow(textRenderer, validationMessage, width / 2, validationY, 0xFFFF5555);
        }
        super.render(context, mouseX, mouseY, delta);
    }
}
