package com.duperknight.client.gui;

import com.duperknight.DMLS;
import com.duperknight.client.feedback.FeedbackService;
import com.duperknight.client.gui.widgets.DropdownWidget;
import com.duperknight.client.gui.widgets.SettingsToggleWidget;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.EditBoxWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

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
    private TextFieldWidget titleField;
    private EditBoxWidget descriptionField;
    private ButtonWidget submitButton;
    private ButtonWidget screenshotButton;
    private ButtonWidget removeScreenshotButton;
    private Path screenshot;
    private Identifier screenshotTexture;
    private int screenshotWidth;
    private int screenshotHeight;
    private String screenshotError = "";
    private boolean submitting;
    private Text status;
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
        super(Text.translatable("dmls.feedback.screen.title"), parent);
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

        titleField = addScrollableChild(new TextFieldWidget(textRenderer, formX, contentY(scaled(13)),
                formWidth, STANDARD_BUTTON_HEIGHT, Text.translatable("dmls.feedback.title")), scaled(13));
        titleField.setMaxLength(120);
        titleField.setPlaceholder(Text.translatable("dmls.feedback.title.placeholder"));
        titleField.setText(draft.title);
        titleField.setChangedListener(value -> {
            draft.title = value;
            updateSubmitState();
        });

        addScrollableDropdownChild(DropdownWidget.builder(
                        Text.translatable("dmls.feedback.category"), CATEGORIES, draft.category,
                        Text::literal, (dropdown, value) -> draft.category = value)
                .dimensions(formX, contentY(scaled(59)), formWidth, STANDARD_BUTTON_HEIGHT)
                .maxVisibleRows(5)
                .showOptionLabel(true)
                .build(), scaled(59));

        int emptyGroupHeight = textRenderer.fontHeight + scaled(12) + STANDARD_BUTTON_HEIGHT;
        int emptyGroupTop = screenshotY + (screenshotPanelHeight - emptyGroupHeight) / 2;
        int screenshotButtonOffset = emptyGroupTop + textRenderer.fontHeight + scaled(12);
        screenshotButton = addScrollableChild(ButtonWidget.builder(
                        Text.translatable("dmls.feedback.screenshot.select"),
                        ignored -> openScreenshotPicker())
                .dimensions(formX + formWidth / 2 - scaled(75),
                        contentY(screenshotButtonOffset),
                        scaled(150), STANDARD_BUTTON_HEIGHT).build(),
                screenshotButtonOffset);
        setScrollableChildEnabled(screenshotButton, screenshot == null);

        int removeSize = scaled(16);
        int removeOffset = screenshotY + scaled(4);
        removeScreenshotButton = addScrollableChild(ButtonWidget.builder(Text.literal("×"),
                        ignored -> removeScreenshot())
                .dimensions(
                formX + formWidth - removeSize - scaled(5), contentY(removeOffset),
                removeSize, removeSize).build(), removeOffset);
        setScrollableChildEnabled(removeScreenshotButton, screenshot != null);

        SettingsToggleWidget diagnostics = new SettingsToggleWidget(textRenderer,
                formX, contentY(diagnosticsOffset), formWidth,
                Text.translatable("dmls.feedback.include_diagnostics"), draft.includeDiagnostics,
                checked -> draft.includeDiagnostics = checked);
        addScrollableChild(diagnostics, diagnosticsOffset);

        descriptionField = EditBoxWidget.builder().x(formX).y(contentY(descriptionOffset))
                .placeholder(Text.translatable("dmls.feedback.description.placeholder"))
                .build(textRenderer, formWidth, scaled(68), Text.translatable("dmls.feedback.description"));
        descriptionField.setMaxLength(2_000);
        descriptionField.setText(draft.description);
        descriptionField.setChangeListener(value -> {
            draft.description = value;
            updateSubmitState();
        });
        addScrollableChild(descriptionField, descriptionOffset);

        addDrawableChild(ButtonWidget.builder(ScreenTexts.BACK, ignored -> close())
                .dimensions(leftPairedButtonX(), footerButtonY(), pairedButtonWidth(), STANDARD_BUTTON_HEIGHT).build());
        submitButton = addDrawableChild(ButtonWidget.builder(
                        Text.translatable("dmls.feedback.submit"), ignored -> submit())
                .dimensions(rightPairedButtonX(), footerButtonY(), pairedButtonWidth(), STANDARD_BUTTON_HEIGHT).build());
        updateSubmitState();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderMenuBackground(context);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2,
                headerHeight() + scaled(16), 0xFFFFFFFF);

        int statusLineCount = status == null ? 0 : textRenderer.wrapLines(status, formWidth).size();
        updateScrollableContentHeight(baseContentHeight + statusLineCount * scaled(11));
        beginContentScissor(context);
        drawLabel(context, Text.translatable("dmls.feedback.title"), contentY(0));
        drawLabel(context, Text.translatable("dmls.feedback.category"), contentY(scaled(46)));
        renderScreenshotPanel(context);
        drawLabel(context, Text.translatable("dmls.feedback.description"), contentY(commentaryLabelOffset));
        if (status != null) {
            int statusY = contentY(statusOffset);
            List<OrderedText> lines = textRenderer.wrapLines(status, formWidth);
            for (OrderedText line : lines) {
                context.drawCenteredTextWithShadow(textRenderer, line, width / 2, statusY, statusColor);
                statusY += scaled(11);
            }
        }
        endContentScissor(context);
        super.render(context, mouseX, mouseY, delta);
    }

    private void renderScreenshotPanel(DrawContext context) {
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
            context.drawTexture(RenderPipelines.GUI_TEXTURED, screenshotTexture,
                    imageX, imageY, 0, 0, renderWidth, renderHeight,
                    screenshotWidth, screenshotHeight, screenshotWidth, screenshotHeight);
        } else if (screenshot == null) {
            Text hint = screenshotError.isBlank()
                    ? Text.translatable("dmls.feedback.screenshot.optional")
                    : Text.literal(screenshotError);
            int emptyGroupHeight = textRenderer.fontHeight + scaled(12) + STANDARD_BUTTON_HEIGHT;
            int hintY = y + (screenshotPanelHeight - emptyGroupHeight) / 2;
            context.drawCenteredTextWithShadow(textRenderer, hint, width / 2,
                    hintY, screenshotError.isBlank() ? 0xFFAAAAAA : ERROR_COLOR);
        }
    }

    private void drawLabel(DrawContext context, Text label, int y) {
        context.drawTextWithShadow(textRenderer, label, formX, y, 0xFFDDDDDD);
    }

    private void openScreenshotPicker() {
        screenshotButton.active = false;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer filters = stack.mallocPointer(4);
            filters.put(stack.UTF8("*.png"));
            filters.put(stack.UTF8("*.jpg"));
            filters.put(stack.UTF8("*.jpeg"));
            filters.put(stack.UTF8("*.webp"));
            filters.flip();
            String selected = TinyFileDialogs.tinyfd_openFileDialog(
                    Text.translatable("dmls.feedback.screenshot.dialog").getString(),
                    null, filters, "Images (*.png, *.jpg, *.jpeg, *.webp)", false);
            if (selected != null) selectScreenshot(Path.of(selected).toAbsolutePath().normalize());
        } catch (RuntimeException | UnsatisfiedLinkError exception) {
            DMLS.LOGGER.warn("Could not open the operating system screenshot picker", exception);
            setStatus(Text.translatable("dmls.feedback.screenshot.picker_unavailable"), ERROR_COLOR);
        } finally {
            screenshotButton.active = true;
        }
    }

    private void selectScreenshot(Path selected) {
        releaseScreenshotTexture();
        screenshot = null;
        screenshotError = "";
        try {
            long size = Files.size(selected);
            if (size <= 0 || size > FeedbackService.MAX_SCREENSHOT_BYTES) {
                screenshotError = Text.translatable("dmls.feedback.screenshot.too_large").getString();
                return;
            }
            byte[] encoded = Files.readAllBytes(selected);
            try {
                NativeImage image = NativeImage.read(encoded);
                screenshotWidth = image.getWidth();
                screenshotHeight = image.getHeight();
                screenshotTexture = Identifier.of(DMLS.MOD_ID,
                        "feedback/preview_" + Integer.toUnsignedString(selected.hashCode(), 36));
                client.getTextureManager().registerTexture(screenshotTexture,
                        new NativeImageBackedTexture(() -> "DMLS feedback screenshot preview", image));
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
            screenshotError = Text.translatable("dmls.feedback.screenshot.invalid").getString();
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
        submitButton.setMessage(Text.translatable("dmls.feedback.submitting"));
        updateSubmitState();
        setStatus(Text.translatable("dmls.feedback.submitting_hint"), STATUS_COLOR);
        FeedbackService.Submission submission = new FeedbackService.Submission(
                draft.title, draft.category, draft.description, screenshot, draft.includeDiagnostics);
        FeedbackService.submit(submission).thenAccept(result -> {
            if (client != null) client.execute(() -> applyResult(result));
        });
    }

    private void applyResult(FeedbackService.Result result) {
        submitting = false;
        submitButton.setMessage(Text.translatable("dmls.feedback.submit"));
        updateSubmitState();
        if (result.succeeded()) {
            draft.title = "";
            draft.description = "";
            titleField.setText("");
            descriptionField.setText("");
            removeScreenshot();
            setStatus(Text.translatable("dmls.feedback.success", result.feedbackId()), SUCCESS_COLOR);
            return;
        }
        setStatus(Text.literal(result.message()), ERROR_COLOR);
    }

    private void updateSubmitState() {
        if (submitButton != null) {
            submitButton.active = !submitting
                    && !draft.title.strip().isEmpty()
                    && !draft.description.strip().isEmpty();
        }
    }

    private void setStatus(Text message, int color) {
        status = message;
        statusColor = color;
    }

    private void clearStatus() {
        status = null;
        statusColor = STATUS_COLOR;
    }

    private void releaseScreenshotTexture() {
        if (screenshotTexture != null && client != null) {
            client.getTextureManager().destroyTexture(screenshotTexture);
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
