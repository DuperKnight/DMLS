package com.duperknight.client.gui;

import com.duperknight.client.modules.DemoWaveModule;
import com.duperknight.client.utils.ClientUtils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;

/** Form for invoking a demotion wave. */
public final class DemoWaveScreen extends DMLSMenuScreen {
    private final DemoWaveModule module;
    private TextFieldWidget ignsField;
    private String rank = DemoWaveModule.ranks().get(0);
    private ButtonWidget submitButton;
    private Text validationMessage = Text.empty();

    public DemoWaveScreen(Screen parent, DemoWaveModule module) {
        super(Text.translatable("dmls.module.demo_wave.name"), parent);
        this.module = module;
    }

    @Override
    protected void init() {
        configureScrollableContent(module, scaled(110));
        int formWidth = Math.min(scaled(360), width - scaled(48));
        int formX = (width - formWidth) / 2;
        ignsField = addScrollableChild(new TextFieldWidget(textRenderer, formX, contentY(scaled(14)), formWidth, STANDARD_BUTTON_HEIGHT,
                Text.translatable("dmls.field.player_igns")), scaled(14));
        ignsField.setMaxLength(1024);
        ignsField.setSuggestion(Text.translatable("dmls.placeholder.player_names_many").getString());
        ignsField.setChangedListener(value -> ignsField.setSuggestion(value.isEmpty() ? Text.translatable("dmls.placeholder.player_names_many").getString() : null));
        setInitialFocus(ignsField);

        addScrollableChild(CyclingButtonWidget.builder((String value) -> Text.literal(value), rank)
                .values(DemoWaveModule.ranks())
                .build(formX, contentY(scaled(48)), formWidth, STANDARD_BUTTON_HEIGHT,
                        Text.translatable("dmls.field.rank"), (button, value) -> rank = value), scaled(48));

        addDrawableChild(ButtonWidget.builder(ScreenTexts.BACK, button -> close())
                .dimensions(leftPairedButtonX(), footerButtonY(), pairedButtonWidth(), STANDARD_BUTTON_HEIGHT).build());
        submitButton = addDrawableChild(ButtonWidget.builder(Text.translatable("dmls.button.demote"), button -> submit())
                .dimensions(rightPairedButtonX(), footerButtonY(), pairedButtonWidth(), STANDARD_BUTTON_HEIGHT).build());
        submitButton.active = !ClientUtils.isNotConnected(client);
    }

    private void submit() {
        String input = ignsField.getText().trim();
        if (input.isEmpty()) {
            validationMessage = Text.translatable("dmls.validation.player_igns");
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
            context.drawTextWithShadow(textRenderer, Text.translatable("dmls.field.player_igns.label"), ignsField.getX(), labelY, 0xFFCCCCCC);
        }
        int warningY = contentY(scaled(80));
        if (isContentVisible(warningY, textRenderer.fontHeight)) {
            context.drawCenteredTextWithShadow(textRenderer, Text.translatable("dmls.module.demo_wave.warning"),
                    width / 2, warningY, 0xFFFFAA00);
        }
        int validationY = contentY(scaled(96));
        if (!validationMessage.getString().isEmpty() && isContentVisible(validationY, textRenderer.fontHeight)) {
            context.drawCenteredTextWithShadow(textRenderer, validationMessage, width / 2, validationY, 0xFFFF5555);
        }
        super.render(context, mouseX, mouseY, delta);
    }
}
