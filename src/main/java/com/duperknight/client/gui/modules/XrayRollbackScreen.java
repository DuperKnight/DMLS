package com.duperknight.client.gui.modules;

import com.duperknight.client.gui.DMLSMenuScreen;
import com.duperknight.client.gui.DangerReviewScreen;
import com.duperknight.client.modules.XrayRollbackModule;
import com.duperknight.client.utils.ClientUtils;
import com.duperknight.client.utils.DMLSConfig;
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
        super(Text.translatable("dmls.module.xray.name"), parent);
        this.module = module;
    }

    @Override
    protected void init() {
        String savedIgn = ignField == null ? "" : ignField.getText();
        configureScrollableContent(module, scaled(82));
        int formWidth = Math.min(scaled(360), width - scaled(48));
        int formX = (width - formWidth) / 2;
        ignField = addScrollableChild(new TextFieldWidget(textRenderer, formX, contentY(scaled(14)), formWidth, STANDARD_BUTTON_HEIGHT,
                Text.translatable("dmls.field.player_ign")), scaled(14));
        ignField.setMaxLength(16);
        ignField.setText(savedIgn);
        ignField.setSuggestion(Text.translatable("dmls.placeholder.player_name").getString());
        ignField.setChangedListener(value -> {
            ignField.setSuggestion(value.isEmpty() ? Text.translatable("dmls.placeholder.player_name").getString() : null);
            validationMessage = Text.empty();
        });
        setInitialFocus(ignField);

        addDrawableChild(ButtonWidget.builder(ScreenTexts.BACK, button -> close())
                .dimensions(leftPairedButtonX(), footerButtonY(), pairedButtonWidth(), STANDARD_BUTTON_HEIGHT).build());
        submitButton = addDrawableChild(ButtonWidget.builder(Text.translatable("dmls.button.rollback"), button -> submit())
                .dimensions(rightPairedButtonX(), footerButtonY(), pairedButtonWidth(), STANDARD_BUTTON_HEIGHT).build());
        submitButton.active = DMLSConfig.dryRun() || !ClientUtils.isNotConnected(client);
    }

    private void submit() {
        String input = ignField.getText().trim();
        if (input.isEmpty()) {
            validationMessage = Text.translatable("dmls.validation.player_ign");
            return;
        }
        XrayRollbackModule.StageResult staged = module.stage(client, input, false);
        if (!staged.staged()) {
            validationMessage = switch (staged.status()) {
                case INVALID -> Text.translatable("dmls.validation.player_ign");
                case BLOCKED -> Text.translatable("dmls.validation.operation.blocked");
                case BUSY -> Text.translatable("dmls.validation.operation.busy");
                case STAGED -> Text.empty();
            };
            return;
        }

        String token = staged.token();
        client.setScreen(new DangerReviewScreen(this,
                Text.translatable("dmls.screen.xray.review_title"),
                module.previewLines(staged.request()),
                Text.translatable("dmls.button.confirm_rollback"),
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
            context.drawTextWithShadow(textRenderer, Text.translatable("dmls.field.player_ign.label"), ignField.getX(), labelY, 0xFFCCCCCC);
        }
        int warningY = contentY(scaled(48));
        if (isContentVisible(warningY, textRenderer.fontHeight)) {
            context.drawCenteredTextWithShadow(textRenderer, Text.translatable("dmls.module.xray.warning"),
                    width / 2, warningY, 0xFFFFAA00);
        }
        int validationY = contentY(scaled(64));
        if (!validationMessage.getString().isEmpty() && isContentVisible(validationY, textRenderer.fontHeight)) {
            context.drawCenteredTextWithShadow(textRenderer, validationMessage, width / 2, validationY, 0xFFFF5555);
        }
        endContentScissor(context);
        super.render(context, mouseX, mouseY, delta);
    }
}
