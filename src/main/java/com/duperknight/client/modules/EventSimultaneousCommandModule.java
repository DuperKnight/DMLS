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

import java.util.List;
import java.util.Optional;

public final class EventSimultaneousCommandModule extends DMLSModule {
    private static final String PREFIX = "§8[§6DMLS - Simultaneous§8] §7";
    public static final int MAX_COMMAND_LENGTH = 256;

    private String storedCommandOne;
    private String storedCommandTwo;

    public enum RunResult {
        SENT,
        SIMULATED,
        INVALID_COMMAND_ONE,
        INVALID_COMMAND_TWO,
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

    public String storedCommandOne() {
        return storedCommandOne;
    }

    public String storedCommandTwo() {
        return storedCommandTwo;
    }

    /** Stores the first command for later use with runStored(). */
    public boolean setCommandOne(MinecraftClient client, String command) {
        Optional<String> validated = validateCommand(command);
        if (validated.isEmpty()) {
            ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.validation.event_simultaneous.command_one");
            return false;
        }
        storedCommandOne = validated.get();
        ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.event_simultaneous.set_one", storedCommandOne);
        return true;
    }

    /** Stores the second command for later use with runStored(). */
    public boolean setCommandTwo(MinecraftClient client, String command) {
        Optional<String> validated = validateCommand(command);
        if (validated.isEmpty()) {
            ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.validation.event_simultaneous.command_two");
            return false;
        }
        storedCommandTwo = validated.get();
        ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.event_simultaneous.set_two", storedCommandTwo);
        return true;
    }

    /** Runs the two previously stored commands in sequence. */
    public RunResult runStored(MinecraftClient client) {
        if (storedCommandOne == null) {
            return RunResult.INVALID_COMMAND_ONE;
        }
        if (storedCommandTwo == null) {
            return RunResult.INVALID_COMMAND_TWO;
        }
        return run(client, storedCommandOne, storedCommandTwo);
    }

    /** Validates both commands, then dispatches them one after another. */
    public RunResult run(MinecraftClient client, String commandOne, String commandTwo) {
        Optional<String> validatedOne = validateCommand(commandOne);
        if (validatedOne.isEmpty()) {
            return RunResult.INVALID_COMMAND_ONE;
        }
        Optional<String> validatedTwo = validateCommand(commandTwo);
        if (validatedTwo.isEmpty()) {
            return RunResult.INVALID_COMMAND_TWO;
        }

        if (!hasRequiredRank(client)) {
            return RunResult.RANK_BLOCKED;
        }

        CommandDispatch firstDispatch = ClientUtils.dispatchCommand(client, validatedOne.get());
        if (firstDispatch == CommandDispatch.BLOCKED) {
            sendGuardBlockedMessage(client);
            return RunResult.SERVER_BLOCKED;
        }

        CommandDispatch secondDispatch = ClientUtils.dispatchCommand(client, validatedTwo.get());
        if (secondDispatch == CommandDispatch.BLOCKED) {
            sendGuardBlockedMessage(client);
            return RunResult.SERVER_BLOCKED;
        }

        boolean anySimulated = firstDispatch == CommandDispatch.SIMULATED
                || secondDispatch == CommandDispatch.SIMULATED;
        return anySimulated ? RunResult.SIMULATED : RunResult.SENT;
    }

    private void sendGuardBlockedMessage(MinecraftClient client) {
        ServerGuard.GuardResult guard = ServerGuard.check(client);
        ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.server_guard.blocked",
                guard.reason(), guard.address());
    }

    /** Trims and validates a 1-256 character command safe for dispatch. */
    public static Optional<String> validateCommand(String command) {
        if (command == null) {
            return Optional.empty();
        }
        String trimmed = command.strip();
        if (trimmed.isBlank() || trimmed.length() > MAX_COMMAND_LENGTH) {
            return Optional.empty();
        }
        boolean unsafe = trimmed.codePoints().anyMatch(Character::isISOControl);
        return unsafe ? Optional.empty() : Optional.of(trimmed);
    }
}