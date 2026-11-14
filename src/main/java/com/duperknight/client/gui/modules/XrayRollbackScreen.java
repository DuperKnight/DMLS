package com.duperknight.client.gui.modules;

import com.duperknight.client.gui.DMLSMenuScreen;
import com.duperknight.client.gui.DangerReviewScreen;
import com.duperknight.client.modules.XrayRollbackModule;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/** Form for invoking the xray rollback. */
public final class XrayRollbackScreen extends DMLSMenuScreen {
    private final XrayRollbackModule module;
    private EditBox ignField;
    private Button submitButton;
    private Component validationMessage = Component.empty();

    public XrayRollbackScreen(Screen parent, XrayRollbackModule module) {
        super(Component.translatable("dmls.module.xray.name"), parent);
        this.module = module;
    }

    @Override
    protected void init() {
        String savedIgn = ignField == null ? "" : ignField.getValue();
        configureScrollableContent(module, scaled(82));
        int formWidth = Math.min(scaled(360), width - scaled(48));
        int formX = (width - formWidth) / 2;
        ignField = addScrollableChild(new EditBox(font, formX, contentY(scaled(14)), formWidth, STANDARD_BUTTON_HEIGHT,
                Component.translatable("dmls.field.player_ign")), scaled(14));
        ignField.setMaxLength(16);
        ignField.setValue(savedIgn);
        ignField.setSuggestion(Component.translatable("dmls.placeholder.player_name").getString());
        ignField.setResponder(value -> {
            ignField.setSuggestion(value.isEmpty() ? Component.translatable("dmls.placeholder.player_name").getString() : null);
            validationMessage = Component.empty();
        });
        setInitialFocus(ignField);

        addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, button -> onClose())
                .bounds(leftPairedButtonX(), footerButtonY(), pairedButtonWidth(), STANDARD_BUTTON_HEIGHT).build());
        submitButton = registerCommandControl(addRenderableWidget(Button.builder(
                        Component.translatable("dmls.button.rollback"), button -> submit())
                .bounds(rightPairedButtonX(), footerButtonY(), pairedButtonWidth(), STANDARD_BUTTON_HEIGHT).build()));
    }

    private void submit() {
        String input = ignField.getValue().trim();
        if (input.isEmpty()) {
            validationMessage = Component.translatable("dmls.validation.player_ign");
            return;
        }
        XrayRollbackModule.StageResult staged = module.stage(minecraft, input, false);
        if (!staged.staged()) {
            validationMessage = switch (staged.status()) {
                case INVALID -> Component.translatable("dmls.validation.player_ign");
                case BLOCKED -> Component.translatable("dmls.validation.operation.blocked");
                case BUSY -> Component.translatable("dmls.validation.operation.busy");
                case STAGED -> Component.empty();
            };
            return;
        }

        String token = staged.token();
        minecraft.gui.setScreen(new DangerReviewScreen(this,
                Component.translatable("dmls.screen.xray.review_title"),
                module.previewLines(staged.request()),
                Component.translatable("dmls.button.confirm_rollback"),
                () -> module.isPending(token),
                () -> module.confirm(minecraft, token),
                () -> module.invalidatePending(token)));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        renderMenuBackground(context);
        renderModuleHeader(context, module);
        beginContentScissor(context);
        int labelY = contentY(0);
        if (isContentVisible(labelY, font.lineHeight)) {
            context.text(font, Component.translatable("dmls.field.player_ign.label"), ignField.getX(), labelY, 0xFFCCCCCC);
        }
        int warningY = contentY(scaled(48));
        if (isContentVisible(warningY, font.lineHeight)) {
            context.centeredText(font, Component.translatable("dmls.module.xray.warning"),
                    width / 2, warningY, 0xFFFFAA00);
        }
        int validationY = contentY(scaled(64));
        if (!validationMessage.getString().isEmpty() && isContentVisible(validationY, font.lineHeight)) {
            context.centeredText(font, validationMessage, width / 2, validationY, 0xFFFF5555);
        }
        endContentScissor(context);
        super.extractRenderState(context, mouseX, mouseY, delta);
    }
}
