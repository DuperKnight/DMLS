package com.duperknight.client.gui;

import com.duperknight.DMLS;
import com.duperknight.client.feedback.FeedbackService;
import com.duperknight.client.gui.widgets.DropdownWidget;
import com.duperknight.client.gui.widgets.SettingsToggleWidget;
import com.mojang.blaze3d.platform.NativeImage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;

/** Form for sending optional screenshots and diagnostics to the DMLS feedback API. */
public final class FeedbackScreen extends DMLSMenuScreen {
    private static final List<String> CATEGORIES = List.of(
            "General", "Bug", "Suggestion", "Visual Glitch", "Crash", "Performance",
            "Compatibility", "Account / Linking", "Other");
    private static final int FORM_WIDTH = 430;
    private static final int STATUS_COLOR = 0xFFDDDDDD;
    private static final int ERROR_COLOR = 0xFFFF7777;
    private static final int SUCCESS_COLOR = 0xFF77FF99;

    private final Draft draft = new Draft();
    private EditBox titleField;
    private MultiLineEditBox descriptionField;
    private Button submitButton;
    private Button screenshotButton;
    private Button removeScreenshotButton;
    private Path screenshot;
    private Identifier screenshotTexture;
    private int screenshotWidth;
    private int screenshotHeight;
    private String screenshotError = "";
    private boolean submitting;
    private Component status;
    private int statusColor = STATUS_COLOR;
    private int formX;
    private int formWidth;
    private int contentTop;
    private int screenshotY;
    private int screenshotPanelHeight;
    private int diagnosticsOffset;
    private int commentaryLabelOffset;
    private int descriptionOffset;
    private int statusOffset;
    private int baseContentHeight;

    public FeedbackScreen(Screen parent) {
        super(Component.translatable("dmls.feedback.screen.title"), parent);
    }

    @Override
    protected void init() {
        formWidth = Math.min(scaled(FORM_WIDTH), width - scaled(32));
        formX = width / 2 - formWidth / 2;
        contentTop = headerHeight() + scaled(31);
        int contentBottom = height - FOOTER_TOP_OFFSET - scaled(8);
        screenshotY = scaled(105);
        screenshotPanelHeight = Math.max(scaled(116), formWidth * 9 / 16);
        diagnosticsOffset = screenshotY + screenshotPanelHeight + scaled(8);
        commentaryLabelOffset = diagnosticsOffset + scaled(26);
        descriptionOffset = commentaryLabelOffset + scaled(14);
        statusOffset = descriptionOffset + scaled(68) + scaled(22);
        baseContentHeight = statusOffset + scaled(21);
        configureScrollableContent(contentTop, contentBottom, baseContentHeight);

        titleField = addScrollableChild(new EditBox(font, formX, contentY(scaled(13)),
                formWidth, STANDARD_BUTTON_HEIGHT, Component.translatable("dmls.feedback.title")), scaled(13));
        titleField.setMaxLength(120);
        titleField.setHint(Component.translatable("dmls.feedback.title.placeholder"));
        titleField.setValue(draft.title);
        titleField.setResponder(value -> {
            draft.title = value;
            updateSubmitState();
        });

        addScrollableDropdownChild(DropdownWidget.builder(
                        Component.translatable("dmls.feedback.category"), CATEGORIES, draft.category,
                        Component::literal, (dropdown, value) -> draft.category = value)
                .dimensions(formX, contentY(scaled(59)), formWidth, STANDARD_BUTTON_HEIGHT)
                .maxVisibleRows(5)
                .showOptionLabel(true)
                .build(), scaled(59));

        int emptyGroupHeight = font.lineHeight + scaled(12) + STANDARD_BUTTON_HEIGHT;
        int emptyGroupTop = screenshotY + (screenshotPanelHeight - emptyGroupHeight) / 2;
        int screenshotButtonOffset = emptyGroupTop + font.lineHeight + scaled(12);
        screenshotButton = addScrollableChild(Button.builder(
                                Component.translatable("dmls.feedback.screenshot.select"),
                                ignored -> openScreenshotPicker())
                        .bounds(formX + formWidth / 2 - scaled(75),
                                contentY(screenshotButtonOffset),
                                scaled(150), STANDARD_BUTTON_HEIGHT).build(),
                screenshotButtonOffset);
        setScrollableChildEnabled(screenshotButton, screenshot == null);

        int removeSize = scaled(16);
        int removeOffset = screenshotY + scaled(4);
        removeScreenshotButton = addScrollableChild(Button.builder(Component.literal("×"),
                        ignored -> removeScreenshot())
                .bounds(
                        formX + formWidth - removeSize - scaled(5), contentY(removeOffset),
                        removeSize, removeSize).build(), removeOffset);
        setScrollableChildEnabled(removeScreenshotButton, screenshot != null);

        SettingsToggleWidget diagnostics = new SettingsToggleWidget(font,
                formX, contentY(diagnosticsOffset), formWidth,
                Component.translatable("dmls.feedback.include_diagnostics"), draft.includeDiagnostics,
                checked -> draft.includeDiagnostics = checked);
        addScrollableChild(diagnostics, diagnosticsOffset);

        descriptionField = MultiLineEditBox.builder().setX(formX).setY(contentY(descriptionOffset))
                .setPlaceholder(Component.translatable("dmls.feedback.description.placeholder"))
                .build(font, formWidth, scaled(68), Component.translatable("dmls.feedback.description"));
        descriptionField.setCharacterLimit(2_000);
        descriptionField.setValue(draft.description);
        descriptionField.setValueListener(value -> {
            draft.description = value;
            updateSubmitState();
        });
        addScrollableChild(descriptionField, descriptionOffset);

        addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, ignored -> onClose())
                .bounds(leftPairedButtonX(), footerButtonY(), pairedButtonWidth(), STANDARD_BUTTON_HEIGHT).build());
        submitButton = addRenderableWidget(Button.builder(
                        Component.translatable("dmls.feedback.submit"), ignored -> submit())
                .bounds(rightPairedButtonX(), footerButtonY(), pairedButtonWidth(), STANDARD_BUTTON_HEIGHT).build());
        updateSubmitState();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        renderMenuBackground(context);
        context.centeredText(font, title, width / 2,
                headerHeight() + scaled(16), 0xFFFFFFFF);

        int statusLineCount = status == null ? 0 : font.split(status, formWidth).size();
        updateScrollableContentHeight(baseContentHeight + statusLineCount * scaled(11));
        beginContentScissor(context);
        drawLabel(context, Component.translatable("dmls.feedback.title"), contentY(0));
        drawLabel(context, Component.translatable("dmls.feedback.category"), contentY(scaled(46)));
        renderScreenshotPanel(context);
        drawLabel(context, Component.translatable("dmls.feedback.description"), contentY(commentaryLabelOffset));
        if (status != null) {
            int statusY = contentY(statusOffset);
            List<FormattedCharSequence> lines = font.split(status, formWidth);
            for (FormattedCharSequence line : lines) {
                context.centeredText(font, line, width / 2, statusY, statusColor);
                statusY += scaled(11);
            }
        }
        endContentScissor(context);
        super.extractRenderState(context, mouseX, mouseY, delta);
    }

    private void renderScreenshotPanel(GuiGraphicsExtractor context) {
        int y = contentY(screenshotY);
        renderPanel(context, formX, y, formWidth, screenshotPanelHeight);
        if (screenshotTexture != null) {
            int inset = scaled(3);
            int availableWidth = formWidth - inset * 2;
            int availableHeight = screenshotPanelHeight - inset * 2;
            double scale = Math.min((double) availableWidth / screenshotWidth,
                    (double) availableHeight / screenshotHeight);
            int renderWidth = Math.max(1, (int) Math.round(screenshotWidth * scale));
            int renderHeight = Math.max(1, (int) Math.round(screenshotHeight * scale));
            int imageX = formX + (formWidth - renderWidth) / 2;
            int imageY = y + (screenshotPanelHeight - renderHeight) / 2;
            context.blit(RenderPipelines.GUI_TEXTURED, screenshotTexture,
                    imageX, imageY, 0, 0, renderWidth, renderHeight,
                    screenshotWidth, screenshotHeight, screenshotWidth, screenshotHeight);
        } else if (screenshot == null) {
            Component hint = screenshotError.isBlank()
                    ? Component.translatable("dmls.feedback.screenshot.optional")
                    : Component.literal(screenshotError);
            int emptyGroupHeight = font.lineHeight + scaled(12) + STANDARD_BUTTON_HEIGHT;
            int hintY = y + (screenshotPanelHeight - emptyGroupHeight) / 2;
            context.centeredText(font, hint, width / 2,
                    hintY, screenshotError.isBlank() ? 0xFFAAAAAA : ERROR_COLOR);
        }
    }

    private void drawLabel(GuiGraphicsExtractor context, Component label, int y) {
        context.text(font, label, formX, y, 0xFFDDDDDD);
    }

    /** The native file dialog is gone in 26.3 (SDL), so screenshots are attached by dropping them on the window. */
    private void openScreenshotPicker() {
        setStatus(Component.translatable("dmls.feedback.screenshot.drop_hint"), STATUS_COLOR);
    }

    @Override
    public void onFilesDrop(List<Path> paths) {
        if (screenshot != null) return;
        for (Path path : paths) {
            String lower = path.getFileName().toString().toLowerCase(Locale.ROOT);
            if (lower.endsWith(".png") || lower.endsWith(".jpg")
                    || lower.endsWith(".jpeg") || lower.endsWith(".webp")) {
                selectScreenshot(path.toAbsolutePath().normalize());
                return;
            }
        }
        screenshotError = Component.translatable("dmls.feedback.screenshot.invalid").getString();
    }

    private void selectScreenshot(Path selected) {
        releaseScreenshotTexture();
        screenshot = null;
        screenshotError = "";
        try {
            long size = Files.size(selected);
            if (size <= 0 || size > FeedbackService.MAX_SCREENSHOT_BYTES) {
                screenshotError = Component.translatable("dmls.feedback.screenshot.too_large").getString();
                return;
            }
            byte[] encoded = Files.readAllBytes(selected);
            try {
                NativeImage image = NativeImage.read(encoded);
                screenshotWidth = image.getWidth();
                screenshotHeight = image.getHeight();
                screenshotTexture = Identifier.fromNamespaceAndPath(DMLS.MOD_ID,
                        "feedback/preview_" + Integer.toUnsignedString(selected.hashCode(), 36));
                minecraft.getTextureManager().register(screenshotTexture,
                        new DynamicTexture(() -> "DMLS feedback screenshot preview", image));
            } catch (IOException previewFailure) {
                // NativeImage cannot preview every accepted WebP image, but the service validates its signature.
                String lower = selected.getFileName().toString().toLowerCase(Locale.ROOT);
                if (!lower.endsWith(".webp")) throw previewFailure;
            }
            screenshot = selected;
            setScrollableChildEnabled(screenshotButton, false);
            setScrollableChildEnabled(removeScreenshotButton, true);
            clearStatus();
        } catch (IOException | RuntimeException exception) {
            DMLS.LOGGER.warn("Could not load feedback screenshot '{}'", selected, exception);
            screenshotError = Component.translatable("dmls.feedback.screenshot.invalid").getString();
        }
    }

    private void removeScreenshot() {
        releaseScreenshotTexture();
        screenshot = null;
        screenshotError = "";
        setScrollableChildEnabled(screenshotButton, true);
        setScrollableChildEnabled(removeScreenshotButton, false);
        clearStatus();
    }

    private void submit() {
        if (submitting) return;
        submitting = true;
        submitButton.setMessage(Component.translatable("dmls.feedback.submitting"));
        updateSubmitState();
        setStatus(Component.translatable("dmls.feedback.submitting_hint"), STATUS_COLOR);
        FeedbackService.Submission submission = new FeedbackService.Submission(
                draft.title, draft.category, draft.description, screenshot, draft.includeDiagnostics);
        FeedbackService.submit(submission).thenAccept(result -> {
            if (minecraft != null) minecraft.execute(() -> applyResult(result));
        });
    }

    private void applyResult(FeedbackService.Result result) {
        submitting = false;
        submitButton.setMessage(Component.translatable("dmls.feedback.submit"));
        updateSubmitState();
        if (result.succeeded()) {
            draft.title = "";
            draft.description = "";
            titleField.setValue("");
            descriptionField.setValue("");
            removeScreenshot();
            setStatus(Component.translatable("dmls.feedback.success", result.feedbackId()), SUCCESS_COLOR);
            return;
        }
        setStatus(Component.literal(result.message()), ERROR_COLOR);
    }

    private void updateSubmitState() {
        if (submitButton != null) {
            submitButton.active = !submitting
                    && !draft.title.strip().isEmpty()
                    && !draft.description.strip().isEmpty();
        }
    }

    private void setStatus(Component message, int color) {
        status = message;
        statusColor = color;
    }

    private void clearStatus() {
        status = null;
        statusColor = STATUS_COLOR;
    }

    private void releaseScreenshotTexture() {
        if (screenshotTexture != null && minecraft != null) {
            minecraft.getTextureManager().release(screenshotTexture);
        }
        screenshotTexture = null;
        screenshotWidth = 0;
        screenshotHeight = 0;
    }

    @Override
    protected int contentScrollbarX() {
        return Math.min(width - SCROLLBAR_WIDTH - scaled(4), formX + formWidth + scaled(7));
    }

    @Override
    public void removed() {
        releaseScreenshotTexture();
        super.removed();
    }

    private static final class Draft {
        private String title = "";
        private String category = CATEGORIES.getFirst();
        private String description = "";
        private boolean includeDiagnostics = true;
    }
}