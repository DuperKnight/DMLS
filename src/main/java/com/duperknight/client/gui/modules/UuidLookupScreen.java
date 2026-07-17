package com.duperknight.client.gui.modules;

import com.duperknight.client.gui.DMLSMenuScreen;
import com.duperknight.client.modules.UuidLookupModule;
import com.duperknight.client.utils.MojangProfileLookup;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;

/** Form for local Mojang username-to-UUID lookups. */
public final class UuidLookupScreen extends DMLSMenuScreen {
    private static final int RESULT_START = 70;
    private static final int RESULT_SPACING = 28;
    private final UuidLookupModule module;
    private TextFieldWidget usernameField;
    private ButtonWidget submitButton;
    private Text validationMessage = Text.empty();
    private MojangProfileLookup.BatchResult result;

    public UuidLookupScreen(Screen parent, UuidLookupModule module) {
        super(Text.translatable("dmls.module.uuid.name"), parent);
        this.module = module;
    }

    @Override
    protected void init() {
        String savedInput = usernameField == null ? "" : usernameField.getText();
        int resultCount = result != null && result.status() == MojangProfileLookup.Status.SUCCESS ? result.entries().size() : 0;
        configureScrollableContent(module, scaled(96 + resultCount * RESULT_SPACING));
        int formWidth = Math.min(scaled(360), width - scaled(48));
        int formX = (width - formWidth) / 2;
        usernameField = addScrollableChild(new TextFieldWidget(textRenderer, formX, contentY(scaled(14)), formWidth,
                STANDARD_BUTTON_HEIGHT, Text.translatable("dmls.field.player_igns")), scaled(14));
        usernameField.setMaxLength(256);
        usernameField.setText(savedInput);
        usernameField.setSuggestion(savedInput.isEmpty() ? Text.translatable("dmls.placeholder.player_names").getString() : null);
        usernameField.setChangedListener(value -> {
            usernameField.setSuggestion(value.isEmpty() ? Text.translatable("dmls.placeholder.player_names").getString() : null);
            validationMessage = Text.empty();
            updateSubmitState();
        });
        setInitialFocus(usernameField);

        if (isOutsideWorld() && resultCount > 0) {
            int copyWidth = scaled(58);
            for (int index = 0; index < result.entries().size(); index++) {
                MojangProfileLookup.Entry entry = result.entries().get(index);
                if (!entry.found()) continue;
                int offset = scaled(RESULT_START + index * RESULT_SPACING);
                addScrollableChild(ButtonWidget.builder(Text.translatable("dmls.button.copy"),
                                button -> client.keyboard.setClipboard(entry.uuid()))
                        .dimensions(formX + formWidth - copyWidth, contentY(offset), copyWidth, STANDARD_BUTTON_HEIGHT).build(), offset);
            }
        }

        addDrawableChild(ButtonWidget.builder(ScreenTexts.BACK, button -> close())
                .dimensions(leftPairedButtonX(), footerButtonY(), pairedButtonWidth(), STANDARD_BUTTON_HEIGHT).build());
        submitButton = addDrawableChild(ButtonWidget.builder(Text.translatable("dmls.button.lookup"), button -> submit())
                .dimensions(rightPairedButtonX(), footerButtonY(), pairedButtonWidth(), STANDARD_BUTTON_HEIGHT).build());
        updateSubmitState();
    }

    private void submit() {
        String input = usernameField.getText().trim();
        if (!isOutsideWorld()) {
            if (module.submit(client, input)) closeToGame();
            return;
        }

        UuidLookupModule.StartResult started = module.submitToScreen(client, input, this::acceptResult);
        validationMessage = switch (started.status()) {
            case VALID -> Text.translatable("dmls.screen.uuid.looking_up");
            case EMPTY -> Text.translatable("dmls.validation.player_igns");
            case INVALID -> Text.translatable("dmls.validation.uuid.invalid_names", String.join(", ", started.invalidNames()));
            case TOO_MANY -> Text.translatable("dmls.validation.uuid.too_many", MojangProfileLookup.MAX_USERNAMES);
            case ACTIVE -> Text.translatable("dmls.chat.uuid.active");
        };
        if (started.status() == UuidLookupModule.InputStatus.VALID) {
            result = null;
            clearAndInit();
            return;
        }
        updateSubmitState();
    }

    private void acceptResult(MojangProfileLookup.BatchResult lookupResult) {
        result = lookupResult;
        validationMessage = switch (lookupResult.status()) {
            case SUCCESS -> Text.empty();
            case RATE_LIMITED, MALFORMED_RESPONSE, TIMEOUT, NETWORK_ERROR, INVALID_REQUEST ->
                    Text.translatable(UuidLookupModule.errorTranslationKey(lookupResult.status()));
        };
        if (client.currentScreen == this) clearAndInit();
    }

    private void updateSubmitState() {
        if (submitButton != null && usernameField != null) {
            submitButton.active = UuidLookupModule.parseInput(usernameField.getText()).status() == UuidLookupModule.InputStatus.VALID
                    && !module.isLookupActive();
        }
    }

    private boolean isOutsideWorld() {
        return client == null || client.world == null || client.player == null;
    }

    @Override
    public void tick() {
        updateSubmitState();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderMenuBackground(context);
        renderModuleHeader(context, module);
        beginContentScissor(context);
        int formWidth = Math.min(scaled(360), width - scaled(48));
        int formX = (width - formWidth) / 2;
        drawContentText(context, Text.translatable("dmls.field.player_igns.label"), formX, contentY(0), 0xFFCCCCCC);

        int messageY = contentY(scaled(48));
        if (!validationMessage.getString().isEmpty()) {
            drawContentText(context, validationMessage, formX, messageY,
                    module.isLookupActive() ? 0xFFAAAAAA : 0xFFFF5555);
        }

        if (isOutsideWorld() && result != null && result.status() == MojangProfileLookup.Status.SUCCESS) {
            int textWidth = formWidth - scaled(66);
            for (int index = 0; index < result.entries().size(); index++) {
                MojangProfileLookup.Entry entry = result.entries().get(index);
                int y = contentY(scaled(RESULT_START + index * RESULT_SPACING));
                Text line = entry.found()
                        ? Text.literal(entry.username() + ": " + entry.uuid())
                        : Text.translatable("dmls.screen.uuid.not_found", entry.requestedUsername());
                if (isContentVisible(y, textRenderer.fontHeight)) {
                    context.enableScissor(formX, y, formX + textWidth, y + STANDARD_BUTTON_HEIGHT);
                    context.drawTextWithShadow(textRenderer, line, formX, y + scaled(6), entry.found() ? 0xFFFFAA00 : 0xFFFF5555);
                    context.disableScissor();
                }
            }
        }
        endContentScissor(context);
        super.render(context, mouseX, mouseY, delta);
    }

    private void drawContentText(DrawContext context, Text text, int x, int y, int color) {
        if (isContentVisible(y, textRenderer.fontHeight)) context.drawTextWithShadow(textRenderer, text, x, y, color);
    }
}
