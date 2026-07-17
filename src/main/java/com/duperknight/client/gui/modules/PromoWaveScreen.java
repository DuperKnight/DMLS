package com.duperknight.client.gui.modules;

import com.duperknight.client.gui.DMLSMenuScreen;
import com.duperknight.client.gui.DangerReviewScreen;
import com.duperknight.client.gui.widgets.DropdownWidget;
import com.duperknight.client.modules.PromoWaveModule;
import com.duperknight.client.utils.ClientUtils;
import com.duperknight.client.utils.DMLSConfig;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
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
        super(Text.translatable("dmls.module.promo_wave.name"), parent);
        this.module = module;
    }

    @Override
    protected void init() {
        String savedNames = ignsField == null ? "" : ignsField.getText();
        configureScrollableContent(module, scaled(94));
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
                        Text.translatable("dmls.field.rank"), PromoWaveModule.ranks(), rank,
                        Text::literal, (dropdown, value) -> {
                            rank = value;
                            validationMessage = Text.empty();
                        })
                .dimensions(formX, contentY(scaled(48)), formWidth, STANDARD_BUTTON_HEIGHT)
                .showOptionLabel(true)
                .build(), scaled(48));

        addDrawableChild(ButtonWidget.builder(ScreenTexts.BACK, button -> close())
                .dimensions(leftPairedButtonX(), footerButtonY(), pairedButtonWidth(), STANDARD_BUTTON_HEIGHT).build());
        submitButton = addDrawableChild(ButtonWidget.builder(Text.translatable("dmls.button.promote"), button -> submit())
                .dimensions(rightPairedButtonX(), footerButtonY(), pairedButtonWidth(), STANDARD_BUTTON_HEIGHT).build());
        submitButton.active = DMLSConfig.dryRun() || !ClientUtils.isNotConnected(client);
    }

    private void submit() {
        String input = ignsField.getText().trim();
        if (input.isEmpty()) {
            validationMessage = Text.translatable("dmls.validation.player_igns");
            return;
        }
        PromoWaveModule.StageResult staged = module.stage(client, rank, input, false);
        if (!staged.staged()) {
            validationMessage = switch (staged.status()) {
                case INVALID -> staged.request().status() == PromoWaveModule.PreparationStatus.TOO_MANY
                        ? Text.translatable("dmls.chat.promo.too_many", PromoWaveModule.MAX_PLAYERS)
                        : Text.translatable("dmls.validation.player_igns");
                case BLOCKED -> Text.translatable("dmls.validation.operation.blocked");
                case BUSY -> Text.translatable("dmls.validation.operation.busy");
                case STAGED -> Text.empty();
            };
            return;
        }
        String token = staged.token();
        client.setScreen(new DangerReviewScreen(this,
                Text.translatable("dmls.screen.promo.review_title"),
                module.previewLines(staged.request()),
                Text.translatable("dmls.button.confirm_promotion"),
                () -> module.isPending(token),
                () -> module.confirm(client, token),
                () -> module.invalidatePending(token)));
    }

    @Override
    public void tick() {
        submitButton.active = DMLSConfig.dryRun() || !ClientUtils.isNotConnected(client);
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
        int validationY = contentY(scaled(80));
        if (!validationMessage.getString().isEmpty() && isContentVisible(validationY, textRenderer.fontHeight)) {
            context.drawCenteredTextWithShadow(textRenderer, validationMessage, width / 2, validationY, 0xFFFF5555);
        }
        endContentScissor(context);
        super.render(context, mouseX, mouseY, delta);
    }
}
