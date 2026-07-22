package com.duperknight.client.gui.modules;

import com.duperknight.client.gui.DMLSMenuScreen;
import com.duperknight.client.gui.DangerReviewScreen;
import com.duperknight.client.gui.widgets.DropdownWidget;
import com.duperknight.client.modules.DemoWaveModule;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
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
        String savedNames = ignsField == null ? "" : ignsField.getText();
        configureScrollableContent(module, scaled(110));
        int formWidth = Math.min(scaled(360), width - scaled(48));
        int formX = (width - formWidth) / 2;
        ignsField = addScrollableChild(new TextFieldWidget(textRenderer, formX, contentY(scaled(14)), formWidth, STANDARD_BUTTON_HEIGHT,
                Text.translatable("dmls.field.player_igns")), scaled(14));
        ignsField.setMaxLength(1024);
        ignsField.setText(savedNames);
        ignsField.setSuggestion(Text.translatable("dmls.placeholder.player_names_many").getString());
        ignsField.setChangedListener(value -> {
            ignsField.setSuggestion(value.isEmpty()
                    ? Text.translatable("dmls.placeholder.player_names_many").getString() : null);
            validationMessage = Text.empty();
        });
        setInitialFocus(ignsField);

        addScrollableDropdownChild(DropdownWidget.builder(
                        Text.translatable("dmls.field.rank"), DemoWaveModule.ranks(), rank,
                        Text::literal, (dropdown, value) -> {
                            rank = value;
                            validationMessage = Text.empty();
                        })
                .dimensions(formX, contentY(scaled(48)), formWidth, STANDARD_BUTTON_HEIGHT)
                .showOptionLabel(true)
                .build(), scaled(48));

        addDrawableChild(ButtonWidget.builder(ScreenTexts.BACK, button -> close())
                .dimensions(leftPairedButtonX(), footerButtonY(), pairedButtonWidth(), STANDARD_BUTTON_HEIGHT).build());
        submitButton = registerCommandControl(addDrawableChild(ButtonWidget.builder(
                        Text.translatable("dmls.button.demote"), button -> submit())
                .dimensions(rightPairedButtonX(), footerButtonY(), pairedButtonWidth(), STANDARD_BUTTON_HEIGHT).build()));
    }

    private void submit() {
        String input = ignsField.getText().trim();
        if (input.isEmpty()) {
            validationMessage = Text.translatable("dmls.validation.player_igns");
            return;
        }
        DemoWaveModule.StageResult staged = module.stage(client, rank, input, false);
        if (!staged.staged()) {
            validationMessage = switch (staged.status()) {
                case INVALID -> staged.request().status() == DemoWaveModule.PreparationStatus.TOO_MANY
                        ? Text.translatable("dmls.chat.demo.too_many", DemoWaveModule.MAX_PLAYERS)
                        : Text.translatable("dmls.validation.player_igns");
                case BLOCKED -> Text.translatable("dmls.validation.operation.blocked");
                case BUSY -> Text.translatable("dmls.validation.operation.busy");
                case STAGED -> Text.empty();
            };
            return;
        }
        String token = staged.token();
        client.setScreen(new DangerReviewScreen(this,
                Text.translatable("dmls.screen.demo.review_title"),
                module.previewLines(staged.request()),
                Text.translatable("dmls.button.confirm_demotion"),
                () -> module.isPending(token),
                () -> module.confirm(client, token),
                () -> module.invalidatePending(token)));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderMenuBackground(context);
        renderModuleHeader(context, module);
        beginContentScissor(context);
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
        endContentScissor(context);
        super.render(context, mouseX, mouseY, delta);
    }
}
