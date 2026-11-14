package com.duperknight.client.gui.modules;

import com.duperknight.client.gui.DMLSMenuScreen;
import com.duperknight.client.gui.DangerReviewScreen;
import com.duperknight.client.gui.widgets.DropdownWidget;
import com.duperknight.client.modules.DemoWaveModule;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/** Form for invoking a demotion wave. */
public final class DemoWaveScreen extends DMLSMenuScreen {
    private final DemoWaveModule module;
    private EditBox ignsField;
    private String rank = DemoWaveModule.ranks().get(0);
    private Button submitButton;
    private Component validationMessage = Component.empty();

    public DemoWaveScreen(Screen parent, DemoWaveModule module) {
        super(Component.translatable("dmls.module.demo_wave.name"), parent);
        this.module = module;
    }

    @Override
    protected void init() {
        String savedNames = ignsField == null ? "" : ignsField.getValue();
        configureScrollableContent(module, scaled(110));
        int formWidth = Math.min(scaled(360), width - scaled(48));
        int formX = (width - formWidth) / 2;
        ignsField = addScrollableChild(new EditBox(font, formX, contentY(scaled(14)), formWidth, STANDARD_BUTTON_HEIGHT,
                Component.translatable("dmls.field.player_igns")), scaled(14));
        ignsField.setMaxLength(1024);
        ignsField.setValue(savedNames);
        ignsField.setSuggestion(Component.translatable("dmls.placeholder.player_names_many").getString());
        ignsField.setResponder(value -> {
            ignsField.setSuggestion(value.isEmpty()
                    ? Component.translatable("dmls.placeholder.player_names_many").getString() : null);
            validationMessage = Component.empty();
        });
        setInitialFocus(ignsField);

        addScrollableDropdownChild(DropdownWidget.builder(
                        Component.translatable("dmls.field.rank"), DemoWaveModule.ranks(), rank,
                        Component::literal, (dropdown, value) -> {
                            rank = value;
                            validationMessage = Component.empty();
                        })
                .dimensions(formX, contentY(scaled(48)), formWidth, STANDARD_BUTTON_HEIGHT)
                .showOptionLabel(true)
                .build(), scaled(48));

        addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, button -> onClose())
                .bounds(leftPairedButtonX(), footerButtonY(), pairedButtonWidth(), STANDARD_BUTTON_HEIGHT).build());
        submitButton = registerCommandControl(addRenderableWidget(Button.builder(
                        Component.translatable("dmls.button.demote"), button -> submit())
                .bounds(rightPairedButtonX(), footerButtonY(), pairedButtonWidth(), STANDARD_BUTTON_HEIGHT).build()));
    }

    private void submit() {
        String input = ignsField.getValue().trim();
        if (input.isEmpty()) {
            validationMessage = Component.translatable("dmls.validation.player_igns");
            return;
        }
        DemoWaveModule.StageResult staged = module.stage(minecraft, rank, input, false);
        if (!staged.staged()) {
            validationMessage = switch (staged.status()) {
                case INVALID -> staged.request().status() == DemoWaveModule.PreparationStatus.TOO_MANY
                        ? Component.translatable("dmls.chat.demo.too_many", DemoWaveModule.MAX_PLAYERS)
                        : Component.translatable("dmls.validation.player_igns");
                case BLOCKED -> Component.translatable("dmls.validation.operation.blocked");
                case BUSY -> Component.translatable("dmls.validation.operation.busy");
                case STAGED -> Component.empty();
            };
            return;
        }
        String token = staged.token();
        minecraft.gui.setScreen(new DangerReviewScreen(this,
                Component.translatable("dmls.screen.demo.review_title"),
                module.previewLines(staged.request()),
                Component.translatable("dmls.button.confirm_demotion"),
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
            context.text(font, Component.translatable("dmls.field.player_igns.label"), ignsField.getX(), labelY, 0xFFCCCCCC);
        }
        int warningY = contentY(scaled(80));
        if (isContentVisible(warningY, font.lineHeight)) {
            context.centeredText(font, Component.translatable("dmls.module.demo_wave.warning"),
                    width / 2, warningY, 0xFFFFAA00);
        }
        int validationY = contentY(scaled(96));
        if (!validationMessage.getString().isEmpty() && isContentVisible(validationY, font.lineHeight)) {
            context.centeredText(font, validationMessage, width / 2, validationY, 0xFFFF5555);
        }
        endContentScissor(context);
        super.extractRenderState(context, mouseX, mouseY, delta);
    }
}
