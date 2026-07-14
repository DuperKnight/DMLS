package com.duperknight.client.gui.modules;

import com.duperknight.client.gui.DMLSMenuScreen;
import com.duperknight.client.gui.DangerReviewScreen;
import com.duperknight.client.gui.widgets.DropdownWidget;
import com.duperknight.client.modules.CoreProtectBuilderModule;
import com.duperknight.client.utils.ClientUtils;
import com.duperknight.client.utils.DMLSConfig;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;

import java.util.List;

/** Form that composes CoreProtect commands with validation and a live preview. */
public final class CoreProtectBuilderScreen extends DMLSMenuScreen {
    private final CoreProtectBuilderModule module;
    private TextFieldWidget userField;
    private TextFieldWidget timeField;
    private TextFieldWidget radiusField;
    private TextFieldWidget includeField;
    private TextFieldWidget excludeField;
    private ButtonWidget runButton;
    private ButtonWidget copyButton;
    private String mode = CoreProtectBuilderModule.MODES.get(0);
    private String action = CoreProtectBuilderModule.ACTIONS.get(0);
    private CoreProtectBuilderModule.BuildResult result = CoreProtectBuilderModule.build("lookup", "", "", "", "", "", "");
    private Text status = Text.empty();

    public CoreProtectBuilderScreen(Screen parent, CoreProtectBuilderModule module) {
        super(Text.translatable("dmls.module.co_builder.name"), parent);
        this.module = module;
    }

    @Override
    protected void init() {
        String savedUser = fieldText(userField);
        String savedTime = fieldText(timeField);
        String savedRadius = fieldText(radiusField);
        String savedInclude = fieldText(includeField);
        String savedExclude = fieldText(excludeField);

        configureScrollableContent(module, scaled(330));
        int formWidth = Math.min(scaled(360), width - scaled(48));
        int formX = (width - formWidth) / 2;
        int halfWidth = formWidth / 2 - scaled(2);

        addScrollableDropdownChild(DropdownWidget.builder(
                        Text.translatable("dmls.field.co.mode"), CoreProtectBuilderModule.MODES, mode,
                        Text::literal, (dropdown, value) -> {
                            mode = value;
                            refresh();
                        })
                .dimensions(formX, contentY(0), halfWidth, STANDARD_BUTTON_HEIGHT)
                .showOptionLabel(true)
                .build(), 0);
        addScrollableDropdownChild(DropdownWidget.builder(
                        Text.translatable("dmls.field.co.action"), CoreProtectBuilderModule.ACTIONS, action,
                        Text::literal, (dropdown, value) -> {
                            action = value;
                            refresh();
                        })
                .dimensions(formX + halfWidth + scaled(4), contentY(0), halfWidth, STANDARD_BUTTON_HEIGHT)
                .showOptionLabel(true)
                .build(), 0);

        userField = addField(formX, scaled(46), halfWidth, savedUser, "PlayerName", 16);
        timeField = addField(formX + halfWidth + scaled(4), scaled(46), halfWidth, savedTime, "30d", 16);
        radiusField = addField(formX, scaled(92), halfWidth, savedRadius, "#global", 8);

        includeField = addField(formX, scaled(138), formWidth, savedInclude, "stone, diamond_ore", 256);
        excludeField = addField(formX, scaled(184), formWidth, savedExclude, "tnt", 256);

        copyButton = addScrollableChild(ButtonWidget.builder(Text.translatable("dmls.button.co.copy"), button -> {
            if (result.valid()) {
                client.keyboard.setClipboard("/" + result.command());
            }
        }).dimensions(formX, contentY(scaled(218)), formWidth, STANDARD_BUTTON_HEIGHT).build(), scaled(218));

        addDrawableChild(ButtonWidget.builder(ScreenTexts.BACK, button -> close())
                .dimensions(leftPairedButtonX(), footerButtonY(), pairedButtonWidth(), STANDARD_BUTTON_HEIGHT).build());
        runButton = addDrawableChild(ButtonWidget.builder(Text.translatable("dmls.button.co.run"), button -> submit())
                .dimensions(rightPairedButtonX(), footerButtonY(), pairedButtonWidth(), STANDARD_BUTTON_HEIGHT).build());
        refresh();
    }

    private void submit() {
        CoreProtectBuilderModule.SubmissionResult submission = module.submit(
                client, mode, userField.getText(), timeField.getText(), radiusField.getText(),
                action, includeField.getText(), excludeField.getText());
        if (submission.staged()) {
            String token = submission.token();
            client.setScreen(new DangerReviewScreen(
                    this,
                    Text.translatable("dmls.screen.co.review_title"),
                    module.previewLines(submission.request()),
                    Text.translatable("dmls.button.co.confirm"),
                    () -> module.isPending(token),
                    () -> module.confirm(client, token).accepted(),
                    () -> module.invalidatePending(token)
            ));
            return;
        }
        if (submission.accepted()) {
            closeToGame();
            return;
        }
        status = Text.translatable(submission.errorKey().isEmpty()
                ? "dmls.validation.operation.start_failed" : submission.errorKey());
    }

    private TextFieldWidget addField(int x, int offset, int fieldWidth, String saved, String placeholder, int maxLength) {
        TextFieldWidget field = addScrollableChild(new TextFieldWidget(textRenderer, x, contentY(offset), fieldWidth,
                STANDARD_BUTTON_HEIGHT, Text.empty()), offset);
        field.setMaxLength(maxLength);
        field.setText(saved);
        field.setSuggestion(saved.isEmpty() ? placeholder : null);
        field.setChangedListener(value -> {
            field.setSuggestion(value.isEmpty() ? placeholder : null);
            refresh();
        });
        return field;
    }

    private static String fieldText(TextFieldWidget field) {
        return field == null ? "" : field.getText();
    }

    private void refresh() {
        if (userField == null || timeField == null || radiusField == null || includeField == null || excludeField == null) {
            return;
        }
        result = CoreProtectBuilderModule.build(mode, userField.getText(), timeField.getText(), radiusField.getText(),
                action, includeField.getText(), excludeField.getText());
        status = Text.empty();
        if (runButton != null) {
            runButton.active = result.valid() && (DMLSConfig.dryRun() || !ClientUtils.isNotConnected(client));
        }
        if (copyButton != null) {
            copyButton.active = result.valid();
        }
    }

    @Override
    public void tick() {
        if (runButton != null) {
            runButton.active = result.valid() && (DMLSConfig.dryRun() || !ClientUtils.isNotConnected(client));
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderMenuBackground(context);
        renderModuleHeader(context, module);

        drawLabel(context, "dmls.field.co.user.label", userField.getX(), scaled(34));
        drawLabel(context, "dmls.field.co.time.label", timeField.getX(), scaled(34));
        drawLabel(context, "dmls.field.co.radius.label", radiusField.getX(), scaled(80));
        drawLabel(context, "dmls.field.co.include.label", includeField.getX(), scaled(126));
        drawLabel(context, "dmls.field.co.exclude.label", excludeField.getX(), scaled(172));

        int previewY = contentY(scaled(246));
        if (result.valid()) {
            List<OrderedText> lines = textRenderer.wrapLines(Text.literal("§7> §6/" + result.command()),
                    Math.min(scaled(360), width - scaled(48)));
            for (OrderedText line : lines) {
                if (isContentVisible(previewY, textRenderer.fontHeight)) {
                    context.drawCenteredTextWithShadow(textRenderer, line, width / 2, previewY, 0xFFFFFFFF);
                }
                previewY += textRenderer.fontHeight + 1;
            }
        } else if (isContentVisible(previewY, textRenderer.fontHeight)) {
            context.drawCenteredTextWithShadow(textRenderer, Text.translatable(result.errorKey()), width / 2, previewY, 0xFFFF5555);
        }

        if (!mode.equals("lookup")) {
            int warningY = contentY(scaled(292));
            if (isContentVisible(warningY, textRenderer.fontHeight)) {
                context.drawCenteredTextWithShadow(textRenderer, Text.translatable("dmls.module.co_builder.warning"),
                        width / 2, warningY, 0xFFFFAA00);
            }
        }
        if (!status.getString().isEmpty()) {
            context.drawCenteredTextWithShadow(textRenderer, status, width / 2,
                    footerButtonY() - scaled(13), 0xFFFF5555);
        }
        super.render(context, mouseX, mouseY, delta);
    }

    private void drawLabel(DrawContext context, String key, int x, int offset) {
        int y = contentY(offset);
        if (isContentVisible(y, textRenderer.fontHeight)) {
            context.drawTextWithShadow(textRenderer, Text.translatable(key), x, y, 0xFFCCCCCC);
        }
    }
}
