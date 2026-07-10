package com.duperknight.client.modules;

import com.duperknight.client.gui.PrefixCreateScreen;
import com.duperknight.client.utils.ChatUtils;
import com.duperknight.client.utils.ClientUtils;
import com.duperknight.client.utils.PrefixTextFormatter;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.command.CommandSource;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

public final class PrefixCreateModule extends DMLSModule {
    public static final int MAX_COMMAND_LENGTH = 256;
    public static final String CUSTOM_LIMIT = "Custom";
    public static final List<String> LIMITS = List.of("10", "30", Integer.toString(Integer.MAX_VALUE), CUSTOM_LIMIT);
    private static final List<String> COMMAND_LIMIT_SUGGESTIONS = LIMITS.stream().filter(limit -> !CUSTOM_LIMIT.equals(limit)).toList();

    private static final String PREFIX = "§8[§6DMLS - Prefix§8] §7";
    private static final int COMMAND_DELAY_TICKS = 20;
    private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9_]{3,16}");

    private CreateSession activeSession;

    public PrefixCreateModule() {
        super(StaffRank.SUPPORT);
    }

    @Override
    public Text displayName() {
        return Text.literal("Prefix Creation");
    }

    @Override
    public ItemStack icon() {
        return new ItemStack(Items.OAK_SIGN);
    }

    @Override
    public List<Text> description() {
        return List.of(
                Text.literal("Create a formatted prefix, set its player limit"),
                Text.literal("and manager in one go.")
        );
    }

    @Override
    public void openScreen(MinecraftClient client, Screen parent) {
        client.setScreen(new PrefixCreateScreen(parent, this));
    }

    @Override
    public void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
                ClientCommandManager.literal("prefixlazy")
                        .then(ClientCommandManager.argument("ign", StringArgumentType.word())
                                .then(ClientCommandManager.argument("limit", StringArgumentType.word())
                                        .suggests((context, builder) -> CommandSource.suggestMatching(COMMAND_LIMIT_SUGGESTIONS, builder))
                                        .then(ClientCommandManager.argument("prefixid", StringArgumentType.word())
                                                .then(ClientCommandManager.argument("prefixtext", StringArgumentType.greedyString())
                                                        .suggests((context, builder) -> CommandSource.suggestMatching(List.of("&a", "&#FFFFFF", "<green>"), builder))
                                                        .executes(context -> {
                                                            submit(context.getSource().getClient(),
                                                                    StringArgumentType.getString(context, "ign").trim(),
                                                                    StringArgumentType.getString(context, "limit").trim(),
                                                                    StringArgumentType.getString(context, "prefixid").trim(),
                                                                    StringArgumentType.getString(context, "prefixtext").trim());
                                                            return 1;
                                                        })))))
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (activeSession != null) {
                activeSession.tick(client);
            }
        });
    }

    /** Starts the prefix creation. The command and GUI both call this method. */
    public ValidationResult submit(MinecraftClient client, String ign, String limit, String prefixId, String prefixText) {
        if (!hasRequiredRank(client)) {
            return ValidationResult.error("You do not have the required staff rank.");
        }

        ValidationResult validation = validate(ign, limit, prefixId, prefixText);
        if (!validation.valid()) {
            ChatUtils.sendClientMessage(client, PREFIX + validation.message());
            return validation;
        }

        if (activeSession != null) {
            ValidationResult activeSessionError = ValidationResult.error("A prefix creation is still running, wait for it to finish.");
            ChatUtils.sendClientMessage(client, PREFIX + activeSessionError.message());
            return activeSessionError;
        }

        activeSession = new CreateSession(ign, validation.limit(), prefixId, prefixText);
        activeSession.start(client);
        return ValidationResult.success(validation.limit());
    }

    public static ValidationResult validate(String ign, String limit, String prefixId, String prefixText) {
        if (!USERNAME.matcher(ign).matches()) {
            return ValidationResult.error("Enter a valid player IGN.");
        }

        Optional<String> resolvedLimit = resolveLimit(limit);
        if (resolvedLimit.isEmpty()) {
            return ValidationResult.error("The player limit must be a whole number from 1 to 2147483647.");
        }

        if (prefixId.isEmpty()) {
            return ValidationResult.error("Enter a prefix ID.");
        }

        if (prefixText.isEmpty()) {
            return ValidationResult.error("Enter prefix text.");
        }

        PrefixTextFormatter.ParseResult formattedPrefix = PrefixTextFormatter.parse(prefixText);
        if (!formattedPrefix.valid()) {
            return ValidationResult.error(formattedPrefix.error());
        }

        int commandLength = createCommand(prefixId, prefixText).length();
        if (commandLength > MAX_COMMAND_LENGTH) {
            return ValidationResult.error("The create command is " + commandLength + "/" + MAX_COMMAND_LENGTH
                    + " characters. Ask an admin to create it via console, shorten the prefix ID, shorten the prefix text or colors, or try a more compact format.");
        }

        return ValidationResult.success(resolvedLimit.get());
    }

    public static Optional<String> resolveLimit(String limit) {
        try {
            int parsed = Integer.parseInt(limit);
            return parsed > 0 ? Optional.of(Integer.toString(parsed)) : Optional.empty();
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
    }

    public static String createCommand(String prefixId, String prefixText) {
        return "prefix create %s %s".formatted(prefixId, prefixText);
    }

    public record ValidationResult(String limit, String message) {
        public static ValidationResult success(String limit) {
            return new ValidationResult(limit, "");
        }

        public static ValidationResult error(String message) {
            return new ValidationResult("", message);
        }

        public boolean valid() {
            return message.isEmpty();
        }
    }

    private final class CreateSession {
        private final String ign;
        private final String limit;
        private final String prefixId;
        private final String prefixText;
        private final List<String> commands;

        private int commandIndex;
        private int waitTicks;

        private CreateSession(String ign, String limit, String prefixId, String prefixText) {
            this.ign = ign;
            this.limit = limit;
            this.prefixId = prefixId;
            this.prefixText = prefixText;
            this.commands = List.of(
                    createCommand(prefixId, prefixText),
                    "prefix x setlimit %s %s".formatted(prefixId, limit),
                    "prefix x setmanager %s %s".formatted(prefixId, ign),
                    "prefix x info %s".formatted(prefixId)
            );
        }

        private void start(MinecraftClient client) {
            ChatUtils.sendClientMessage(client, PREFIX + "Creating prefix §6" + prefixId + "§7 for §6" + ign + "§7...");
            ClientUtils.sendCommand(client, commands.get(0));
        }

        private void tick(MinecraftClient client) {
            if (ClientUtils.isNotConnected(client)) {
                activeSession = null;
                return;
            }

            waitTicks++;
            if (waitTicks < COMMAND_DELAY_TICKS) {
                return;
            }

            waitTicks = 0;
            commandIndex++;
            if (commandIndex >= commands.size()) {
                report(client);
                activeSession = null;
                return;
            }

            ClientUtils.sendCommand(client, commands.get(commandIndex));
        }

        private void report(MinecraftClient client) {
            PrefixTextFormatter.ParseResult formattedPrefix = PrefixTextFormatter.parse(prefixText);
            Text displayedPrefix = formattedPrefix.valid() ? formattedPrefix.preview() : Text.literal(prefixText);
            ChatUtils.sendClientMessage(client, Text.literal(PREFIX + "Created prefix \u00A76" + prefixId + "\u00A77 with text ")
                    .append(displayedPrefix)
                    .append(Text.literal("\u00A77, player limit \u00A76" + limit + "\u00A77 and manager \u00A76" + ign
                            + "\u00A77. Check the info above to confirm.")));
        }
    }
}
