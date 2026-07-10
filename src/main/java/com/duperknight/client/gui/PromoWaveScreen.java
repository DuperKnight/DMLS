package com.duperknight.client.gui;

import com.duperknight.client.modules.PromoWaveModule;
import com.duperknight.client.utils.ClientUtils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;

/** Form for invoking a promotion wave. */
public final class PromoWaveScreen extends DMLSMenuScreen {
    private final PromoWaveModule module;
    private TextFieldWidget ignsField;
    private String rank = PromoWaveModule.ranks().get(0);
    private ButtonWidget submitButton;
    private Text validationMessage = Text.empty();

    public PromoWaveScreen(Screen parent, PromoWaveModule module) {
        super(Text.literal("Promo Wave"), parent);
        this.module = module;
    }

    @Override
    protected void init() {
        configureScrollableContent(module, scaled(94));
        int formWidth = Math.min(scaled(360), width - scaled(48));
        int formX = (width - formWidth) / 2;
        ignsField = addScrollableChild(new TextFieldWidget(textRenderer, formX, contentY(scaled(14)), formWidth, STANDARD_BUTTON_HEIGHT,
                Text.literal("Player IGN(s)")), scaled(14));
        ignsField.setMaxLength(1024);
        ignsField.setSuggestion("PlayerOne, PlayerTwo, PlayerThree");
        ignsField.setChangedListener(value -> ignsField.setSuggestion(value.isEmpty() ? "PlayerOne, PlayerTwo, PlayerThree" : null));
        setInitialFocus(ignsField);

        addScrollableChild(CyclingButtonWidget.builder((String value) -> Text.literal(value), rank)
                .values(PromoWaveModule.ranks())
                .build(formX, contentY(scaled(48)), formWidth, STANDARD_BUTTON_HEIGHT,
                        Text.literal("Rank"), (button, value) -> rank = value), scaled(48));

        addDrawableChild(ButtonWidget.builder(ScreenTexts.BACK, button -> close())
                .dimensions(leftPairedButtonX(), footerButtonY(), pairedButtonWidth(), STANDARD_BUTTON_HEIGHT).build());
        submitButton = addDrawableChild(ButtonWidget.builder(Text.literal("Promote"), button -> submit())
                .dimensions(rightPairedButtonX(), footerButtonY(), pairedButtonWidth(), STANDARD_BUTTON_HEIGHT).build());
        submitButton.active = !ClientUtils.isNotConnected(client);
    }

    private void submit() {
        String input = ignsField.getText().trim();
        if (input.isEmpty()) {
            validationMessage = Text.literal("Enter at least one player IGN.");
            return;
        }
        module.submit(client, rank, input);
        closeToGame();
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
            context.drawTextWithShadow(textRenderer, Text.literal("Player IGN(s):"), ignsField.getX(), labelY, 0xFFCCCCCC);
        }
        int validationY = contentY(scaled(80));
        if (!validationMessage.getString().isEmpty() && isContentVisible(validationY, textRenderer.fontHeight)) {
            context.drawCenteredTextWithShadow(textRenderer, validationMessage, width / 2, validationY, 0xFFFF5555);
        }
        super.render(context, mouseX, mouseY, delta);
    }
}
