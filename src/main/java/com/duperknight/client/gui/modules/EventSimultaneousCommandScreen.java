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

import java.util.ArrayList;
import java.util.List;

/** Enter 2-5 commands, a repeat count, and run them one after another that many times. */
public final class EventSimultaneousCommandScreen extends DMLSMenuScreen {
    private static final int FIELD_SPACING = 24;
    private static final String DEFAULT_REPEAT_COUNT = "1";

    private final EventSimultaneousCommandModule module;
    private final List<TextFieldWidget> commandFields = new ArrayList<>(EventSimultaneousCommandModule.MAX_COMMANDS);
    private TextFieldWidget repeatCountField;
    private Text status = Text.empty();

    public EventSimultaneousCommandScreen(Screen parent, EventSimultaneousCommandModule module) {
        super(Text.translatable("dmls.module.event_simultaneous.name"), parent);
        this.module = module;
    }

    @Override
    protected void init() {
        // +1 field block for the repeat count row.
        int fieldBlockHeight = scaled(FIELD_SPACING) * (EventSimultaneousCommandModule.MAX_COMMANDS + 1);
        configureScrollableContent(module, fieldBlockHeight + scaled(50));
        int controlWidth = scaled(200);
        int x = width / 2 - controlWidth / 2;

        commandFields.clear();
        List<String> stored = module.storedCommands();
        for (int i = 0; i < EventSimultaneousCommandModule.MAX_COMMANDS; i++) {
            int slot = i + 1;
            int offsetY = scaled(FIELD_SPACING) * i;
            boolean required = slot <= EventSimultaneousCommandModule.MIN_COMMANDS;

            TextFieldWidget field = new TextFieldWidget(textRenderer, x, contentY(offsetY), controlWidth,
                    STANDARD_BUTTON_HEIGHT, Text.translatable("dmls.module.event_simultaneous.command_field", slot));
            field.setMaxLength(EventSimultaneousCommandModule.MAX_COMMAND_LENGTH);
            field.setPlaceholder(Text.translatable(required
                    ? "dmls.module.event_simultaneous.command_placeholder_required"
                    : "dmls.module.event_simultaneous.command_placeholder_optional"));
            String existing = stored.get(i);
            if (existing != null) {
                field.setText(existing);
            }
            commandFields.add(field);
            addScrollableChild(field, offsetY);
        }

        int repeatFieldY = scaled(FIELD_SPACING) * EventSimultaneousCommandModule.MAX_COMMANDS;
        repeatCountField = new TextFieldWidget(textRenderer, x, contentY(repeatFieldY), controlWidth,
                STANDARD_BUTTON_HEIGHT, Text.translatable("dmls.module.event_simultaneous.repeat_count_field"));
        repeatCountField.setMaxLength(3);
        repeatCountField.setPlaceholder(Text.translatable("dmls.module.event_simultaneous.repeat_count_placeholder",
                EventSimultaneousCommandModule.MIN_REPEAT_COUNT, EventSimultaneousCommandModule.MAX_REPEAT_COUNT));
        repeatCountField.setText(DEFAULT_REPEAT_COUNT);
        repeatCountField.setTextPredicate(text -> text.isEmpty() || text.chars().allMatch(Character::isDigit));
        addScrollableChild(repeatCountField, repeatFieldY);

        int runButtonY = fieldBlockHeight;
        addScrollableChild(ButtonWidget.builder(Text.translatable("dmls.module.event_simultaneous.run"), button -> {
            MinecraftClient client = MinecraftClient.getInstance();
            Integer repeatCount = parseRepeatCount();
            if (repeatCount == null) {
                status = Text.translatable("dmls.validation.event_simultaneous.repeat_count",
                        EventSimultaneousCommandModule.MIN_REPEAT_COUNT, EventSimultaneousCommandModule.MAX_REPEAT_COUNT);
                return;
            }
            EventSimultaneousCommandModule.RunResult result = module.run(client, collectCommands(), repeatCount);
            status = statusFor(result);
        }).dimensions(x, contentY(runButtonY), controlWidth, STANDARD_BUTTON_HEIGHT).build(), runButtonY);

        addDrawableChild(ButtonWidget.builder(ScreenTexts.BACK, button -> close())
                .dimensions(width / 2 - scaled(75), footerButtonY(), scaled(150), STANDARD_BUTTON_HEIGHT).build());
    }

    /** Collects the entered commands, dropping trailing blank slots. */
    private List<String> collectCommands() {
        List<String> commands = new ArrayList<>(commandFields.size());
        for (TextFieldWidget field : commandFields) {
            commands.add(field.getText());
        }
        int lastFilled = -1;
        for (int i = commands.size() - 1; i >= 0; i--) {
            if (!commands.get(i).isBlank()) {
                lastFilled = i;
                break;
            }
        }
        return new ArrayList<>(commands.subList(0, lastFilled + 1));
    }

    /** Parses the repeat count field, returning null if it's missing or out of range. */
    private Integer parseRepeatCount() {
        String text = repeatCountField.getText().strip();
        if (text.isEmpty()) {
            return EventSimultaneousCommandModule.MIN_REPEAT_COUNT;
        }
        try {
            int value = Integer.parseInt(text);
            return EventSimultaneousCommandModule.isValidRepeatCount(value) ? value : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Text statusFor(EventSimultaneousCommandModule.RunResult result) {
        return switch (result) {
            case SENT -> Text.translatable("dmls.chat.event_simultaneous.sent");
            case SIMULATED -> Text.translatable("dmls.chat.dry_run.status.on");
            case INVALID_COMMAND_COUNT -> Text.translatable("dmls.validation.event_simultaneous.command_count",
                    EventSimultaneousCommandModule.MIN_COMMANDS, EventSimultaneousCommandModule.MAX_COMMANDS);
            case INVALID_COMMAND -> Text.translatable("dmls.validation.event_simultaneous.command");
            case INVALID_REPEAT_COUNT -> Text.translatable("dmls.validation.event_simultaneous.repeat_count",
                    EventSimultaneousCommandModule.MIN_REPEAT_COUNT, EventSimultaneousCommandModule.MAX_REPEAT_COUNT);
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
        int statusY = contentY(scaled(FIELD_SPACING) * (EventSimultaneousCommandModule.MAX_COMMANDS + 1) + scaled(28));
        if (!status.getString().isEmpty() && isContentVisible(statusY, textRenderer.fontHeight)) {
            context.drawCenteredTextWithShadow(textRenderer, status, width / 2, statusY, 0xFFDDDDDD);
        }
        endContentScissor(context);
        super.render(context, mouseX, mouseY, delta);
    }
}