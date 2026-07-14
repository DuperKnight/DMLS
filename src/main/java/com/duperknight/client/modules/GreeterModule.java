package com.duperknight.client.modules;

import com.duperknight.client.gui.modules.GreeterScreen;
import com.duperknight.client.session.CommandDispatch;
import com.duperknight.client.utils.ChatUtils;
import com.duperknight.client.utils.ClientUtils;
import com.duperknight.client.utils.DMLSConfig;
import com.duperknight.client.utils.InputValidators;
import com.duperknight.client.utils.ServerGuard;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.LongSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Detects first-join broadcasts and offers a click-to-send welcome message. */
public final class GreeterModule extends DMLSModule {
    private static final String PREFIX = "§8[§6DMLS - Greeter§8] §7";
    private static final String GREETING = "Welcome to Stoneworks, %s! Enjoy your stay, and feel free to ask if you have any questions :)";
    private static final long PROMPT_COOLDOWN_MILLIS = 5 * 60 * 1000;
    private static final List<Pattern> FIRST_JOIN_PATTERNS = List.of(
            Pattern.compile("^welcome,?\\s+\\[?([A-Za-z0-9_]{3,16})]?,?\\s+to\\s+(?:abexilas|stoneworks|(?:the\\s+)?server)\\s*[!.]?$",
                    Pattern.CASE_INSENSITIVE),
            Pattern.compile("([A-Za-z0-9_]{3,16}) (?:has )?joined (?:the server |us )?for the first time", Pattern.CASE_INSENSITIVE)
    );

    private final LongSupplier clock;
    private final Map<String, Long> recentPrompts = new HashMap<>();
    private Object promptConnectionIdentity;

    public GreeterModule() {
        this(System::currentTimeMillis);
    }

    GreeterModule(LongSupplier clock) {
        super(StaffRank.HELPER);
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public Text displayName() {
        return Text.translatable("dmls.module.greeter.name");
    }

    @Override
    public ItemStack icon() {
        return new ItemStack(Items.CAKE);
    }

    @Override
    public List<Text> description() {
        return List.of(Text.translatable("dmls.module.greeter.description"));
    }

    @Override
    public void openScreen(MinecraftClient client, Screen parent) {
        client.setScreen(new GreeterScreen(parent, this));
    }

    @Override
    public void register() {
        ClientReceiveMessageEvents.MODIFY_GAME.register(this::appendWelcomeAction);
    }

    public boolean enabled() {
        return DMLSConfig.greeterEnabled();
    }

    /** Enables or disables first-join prompts and saves the preference. */
    public boolean setEnabled(MinecraftClient client, boolean enabled) {
        if (!DMLSConfig.setGreeterEnabled(enabled)) {
            ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.config.save_failed");
            return false;
        }
        if (!enabled) {
            resetPromptCache();
        }
        ChatUtils.sendTranslatedMessage(client, PREFIX,
                enabled ? "dmls.chat.greeter.enabled" : "dmls.chat.greeter.disabled");
        return true;
    }

    /** Sends the public welcome message for the given player. */
    public CommandDispatch greet(MinecraftClient client, String ign) {
        if (!InputValidators.isUsername(ign)) {
            ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.common.invalid_ign");
            return CommandDispatch.BLOCKED;
        }

        if (!hasRequiredRank(client)) {
            return CommandDispatch.BLOCKED;
        }

        CommandDispatch result = ClientUtils.dispatchChatMessage(client, GREETING.formatted(ign));
        if (result == CommandDispatch.BLOCKED) {
            ServerGuard.GuardResult guard = ServerGuard.check(client);
            ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.server_guard.blocked",
                    guard.reason(), guard.address());
        }
        return result;
    }

    private Text appendWelcomeAction(Text message, boolean overlay) {
        if (overlay || !enabled() || !isAvailableToDetectedRank()) {
            return message;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (ClientUtils.isNotConnected(client) || !ServerGuard.check(client).allowed()) {
            resetPromptCache();
            return message;
        }
        Object currentConnection = client.getNetworkHandler();
        if (promptConnectionIdentity != currentConnection) {
            recentPrompts.clear();
            promptConnectionIdentity = currentConnection;
        }

        Optional<String> parsedName = parseFirstJoin(ChatUtils.cleanLine(message.getString()));
        if (parsedName.isEmpty()) {
            return message;
        }

        String name = parsedName.get();
        if (name.equalsIgnoreCase(client.player.getName().getString())) {
            return message;
        }

        if (!InputValidators.isUsername(name) || !recordPrompt(name, clock.getAsLong())) {
            return message;
        }

        Text button = Text.translatable("dmls.chat.greeter.button").formatted(Formatting.GREEN)
                .styled(style -> style
                        .withClickEvent(new ClickEvent.RunCommand("/dmls greet " + name))
                        .withHoverEvent(new HoverEvent.ShowText(Text.translatable("dmls.chat.greeter.button.hover", name))));
        return message.copy().append(" ").append(button);
    }

    static Optional<String> parseFirstJoin(String message) {
        for (Pattern pattern : FIRST_JOIN_PATTERNS) {
            Matcher matcher = pattern.matcher(message);
            if (matcher.find()) {
                return Optional.of(matcher.group(1));
            }
        }
        return Optional.empty();
    }

    /** Prunes expired entries and records a prompt only when its cooldown elapsed. */
    boolean recordPrompt(String name, long now) {
        recentPrompts.entrySet().removeIf(entry -> now - entry.getValue() >= PROMPT_COOLDOWN_MILLIS);
        String key = name.toLowerCase(Locale.ROOT);
        if (recentPrompts.containsKey(key)) {
            return false;
        }
        recentPrompts.put(key, now);
        return true;
    }

    private void resetPromptCache() {
        recentPrompts.clear();
        promptConnectionIdentity = null;
    }
}
