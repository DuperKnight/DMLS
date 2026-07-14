package com.duperknight.client.modules;

import com.duperknight.client.gui.modules.EventProtectScreen;
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

public final class EventProtectModule extends DMLSModule{
    private static final String PREFIX = "§8[§6DMLS - Event Protect§8] §7";
    public static final int MAX_EVENT_NAME_CODE_POINTS = 64;
    public static final int MAX_LAND_NAME_CODE_POINTS = 64;

    public enum BroadcastResult {
        SENT,
        SIMULATED,
        INVALID_EVENT,
        INVALID_LAND,
        RANK_BLOCKED,
        SERVER_BLOCKED
    }

    public EventProtectModule() {
        super(StaffDepartment.EVENTS);
    }

    @Override
    public Text displayName() {
        return Text.translatable("dmls.module.event_protect.name");
    }

    @Override
    public ItemStack icon() {
        return new ItemStack(Items.SHIELD);
    }

    @Override
    public List<Text> description() {
        return List.of(Text.translatable("dmls.module.event_protect.description"));
    }

    @Override
    public void openScreen(MinecraftClient client, Screen parent) {
        client.setScreen(new EventProtectScreen(parent, this));
    }

    @Override
    public void register() {
        // No event listeners needed
    }

    /**
     * Issues /broadcastraw with a protection notice after validating the
     * immutable value supplied by this submission.
     */
    public BroadcastResult broadcastProtection(MinecraftClient client, String eventName, String landName) {
        Optional<String> validatedEventName = validateEventName(eventName);
        if (validatedEventName.isEmpty()) {
            return BroadcastResult.INVALID_EVENT;
        }
        Optional<String> validatedLandName = validateLandName(landName);
        if (validatedLandName.isEmpty()) {
            return BroadcastResult.INVALID_LAND;
        }

        if (!hasRequiredRank(client)) {
            return BroadcastResult.RANK_BLOCKED;
        }

        CommandDispatch dispatch = ClientUtils.dispatchCommand(client,
                buildBroadcastCommand(validatedEventName.get(), validatedLandName.get()));
        if (dispatch == CommandDispatch.BLOCKED) {
            ServerGuard.GuardResult guard = ServerGuard.check(client);
            ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.server_guard.blocked",
                    guard.reason(), guard.address());
            return BroadcastResult.SERVER_BLOCKED;
        }
        return dispatch == CommandDispatch.SIMULATED ? BroadcastResult.SIMULATED : BroadcastResult.SENT;
    }

    /** Trims and validates a 1-64 code-point event name safe for command use. */
    public static Optional<String> validateEventName(String eventName) {
        return validateBroadcastField(eventName, MAX_EVENT_NAME_CODE_POINTS);
    }

    /** Trims and validates a 1-64 code-point land claim name safe for command use. */
    public static Optional<String> validateLandName(String landName) {
        return validateBroadcastField(landName, MAX_LAND_NAME_CODE_POINTS);
    }

    private static Optional<String> validateBroadcastField(String input, int maximumCodePoints) {
        if (input == null) {
            return Optional.empty();
        }

        String trimmed = input.strip();
        int length = trimmed.codePointCount(0, trimmed.length());
        if (trimmed.isBlank() || length < 1 || length > maximumCodePoints) {
            return Optional.empty();
        }

        boolean unsafe = trimmed.codePoints().anyMatch(EventProtectModule::isUnsafeEventNameCodePoint);
        return unsafe ? Optional.empty() : Optional.of(trimmed);
    }

    private static boolean isUnsafeEventNameCodePoint(int codePoint) {
        if (codePoint == '&' || codePoint == '§' || Character.isISOControl(codePoint)) {
            return true;
        }
        return switch (Character.getType(codePoint)) {
            case Character.FORMAT, Character.LINE_SEPARATOR, Character.PARAGRAPH_SEPARATOR,
                    Character.SURROGATE, Character.UNASSIGNED -> true;
            default -> false;
        };
    }

    static String buildBroadcastCommand(String eventName, String landName) {
        String message = "&7&l" + eventName + " is starting in " + landName
                + " and is protected by staff. Bandits and raiders are &4&lnot&7&l allowed to interfere.";
        return "broadcastraw public " + message;
    }
}
