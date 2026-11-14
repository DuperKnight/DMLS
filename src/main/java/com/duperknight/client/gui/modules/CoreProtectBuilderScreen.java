package com.duperknight.client.gui.modules;

import com.duperknight.client.gui.DMLSMenuScreen;
import com.duperknight.client.gui.DangerReviewScreen;
import com.duperknight.client.gui.widgets.DropdownWidget;
import com.duperknight.client.modules.CoreProtectBuilderModule;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/** Form that composes CoreProtect commands with validation and a live preview. */
public final class CoreProtectBuilderScreen extends DMLSMenuScreen {
    private static final int PREVIEW_OFFSET = scaled(246);
    private static final int PREVIEW_WARNING_GAP = scaled(5);
    private static final int CONTENT_BOTTOM_PADDING = scaled(10);

    private final CoreProtectBuilderModule module;
    private EditBox userField;
    private EditBox timeField;
    private EditBox radiusField;
    private EditBox includeField;
    private EditBox excludeField;
    private Button runButton;
    private Button copyButton;
    private String mode = CoreProtectBuilderModule.MODES.get(0);
    private String action = CoreProtectBuilderModule.ACTIONS.get(0);
    private CoreProtectBuilderModule.BuildResult result = CoreProtectBuilderModule.build("lookup", "", "", "", "", "", "");
    private Component status = Component.empty();

    public CoreProtectBuilderScreen(Screen parent, CoreProtectBuilderModule module) {
        super(Component.translatable("dmls.module.co_builder.name"), parent);
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
                        Component.translatable("dmls.field.co.mode"), CoreProtectBuilderModule.MODES, mode,
                        Component::literal, (dropdown, value) -> {
                            mode = value;
                            refresh();
                        })
                .dimensions(formX, contentY(0), halfWidth, STANDARD_BUTTON_HEIGHT)
                .showOptionLabel(true)
                .build(), 0);
        addScrollableDropdownChild(DropdownWidget.builder(
                        Component.translatable("dmls.field.co.action"), CoreProtectBuilderModule.ACTIONS, action,
                        Component::literal, (dropdown, value) -> {
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

        copyButton = addScrollableChild(Button.builder(Component.translatable("dmls.button.co.copy"), button -> {
            if (result.valid()) {
                minecraft.keyboardHandler.setClipboard("/" + result.command());
            }
        }).bounds(formX, contentY(scaled(218)), formWidth, STANDARD_BUTTON_HEIGHT).build(), scaled(218));

        addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, button -> onClose())
                .bounds(leftPairedButtonX(), footerButtonY(), pairedButtonWidth(), STANDARD_BUTTON_HEIGHT).build());
        runButton = registerCommandControl(addRenderableWidget(Button.builder(
                        Component.translatable("dmls.button.co.run"), button -> submit())
                .bounds(rightPairedButtonX(), footerButtonY(), pairedButtonWidth(), STANDARD_BUTTON_HEIGHT).build()),
                () -> result.valid());
        refresh();
    }

    private void submit() {
        CoreProtectBuilderModule.SubmissionResult submission = module.submit(
                minecraft, mode, userField.getValue(), timeField.getValue(), radiusField.getValue(),
                action, includeField.getValue(), excludeField.getValue());
        if (submission.staged()) {
            String token = submission.token();
            minecraft.gui.setScreen(new DangerReviewScreen(
                    this,
                    Component.translatable("dmls.screen.co.review_title"),
                    module.previewLines(submission.request()),
                    Component.translatable("dmls.button.co.confirm"),
                    () -> module.isPending(token),
                    () -> module.confirm(minecraft, token).accepted(),
                    () -> module.invalidatePending(token)
            ));
            return;
        }
        if (submission.accepted()) {
            closeToGame();
            return;
        }
        status = Component.translatable(submission.errorKey().isEmpty()
                ? "dmls.validation.operation.start_failed" : submission.errorKey());
    }

    private EditBox addField(int x, int offset, int fieldWidth, String saved, String placeholder, int maxLength) {
        EditBox field = addScrollableChild(new EditBox(font, x, contentY(offset), fieldWidth,
                STANDARD_BUTTON_HEIGHT, Component.empty()), offset);
        field.setMaxLength(maxLength);
        field.setValue(saved);
        field.setSuggestion(saved.isEmpty() ? placeholder : null);
        field.setResponder(value -> {
            field.setSuggestion(value.isEmpty() ? placeholder : null);
            refresh();
        });
        return field;
    }

    private static String fieldText(EditBox field) {
        return field == null ? "" : field.getValue();
    }

    private void refresh() {
        if (userField == null || timeField == null || radiusField == null || includeField == null || excludeField == null) {
            return;
        }
        result = CoreProtectBuilderModule.build(mode, userField.getValue(), timeField.getValue(), radiusField.getValue(),
                action, includeField.getValue(), excludeField.getValue());
        status = Component.empty();
        if (copyButton != null) {
            copyButton.active = result.valid();
        }
        updateScrollableContentHeight(previewContentHeight());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        renderMenuBackground(context);
        renderModuleHeader(context, module);
        beginContentScissor(context);

        drawLabel(context, "dmls.field.co.user.label", userField.getX(), scaled(34));
        drawLabel(context, "dmls.field.co.time.label", timeField.getX(), scaled(34));
        drawLabel(context, "dmls.field.co.radius.label", radiusField.getX(), scaled(80));
        drawLabel(context, "dmls.field.co.include.label", includeField.getX(), scaled(126));
        drawLabel(context, "dmls.field.co.exclude.label", excludeField.getX(), scaled(172));

        int textY = contentY(PREVIEW_OFFSET);
        int lineSpacing = font.lineHeight + 1;
        List<FormattedCharSequence> previewLines = previewLines();
        int previewColor = result.valid() ? 0xFFFFFFFF : 0xFFFF5555;
        for (FormattedCharSequence line : previewLines) {
            if (isContentVisible(textY, font.lineHeight)) {
                context.centeredText(font, line, width / 2, textY, previewColor);
            }
            textY += lineSpacing;
        }

        if (!mode.equals("lookup")) {
            textY += PREVIEW_WARNING_GAP;
            for (FormattedCharSequence line : warningLines()) {
                if (isContentVisible(textY, font.lineHeight)) {
                    context.centeredText(font, line, width / 2, textY, 0xFFFF5555);
                }
                textY += lineSpacing;
            }
        }
        endContentScissor(context);
        if (!status.getString().isEmpty()) {
            context.centeredText(font, status, width / 2,
                    footerButtonY() - scaled(13), 0xFFFF5555);
        }
        super.extractRenderState(context, mouseX, mouseY, delta);
    }

    private void drawLabel(GuiGraphicsExtractor context, String key, int x, int offset) {
        int y = contentY(offset);
        if (isContentVisible(y, font.lineHeight)) {
            context.text(font, Component.translatable(key), x, y, 0xFFCCCCCC);
        }
    }

    private List<FormattedCharSequence> previewLines() {
        Component preview = result.valid()
                ? Component.literal("> ").withStyle(ChatFormatting.GRAY)
                        .append(Component.literal("/" + result.command()).withStyle(ChatFormatting.GOLD))
                : Component.translatable(result.errorKey());
        return font.split(preview, previewTextWidth());
    }

    private List<FormattedCharSequence> warningLines() {
        return font.split(Component.translatable("dmls.module.co_builder.warning"), previewTextWidth());
    }

    private int previewTextWidth() {
        int standardWidth = Math.min(scaled(360), width - scaled(48));
        int safeRightEdge = contentScrollbarX() - scaled(8);
        int scrollbarSafeWidth = Math.max(1, (safeRightEdge - width / 2) * 2);
        return Math.max(1, Math.min(standardWidth, scrollbarSafeWidth));
    }

    private int previewContentHeight() {
        int lineSpacing = font.lineHeight + 1;
        int contentBottom = PREVIEW_OFFSET + previewLines().size() * lineSpacing;
        if (!mode.equals("lookup")) {
            contentBottom += PREVIEW_WARNING_GAP + warningLines().size() * lineSpacing;
        }
        return Math.max(scaled(280), contentBottom + CONTENT_BOTTOM_PADDING);
    }
}
