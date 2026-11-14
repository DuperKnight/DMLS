package com.duperknight.client.gui.modules;

import com.duperknight.client.gui.DMLSMenuScreen;
import com.duperknight.client.modules.ContainerScanModule;
import com.duperknight.client.modules.session.AbstractCoreProtectScanModule;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/** Form for invoking a container scan. */
public final class ContainerScanScreen extends DMLSMenuScreen {
    private final ContainerScanModule module;
    private EditBox ignField;
    private EditBox timeField;
    private EditBox radiusField;
    private Button submitButton;
    private Component validationMessage = Component.empty();

    public ContainerScanScreen(Screen parent, ContainerScanModule module) {
        super(Component.translatable("dmls.module.containers.name"), parent);
        this.module = module;
    }

    @Override
    protected void init() {
        configureScrollableContent(module, scaled(140));
        int formWidth = Math.min(scaled(360), width - scaled(48));
        int formX = (width - formWidth) / 2;

        ignField = addScrollableChild(new EditBox(font, formX, contentY(scaled(14)), formWidth,
                STANDARD_BUTTON_HEIGHT, Component.translatable("dmls.field.player_ign")), scaled(14));
        ignField.setMaxLength(16);
        ignField.setSuggestion(Component.translatable("dmls.placeholder.containers.ign").getString());
        ignField.setResponder(value -> {
            ignField.setSuggestion(value.isEmpty() ? Component.translatable("dmls.placeholder.containers.ign").getString() : null);
            validationMessage = Component.empty();
        });
        setInitialFocus(ignField);

        int halfWidth = formWidth / 2 - scaled(2);
        timeField = addScrollableChild(new EditBox(font, formX, contentY(scaled(60)), halfWidth,
                STANDARD_BUTTON_HEIGHT, Component.empty()), scaled(60));
        timeField.setMaxLength(16);
        timeField.setSuggestion("7d");
        timeField.setResponder(value -> {
            timeField.setSuggestion(value.isEmpty() ? "7d" : null);
            validationMessage = Component.empty();
        });

        radiusField = addScrollableChild(new EditBox(font, formX + halfWidth + scaled(4), contentY(scaled(60)), halfWidth,
                STANDARD_BUTTON_HEIGHT, Component.empty()), scaled(60));
        radiusField.setMaxLength(8);
        radiusField.setSuggestion("20");
        radiusField.setResponder(value -> {
            radiusField.setSuggestion(value.isEmpty() ? "20" : null);
            validationMessage = Component.empty();
        });

        addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, button -> onClose())
                .bounds(leftPairedButtonX(), footerButtonY(), pairedButtonWidth(), STANDARD_BUTTON_HEIGHT).build());
        submitButton = registerCommandControl(addRenderableWidget(Button.builder(
                        Component.translatable("dmls.button.containers.scan"), button -> submit())
                .bounds(rightPairedButtonX(), footerButtonY(), pairedButtonWidth(), STANDARD_BUTTON_HEIGHT).build()));
    }

    private void submit() {
        if (timeField.getValue().trim().isEmpty() || radiusField.getValue().trim().isEmpty()) {
            validationMessage = Component.translatable("dmls.validation.containers");
            return;
        }
        AbstractCoreProtectScanModule.SubmissionResult result = module.submit(
                minecraft, ignField.getValue(), timeField.getValue(), radiusField.getValue());
        validationMessage = switch (result) {
            case INVALID_IGN -> Component.translatable("dmls.chat.common.invalid_ign");
            case INVALID_TIME -> Component.translatable("dmls.validation.co.time");
            case INVALID_RADIUS -> Component.translatable("dmls.validation.co.radius");
            case RANK_BLOCKED -> Component.translatable("dmls.validation.required_rank");
            case SERVER_BLOCKED -> Component.translatable("dmls.validation.server_blocked");
            case BUSY -> Component.translatable("dmls.validation.scan.busy");
            case FAILED -> Component.translatable("dmls.validation.scan.start_failed");
            case STARTED, SIMULATED -> Component.empty();
        };
        if (result.accepted()) {
            closeToGame();
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        renderMenuBackground(context);
        renderModuleHeader(context, module);
        beginContentScissor(context);
        int ignLabelY = contentY(0);
        if (isContentVisible(ignLabelY, font.lineHeight)) {
            context.text(font, Component.translatable("dmls.field.containers.ign.label"), ignField.getX(), ignLabelY, 0xFFCCCCCC);
        }
        int rowLabelY = contentY(scaled(48));
        if (isContentVisible(rowLabelY, font.lineHeight)) {
            context.text(font, Component.translatable("dmls.field.co.time.label"), timeField.getX(), rowLabelY, 0xFFCCCCCC);
            context.text(font, Component.translatable("dmls.field.co.radius.label"), radiusField.getX(), rowLabelY, 0xFFCCCCCC);
        }
        int validationY = contentY(scaled(96));
        if (!validationMessage.getString().isEmpty() && isContentVisible(validationY, font.lineHeight)) {
            context.centeredText(font, validationMessage, width / 2, validationY, 0xFFFF5555);
        }
        endContentScissor(context);
        super.extractRenderState(context, mouseX, mouseY, delta);
    }
}
