package com.duperknight.client.gui.modules;

import com.duperknight.client.utils.EditBoxUtils;

import com.duperknight.client.gui.DMLSMenuScreen;
import com.duperknight.client.modules.EventSimultaneousCommandModule;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/** Enter 2-5 commands, a repeat count, and run them one after another that many times. */
public final class EventSimultaneousCommandScreen extends DMLSMenuScreen {
    private static final int FIELD_SPACING = 24;
    private static final String DEFAULT_REPEAT_COUNT = "1";

    private final EventSimultaneousCommandModule module;
    private final List<EditBox> commandFields = new ArrayList<>(EventSimultaneousCommandModule.MAX_COMMANDS);
    private EditBox repeatCountField;
    private Component status = Component.empty();

    public EventSimultaneousCommandScreen(Screen parent, EventSimultaneousCommandModule module) {
        super(Component.translatable("dmls.module.event_simultaneous.name"), parent);
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

            EditBox field = new EditBox(font, x, contentY(offsetY), controlWidth,
                    STANDARD_BUTTON_HEIGHT, Component.translatable("dmls.module.event_simultaneous.command_field", slot));
            field.setMaxLength(EventSimultaneousCommandModule.MAX_COMMAND_LENGTH);
            field.setHint(Component.translatable(required
                    ? "dmls.module.event_simultaneous.command_placeholder_required"
                    : "dmls.module.event_simultaneous.command_placeholder_optional"));
            String existing = stored.get(i);
            if (existing != null) {
                field.setValue(existing);
            }
            commandFields.add(field);
            addScrollableChild(field, offsetY);
        }

        int repeatFieldY = scaled(FIELD_SPACING) * EventSimultaneousCommandModule.MAX_COMMANDS;
        repeatCountField = new EditBox(font, x, contentY(repeatFieldY), controlWidth,
                STANDARD_BUTTON_HEIGHT, Component.translatable("dmls.module.event_simultaneous.repeat_count_field"));
        repeatCountField.setMaxLength(3);
        repeatCountField.setHint(Component.translatable("dmls.module.event_simultaneous.repeat_count_placeholder",
                EventSimultaneousCommandModule.MIN_REPEAT_COUNT, EventSimultaneousCommandModule.MAX_REPEAT_COUNT));
        repeatCountField.setValue(DEFAULT_REPEAT_COUNT);
        EditBoxUtils.setFilter(repeatCountField, text -> text.isEmpty() || text.chars().allMatch(Character::isDigit));
        addScrollableChild(repeatCountField, repeatFieldY);

        int runButtonY = fieldBlockHeight;
        addScrollableChild(Button.builder(Component.translatable("dmls.module.event_simultaneous.run"), button -> {
            Minecraft client = Minecraft.getInstance();
            Integer repeatCount = parseRepeatCount();
            if (repeatCount == null) {
                status = Component.translatable("dmls.validation.event_simultaneous.repeat_count",
                        EventSimultaneousCommandModule.MIN_REPEAT_COUNT, EventSimultaneousCommandModule.MAX_REPEAT_COUNT);
                return;
            }
            EventSimultaneousCommandModule.RunResult result = module.run(client, collectCommands(), repeatCount);
            status = statusFor(result);
        }).bounds(x, contentY(runButtonY), controlWidth, STANDARD_BUTTON_HEIGHT).build(), runButtonY);

        addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, button -> onClose())
                .bounds(width / 2 - scaled(75), footerButtonY(), scaled(150), STANDARD_BUTTON_HEIGHT).build());
    }

    /** Collects the entered commands, dropping trailing blank slots. */
    private List<String> collectCommands() {
        List<String> commands = new ArrayList<>(commandFields.size());
        for (EditBox field : commandFields) {
            commands.add(field.getValue());
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
        String text = repeatCountField.getValue().strip();
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

    private Component statusFor(EventSimultaneousCommandModule.RunResult result) {
        return switch (result) {
            case SENT -> Component.translatable("dmls.chat.event_simultaneous.sent");
            case SIMULATED -> Component.translatable("dmls.chat.dry_run.status.on");
            case INVALID_COMMAND_COUNT -> Component.translatable("dmls.validation.event_simultaneous.command_count",
                    EventSimultaneousCommandModule.MIN_COMMANDS, EventSimultaneousCommandModule.MAX_COMMANDS);
            case INVALID_COMMAND -> Component.translatable("dmls.validation.event_simultaneous.command");
            case INVALID_REPEAT_COUNT -> Component.translatable("dmls.validation.event_simultaneous.repeat_count",
                    EventSimultaneousCommandModule.MIN_REPEAT_COUNT, EventSimultaneousCommandModule.MAX_REPEAT_COUNT);
            case RANK_BLOCKED -> Component.translatable("dmls.chat.department.required",
                    com.duperknight.client.modules.StaffDepartment.EVENTS.displayName());
            case SERVER_BLOCKED -> Component.translatable("dmls.validation.server_blocked");
        };
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        renderMenuBackground(context);
        renderModuleHeader(context, module);
        beginContentScissor(context);
        int statusY = contentY(scaled(FIELD_SPACING) * (EventSimultaneousCommandModule.MAX_COMMANDS + 1) + scaled(28));
        if (!status.getString().isEmpty() && isContentVisible(statusY, font.lineHeight)) {
            context.centeredText(font, status, width / 2, statusY, 0xFFDDDDDD);
        }
        endContentScissor(context);
        super.extractRenderState(context, mouseX, mouseY, delta);
    }
}