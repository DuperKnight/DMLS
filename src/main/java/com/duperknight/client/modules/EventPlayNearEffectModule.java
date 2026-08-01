package com.duperknight.client.modules;

import com.duperknight.client.gui.modules.EventPlayNearEffectScreen;
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

public final class EventPlayNearEffectModule extends DMLSModule {
    private static final String PREFIX = "§8[§6DMLS - PlayNearEffect§8] §7";
    public static final int MAX_EFFECT_NAME_LENGTH = 64;
    public static final int MAX_RADIUS = 1000;
    public static final int MAX_DURATION_SECONDS = 1000000;
    public static final int MAX_AMPLIFIER = 255;

    public enum RunResult {
        SENT,
        SIMULATED,
        INVALID_EFFECT,
        INVALID_RADIUS,
        INVALID_DURATION,
        INVALID_AMPLIFIER,
        RANK_BLOCKED,
        SERVER_BLOCKED
    }

    public EventPlayNearEffectModule() {
        super(StaffDepartment.EVENTS);
    }

    @Override
    public Text displayName() {
        return Text.translatable("dmls.module.event_play_near_effect.name");
    }

    @Override
    public ItemStack icon() {
        return new ItemStack(Items.POTION);
    }

    @Override
    public List<Text> description() {
        return List.of(Text.translatable("dmls.module.event_play_near_effect.description"));
    }

    @Override
    public ModuleCategory category() {
        return ModuleCategory.EVENTS;
    }

    @Override
    public void openScreen(MinecraftClient client, Screen parent) {
        client.setScreen(new EventPlayNearEffectScreen(parent, this));
    }

    @Override
    public void register() {
        // No event listeners needed
    }

    public RunResult run(MinecraftClient client, String effectName, String durationInput,
                         String amplifierInput, String radiusInput) {
        Optional<String> validatedEffect = validateEffectName(effectName);
        if (validatedEffect.isEmpty()) {
            return RunResult.INVALID_EFFECT;
        }
        Optional<Integer> validatedDuration = validateDuration(durationInput);
        if (validatedDuration.isEmpty()) {
            return RunResult.INVALID_DURATION;
        }
        Optional<Integer> validatedAmplifier = validateAmplifier(amplifierInput);
        if (validatedAmplifier.isEmpty()) {
            return RunResult.INVALID_AMPLIFIER;
        }
        Optional<Integer> validatedRadius = validateRadius(radiusInput);
        if (validatedRadius.isEmpty()) {
            return RunResult.INVALID_RADIUS;
        }

        if (!hasRequiredRank(client)) {
            return RunResult.RANK_BLOCKED;
        }

        String command = buildCommand(validatedEffect.get(), validatedDuration.get(),
                validatedAmplifier.get(), validatedRadius.get());
        CommandDispatch dispatch = ClientUtils.dispatchCommand(client, command);
        if (dispatch == CommandDispatch.BLOCKED) {
            ServerGuard.GuardResult guard = ServerGuard.check(client);
            ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.server_guard.blocked",
                    guard.reason(), guard.address());
            return RunResult.SERVER_BLOCKED;
        }
        return dispatch == CommandDispatch.SIMULATED ? RunResult.SIMULATED : RunResult.SENT;
    }

    static String buildCommand(String effectName, int durationSeconds, int amplifier, int radius) {
        return "execute as @a[distance=..%d] run effect give @s %s %d %d"
                .formatted(radius, effectName, durationSeconds, amplifier);
    }

    public static Optional<String> validateEffectName(String effectName) {
        if (effectName == null) {
            return Optional.empty();
        }
        String trimmed = effectName.strip();
        if (trimmed.isBlank() || trimmed.length() > MAX_EFFECT_NAME_LENGTH) {
            return Optional.empty();
        }
        boolean unsafe = trimmed.codePoints().anyMatch(codePoint ->
                Character.isISOControl(codePoint) || Character.isWhitespace(codePoint));
        return unsafe ? Optional.empty() : Optional.of(trimmed);
    }

    public static Optional<Integer> validateDuration(String durationInput) {
        return parseBoundedInt(durationInput, 1, MAX_DURATION_SECONDS);
    }

    public static Optional<Integer> validateAmplifier(String amplifierInput) {
        return parseBoundedInt(amplifierInput, 0, MAX_AMPLIFIER);
    }

    public static Optional<Integer> validateRadius(String radiusInput) {
        return parseBoundedInt(radiusInput, 1, MAX_RADIUS);
    }

    private static Optional<Integer> parseBoundedInt(String input, int minimum, int maximum) {
        if (input == null) {
            return Optional.empty();
        }
        try {
            int value = Integer.parseInt(input.strip());
            return value >= minimum && value <= maximum ? Optional.of(value) : Optional.empty();
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
    }
}