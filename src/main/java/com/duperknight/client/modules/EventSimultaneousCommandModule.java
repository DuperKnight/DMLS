package com.duperknight.client.modules;

import com.duperknight.client.gui.modules.EventSimultaneousCommandScreen;
import com.duperknight.client.session.CommandDispatch;
import com.duperknight.client.utils.ChatUtils;
import com.duperknight.client.utils.ClientUtils;
import com.duperknight.client.utils.ServerGuard;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public final class EventSimultaneousCommandModule extends DMLSModule {
    private static final String PREFIX = "§8[§6DMLS - Simultaneous§8] §7";
    public static final int MIN_COMMANDS = 2;
    public static final int MAX_COMMANDS = 5;
    public static final int MAX_COMMAND_LENGTH = 256;

    private final List<String> storedCommands = new ArrayList<>(Collections.nCopies(MAX_COMMANDS, null));

    public enum RunResult {
        SENT,
        SIMULATED,
        INVALID_COMMAND_COUNT,
        INVALID_COMMAND,
        RANK_BLOCKED,
        SERVER_BLOCKED
    }

    public EventSimultaneousCommandModule() {
        super(StaffDepartment.EVENTS);
    }

    @Override
    public Text displayName() {
        return Text.translatable("dmls.module.event_simultaneous.name");
    }

    @Override
    public ItemStack icon() {
        return new ItemStack(Items.REPEATER);
    }

    @Override
    public List<Text> description() {
        return List.of(Text.translatable("dmls.module.event_simultaneous.description"));
    }

    @Override
    public ModuleCategory category() {
        return ModuleCategory.EVENTS;
    }

    @Override
    public void openScreen(MinecraftClient client, Screen parent) {
        client.setScreen(new EventSimultaneousCommandScreen(parent, this));
    }

    @Override
    public void register() {
        // No event listeners needed
    }

    public List<String> storedCommands() {
        return Collections.unmodifiableList(new ArrayList<>(storedCommands));
    }

    public String storedCommandOne() {
        return storedCommands.get(0);
    }

    public String storedCommandTwo() {
        return storedCommands.get(1);
    }

    /** Stores a command in a one-based slot for later use with runStored(). */
    public boolean setCommand(MinecraftClient client, int slot, String command) {
        Optional<String> validated = validateCommand(command);
        if (slot < 1 || slot > MAX_COMMANDS || validated.isEmpty()) {
            ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.validation.event_simultaneous.command");
            return false;
        }
        storedCommands.set(slot - 1, validated.get());
        ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.event_simultaneous.set", slot, validated.get());
        return true;
    }

    /** Backward-compatible alias for the original command1 subcommand. */
    public boolean setCommandOne(MinecraftClient client, String command) {
        return setCommand(client, 1, command);
    }

    /** Backward-compatible alias for the original command2 subcommand. */
    public boolean setCommandTwo(MinecraftClient client, String command) {
        return setCommand(client, 2, command);
    }

    /** Runs the consecutive stored commands, starting at slot one. */
    public RunResult runStored(MinecraftClient client) {
        int lastCommand = -1;
        for (int index = storedCommands.size() - 1; index >= 0; index--) {
            if (storedCommands.get(index) != null) {
                lastCommand = index;
                break;
            }
        }
        if (lastCommand + 1 < MIN_COMMANDS) {
            return RunResult.INVALID_COMMAND_COUNT;
        }
        List<String> commands = storedCommands.subList(0, lastCommand + 1);
        return commands.contains(null) ? RunResult.INVALID_COMMAND : run(client, commands);
    }

    /** Validates every command before dispatching the full list in order. */
    public RunResult run(MinecraftClient client, List<String> commands) {
        if (commands == null || commands.size() < MIN_COMMANDS || commands.size() > MAX_COMMANDS) {
            return RunResult.INVALID_COMMAND_COUNT;
        }
        Optional<List<String>> validated = validateCommands(commands);
        if (validated.isEmpty()) {
            return RunResult.INVALID_COMMAND;
        }
        if (!hasRequiredRank(client)) {
            return RunResult.RANK_BLOCKED;
        }

        boolean anySimulated = false;
        for (String command : validated.get()) {
            CommandDispatch dispatch = ClientUtils.dispatchCommand(client, command);
            if (dispatch == CommandDispatch.BLOCKED) {
                sendGuardBlockedMessage(client);
                return RunResult.SERVER_BLOCKED;
            }
            anySimulated |= dispatch == CommandDispatch.SIMULATED;
        }
        return anySimulated ? RunResult.SIMULATED : RunResult.SENT;
    }

    /** Backward-compatible two-command entry point. */
    public RunResult run(MinecraftClient client, String commandOne, String commandTwo) {
        return run(client, Arrays.asList(commandOne, commandTwo));
    }

    private void sendGuardBlockedMessage(MinecraftClient client) {
        ServerGuard.GuardResult guard = ServerGuard.check(client);
        ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.server_guard.blocked",
                guard.reason(), guard.address());
    }

    /** Trims and validates every command while preserving list order. */
    public static Optional<List<String>> validateCommands(List<String> commands) {
        if (commands == null || commands.size() < MIN_COMMANDS || commands.size() > MAX_COMMANDS) {
            return Optional.empty();
        }
        List<String> validated = new ArrayList<>(commands.size());
        for (String command : commands) {
            Optional<String> value = validateCommand(command);
            if (value.isEmpty()) return Optional.empty();
            validated.add(value.get());
        }
        return Optional.of(List.copyOf(validated));
    }

    /** Trims and validates a 1-256 character command safe for dispatch. */
    public static Optional<String> validateCommand(String command) {
        if (command == null) {
            return Optional.empty();
        }
        String trimmed = command.strip();
        while (trimmed.startsWith("/")) {
            trimmed = trimmed.substring(1).stripLeading();
        }
        if (trimmed.isBlank() || trimmed.length() > MAX_COMMAND_LENGTH) {
            return Optional.empty();
        }
        boolean unsafe = trimmed.codePoints().anyMatch(Character::isISOControl);
        return unsafe ? Optional.empty() : Optional.of(trimmed);
    }
}
