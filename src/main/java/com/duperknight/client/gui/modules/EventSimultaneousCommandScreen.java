package com.duperknight.client.gui.modules;

import com.duperknight.client.gui.DMLSMenuScreen;
import com.duperknight.client.modules.EventSimultaneousCommandModule;
import com.duperknight.client.utils.ClientUtils;
import com.duperknight.client.utils.DMLSConfig;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/** Standard DMLS form for building and running a short sequence of commands. */
public final class EventSimultaneousCommandScreen extends DMLSMenuScreen {
    private static final int ROW_HEIGHT_UNSCALED = 46;
    private static final List<String> PLACEHOLDER_KEYS = List.of(
            "dmls.module.event_simultaneous.command_placeholder.1",
            "dmls.module.event_simultaneous.command_placeholder.2",
            "dmls.module.event_simultaneous.command_placeholder.3",
            "dmls.module.event_simultaneous.command_placeholder.4",
            "dmls.module.event_simultaneous.command_placeholder.5"
    );

    private final EventSimultaneousCommandModule module;
    private final List<String> commands = new ArrayList<>(List.of("", ""));
    private final List<TextFieldWidget> commandFields = new ArrayList<>();
    private ButtonWidget runButton;
    private Text validation = Text.empty();
    private Text status = Text.empty();

    public EventSimultaneousCommandScreen(Screen parent, EventSimultaneousCommandModule module) {
        super(Text.translatable("dmls.module.event_simultaneous.name"), parent);
        this.module = module;
    }

    @Override
    protected void init() {
        commandFields.clear();
        int addButtonOffset = commands.size() * scaled(ROW_HEIGHT_UNSCALED);
        configureScrollableContent(module, addButtonOffset + scaled(30));

        int formWidth = Math.min(scaled(360), width - scaled(48));
        int formX = (width - formWidth) / 2;
        int removeWidth = scaled(20);
        int gap = scaled(4);
        boolean removable = commands.size() > EventSimultaneousCommandModule.MIN_COMMANDS;

        for (int index = 0; index < commands.size(); index++) {
            int commandIndex = index;
            int rowOffset = index * scaled(ROW_HEIGHT_UNSCALED);
            int fieldWidth = removable ? formWidth - removeWidth - gap : formWidth;
            TextFieldWidget field = addScrollableChild(new TextFieldWidget(textRenderer, formX,
                    contentY(rowOffset + scaled(14)), fieldWidth, STANDARD_BUTTON_HEIGHT,
                    Text.translatable("dmls.module.event_simultaneous.command_field", index + 1)),
                    rowOffset + scaled(14));
            field.setMaxLength(EventSimultaneousCommandModule.MAX_COMMAND_LENGTH);
            field.setText(commands.get(index));
            updateSuggestion(field, index);
            field.setChangedListener(value -> {
                commands.set(commandIndex, value);
                updateSuggestion(field, commandIndex);
                status = Text.empty();
                refreshValidation();
            });
            commandFields.add(field);

            if (removable) {
                addScrollableChild(ButtonWidget.builder(Text.literal("✕"), button -> removeCommand(commandIndex))
                        .dimensions(formX + formWidth - removeWidth, contentY(rowOffset + scaled(14)),
                                removeWidth, STANDARD_BUTTON_HEIGHT).build(), rowOffset + scaled(14));
            }
        }

        ButtonWidget addButton = addScrollableChild(ButtonWidget.builder(
                        Text.translatable("dmls.module.event_simultaneous.add"), button -> addCommand())
                .dimensions(formX, contentY(addButtonOffset), formWidth, STANDARD_BUTTON_HEIGHT).build(),
                addButtonOffset);
        addButton.active = commands.size() < EventSimultaneousCommandModule.MAX_COMMANDS;

        addDrawableChild(ButtonWidget.builder(ScreenTexts.BACK, button -> close())
                .dimensions(leftPairedButtonX(), footerButtonY(), pairedButtonWidth(), STANDARD_BUTTON_HEIGHT).build());
        runButton = addDrawableChild(ButtonWidget.builder(
                        Text.translatable("dmls.module.event_simultaneous.run"), button -> runCommands())
                .dimensions(rightPairedButtonX(), footerButtonY(), pairedButtonWidth(), STANDARD_BUTTON_HEIGHT).build());

        if (!commandFields.isEmpty()) {
            setInitialFocus(commandFields.get(0));
        }
        refreshValidation();
    }

    private void addCommand() {
        if (commands.size() >= EventSimultaneousCommandModule.MAX_COMMANDS) return;
        commands.add("");
        status = Text.empty();
        clearAndInit();
        scrollContentToBottom();
        if (!commandFields.isEmpty()) {
            setFocused(commandFields.get(commandFields.size() - 1));
        }
    }

    private void removeCommand(int index) {
        if (commands.size() <= EventSimultaneousCommandModule.MIN_COMMANDS) return;
        commands.remove(index);
        status = Text.empty();
        clearAndInit();
    }

    private void runCommands() {
        EventSimultaneousCommandModule.RunResult result = module.run(client, commands);
        status = statusFor(result);
        if (result == EventSimultaneousCommandModule.RunResult.SENT
                || result == EventSimultaneousCommandModule.RunResult.SIMULATED) {
            closeToGame();
        }
    }

    private void refreshValidation() {
        boolean allEmpty = commands.stream().allMatch(String::isBlank);
        boolean anyBlank = commands.stream().anyMatch(String::isBlank);
        boolean allValid = EventSimultaneousCommandModule.validateCommands(commands).isPresent();

        if (allEmpty) {
            validation = Text.empty();
        } else if (anyBlank) {
            validation = Text.translatable("dmls.validation.event_simultaneous.incomplete");
        } else if (!allValid) {
            validation = Text.translatable("dmls.validation.event_simultaneous.command");
        } else {
            validation = Text.empty();
        }

        if (runButton != null) {
            runButton.active = allValid && (DMLSConfig.dryRun() || !ClientUtils.isNotConnected(client));
        }
    }

    private void updateSuggestion(TextFieldWidget field, int index) {
        field.setSuggestion(field.getText().isEmpty()
                ? Text.translatable(PLACEHOLDER_KEYS.get(index)).getString() : null);
    }

    private Text statusFor(EventSimultaneousCommandModule.RunResult result) {
        return switch (result) {
            case SENT -> Text.translatable("dmls.chat.event_simultaneous.sent", commands.size());
            case SIMULATED -> Text.translatable("dmls.chat.dry_run.status.on");
            case INVALID_COMMAND_COUNT -> Text.translatable("dmls.validation.event_simultaneous.count",
                    EventSimultaneousCommandModule.MIN_COMMANDS, EventSimultaneousCommandModule.MAX_COMMANDS);
            case INVALID_COMMAND -> Text.translatable("dmls.validation.event_simultaneous.command");
            case RANK_BLOCKED -> Text.translatable("dmls.chat.department.required",
                    com.duperknight.client.modules.StaffDepartment.EVENTS.displayName());
            case SERVER_BLOCKED -> Text.translatable("dmls.validation.server_blocked");
        };
    }

    @Override
    public void tick() {
        refreshValidation();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderMenuBackground(context);
        renderModuleHeader(context, module);
        beginContentScissor(context);
        int formWidth = Math.min(scaled(360), width - scaled(48));
        int formX = (width - formWidth) / 2;
        for (int index = 0; index < commands.size(); index++) {
            int labelY = contentY(index * scaled(ROW_HEIGHT_UNSCALED));
            if (isContentVisible(labelY, textRenderer.fontHeight)) {
                context.drawTextWithShadow(textRenderer,
                        Text.translatable("dmls.module.event_simultaneous.command_field", index + 1),
                        formX, labelY, 0xFFCCCCCC);
            }
        }
        endContentScissor(context);

        Text footerStatus = status.getString().isEmpty() ? validation : status;
        if (!footerStatus.getString().isEmpty()) {
            context.drawCenteredTextWithShadow(textRenderer, footerStatus, width / 2,
                    footerButtonY() - scaled(13), 0xFFFF5555);
        }
        super.render(context, mouseX, mouseY, delta);
    }
}
