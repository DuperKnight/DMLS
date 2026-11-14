package com.duperknight.client.gui.modules;

import com.duperknight.client.gui.DMLSMenuScreen;
import com.duperknight.client.modules.UuidLookupModule;
import com.duperknight.client.utils.MojangProfileLookup;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/** Form for local Mojang username-to-UUID lookups. */
public final class UuidLookupScreen extends DMLSMenuScreen {
    private static final int RESULT_START = 70;
    private static final int RESULT_SPACING = 28;
    private final UuidLookupModule module;
    private EditBox usernameField;
    private Button submitButton;
    private Component validationMessage = Component.empty();
    private MojangProfileLookup.BatchResult result;

    public UuidLookupScreen(Screen parent, UuidLookupModule module) {
        super(Component.translatable("dmls.module.uuid.name"), parent);
        this.module = module;
    }

    @Override
    protected void init() {
        String savedInput = usernameField == null ? "" : usernameField.getValue();
        int resultCount = result != null && result.status() == MojangProfileLookup.Status.SUCCESS ? result.entries().size() : 0;
        configureScrollableContent(module, scaled(96 + resultCount * RESULT_SPACING));
        int formWidth = Math.min(scaled(360), width - scaled(48));
        int formX = (width - formWidth) / 2;
        usernameField = addScrollableChild(new EditBox(font, formX, contentY(scaled(14)), formWidth,
                STANDARD_BUTTON_HEIGHT, Component.translatable("dmls.field.player_igns")), scaled(14));
        usernameField.setMaxLength(256);
        usernameField.setValue(savedInput);
        usernameField.setSuggestion(savedInput.isEmpty() ? Component.translatable("dmls.placeholder.player_names").getString() : null);
        usernameField.setResponder(value -> {
            usernameField.setSuggestion(value.isEmpty() ? Component.translatable("dmls.placeholder.player_names").getString() : null);
            validationMessage = Component.empty();
            updateSubmitState();
        });
        setInitialFocus(usernameField);

        if (isOutsideWorld() && resultCount > 0) {
            int copyWidth = scaled(58);
            for (int index = 0; index < result.entries().size(); index++) {
                MojangProfileLookup.Entry entry = result.entries().get(index);
                if (!entry.found()) continue;
                int offset = scaled(RESULT_START + index * RESULT_SPACING);
                addScrollableChild(Button.builder(Component.translatable("dmls.button.copy"),
                                button -> minecraft.keyboardHandler.setClipboard(entry.uuid()))
                        .bounds(formX + formWidth - copyWidth, contentY(offset), copyWidth, STANDARD_BUTTON_HEIGHT).build(), offset);
            }
        }

        addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, button -> onClose())
                .bounds(leftPairedButtonX(), footerButtonY(), pairedButtonWidth(), STANDARD_BUTTON_HEIGHT).build());
        submitButton = addRenderableWidget(Button.builder(Component.translatable("dmls.button.lookup"), button -> submit())
                .bounds(rightPairedButtonX(), footerButtonY(), pairedButtonWidth(), STANDARD_BUTTON_HEIGHT).build());
        updateSubmitState();
    }

    private void submit() {
        String input = usernameField.getValue().trim();
        if (!isOutsideWorld()) {
            if (module.submit(minecraft, input)) closeToGame();
            return;
        }

        UuidLookupModule.StartResult started = module.submitToScreen(minecraft, input, this::acceptResult);
        validationMessage = switch (started.status()) {
            case VALID -> Component.translatable("dmls.screen.uuid.looking_up");
            case EMPTY -> Component.translatable("dmls.validation.player_igns");
            case INVALID -> Component.translatable("dmls.validation.uuid.invalid_names", String.join(", ", started.invalidNames()));
            case TOO_MANY -> Component.translatable("dmls.validation.uuid.too_many", MojangProfileLookup.MAX_USERNAMES);
            case ACTIVE -> Component.translatable("dmls.chat.uuid.active");
        };
        if (started.status() == UuidLookupModule.InputStatus.VALID) {
            result = null;
            rebuildWidgets();
            return;
        }
        updateSubmitState();
    }

    private void acceptResult(MojangProfileLookup.BatchResult lookupResult) {
        result = lookupResult;
        validationMessage = switch (lookupResult.status()) {
            case SUCCESS -> Component.empty();
            case RATE_LIMITED, MALFORMED_RESPONSE, TIMEOUT, NETWORK_ERROR, INVALID_REQUEST ->
                    Component.translatable(UuidLookupModule.errorTranslationKey(lookupResult.status()));
        };
        if (minecraft.gui.screen() == this) rebuildWidgets();
    }

    private void updateSubmitState() {
        if (submitButton != null && usernameField != null) {
            submitButton.active = UuidLookupModule.parseInput(usernameField.getValue()).status() == UuidLookupModule.InputStatus.VALID
                    && !module.isLookupActive();
        }
    }

    private boolean isOutsideWorld() {
        return minecraft == null || minecraft.level == null || minecraft.player == null;
    }

    @Override
    public void tick() {
        updateSubmitState();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        renderMenuBackground(context);
        renderModuleHeader(context, module);
        beginContentScissor(context);
        int formWidth = Math.min(scaled(360), width - scaled(48));
        int formX = (width - formWidth) / 2;
        drawContentText(context, Component.translatable("dmls.field.player_igns.label"), formX, contentY(0), 0xFFCCCCCC);

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
                Component line = entry.found()
                        ? Component.literal(entry.username() + ": " + entry.uuid())
                        : Component.translatable("dmls.screen.uuid.not_found", entry.requestedUsername());
                if (isContentVisible(y, font.lineHeight)) {
                    context.enableScissor(formX, y, formX + textWidth, y + STANDARD_BUTTON_HEIGHT);
                    context.text(font, line, formX, y + scaled(6), entry.found() ? 0xFFFFAA00 : 0xFFFF5555);
                    context.disableScissor();
                }
            }
        }
        endContentScissor(context);
        super.extractRenderState(context, mouseX, mouseY, delta);
    }

    private void drawContentText(GuiGraphicsExtractor context, Component text, int x, int y, int color) {
        if (isContentVisible(y, font.lineHeight)) context.text(font, text, x, y, color);
    }
}
