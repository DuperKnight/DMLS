package com.duperknight.client.gui.modules;

import com.duperknight.client.gui.DMLSMenuScreen;
import com.duperknight.client.modules.EventSimultaneousCommandModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;

/** Enter two commands and run them one after another. */
public final class EventSimultaneousCommandScreen extends DMLSMenuScreen {
    private final EventSimultaneousCommandModule module;
    private TextFieldWidget commandOneField;
    private TextFieldWidget commandTwoField;
    private Text status = Text.empty();

    public EventSimultaneousCommandScreen(Screen parent, EventSimultaneousCommandModule module) {
        super(Text.translatable("dmls.module.event_simultaneous.name"), parent);
        this.module = module;
    }

    @Override
    protected void init() {
        configureScrollableContent(module, scaled(98));
        int controlWidth = scaled(200);
        int x = width / 2 - controlWidth / 2;

        commandOneField = new TextFieldWidget(textRenderer, x, contentY(0), controlWidth, STANDARD_BUTTON_HEIGHT,
                Text.translatable("dmls.module.event_simultaneous.command_one_field"));
        commandOneField.setMaxLength(EventSimultaneousCommandModule.MAX_COMMAND_LENGTH);
        commandOneField.setPlaceholder(Text.translatable("dmls.module.event_simultaneous.command_one_placeholder"));
        addScrollableChild(commandOneField, 0);

        commandTwoField = new TextFieldWidget(textRenderer, x, contentY(scaled(24)), controlWidth, STANDARD_BUTTON_HEIGHT,
                Text.translatable("dmls.module.event_simultaneous.command_two_field"));
        commandTwoField.setMaxLength(EventSimultaneousCommandModule.MAX_COMMAND_LENGTH);
        commandTwoField.setPlaceholder(Text.translatable("dmls.module.event_simultaneous.command_two_placeholder"));
        addScrollableChild(commandTwoField, scaled(24));

        addScrollableChild(ButtonWidget.builder(Text.translatable("dmls.module.event_simultaneous.run"), button -> {
            MinecraftClient client = MinecraftClient.getInstance();
            EventSimultaneousCommandModule.RunResult result = module.run(client,
                    commandOneField.getText(), commandTwoField.getText());
            status = statusFor(result);
        }).dimensions(x, contentY(scaled(48)), controlWidth, STANDARD_BUTTON_HEIGHT).build(), scaled(48));

        addDrawableChild(ButtonWidget.builder(ScreenTexts.BACK, button -> close())
                .dimensions(width / 2 - scaled(75), footerButtonY(), scaled(150), STANDARD_BUTTON_HEIGHT).build());
    }

    private Text statusFor(EventSimultaneousCommandModule.RunResult result) {
        return switch (result) {
            case SENT -> Text.translatable("dmls.chat.event_simultaneous.sent");
            case SIMULATED -> Text.translatable("dmls.chat.dry_run.status.on");
            case INVALID_COMMAND_ONE -> Text.translatable("dmls.validation.event_simultaneous.command_one");
            case INVALID_COMMAND_TWO -> Text.translatable("dmls.validation.event_simultaneous.command_two");
            case RANK_BLOCKED -> Text.translatable("dmls.chat.department.required",
                    com.duperknight.client.modules.StaffDepartment.EVENTS.displayName());
            case SERVER_BLOCKED -> Text.translatable("dmls.validation.server_blocked");
        };
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderMenuBackground(context);
        renderModuleHeader(context, module);
        beginContentScissor(context);
        int statusY = contentY(scaled(76));
        if (!status.getString().isEmpty() && isContentVisible(statusY, textRenderer.fontHeight)) {
            context.drawCenteredTextWithShadow(textRenderer, status, width / 2, statusY, 0xFFDDDDDD);
        }
        endContentScissor(context);
        super.render(context, mouseX, mouseY, delta);
    }
}