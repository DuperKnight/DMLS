package com.duperknight.client.modules;

import com.duperknight.client.gui.GreeterScreen;
import com.duperknight.client.message.MessageOrigin;
import com.duperknight.client.message.ServerMessage;
import com.duperknight.client.message.ServerMessageRouter;
import com.duperknight.client.utils.ChatUtils;
import com.duperknight.client.utils.ClientUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Detects first-join broadcasts and offers a click-to-send welcome message. */
public final class GreeterModule extends DMLSModule {
    private static final String PREFIX = "§8[§6DMLS - Greeter§8] §7";
    private static final String GREETING = "Welcome to Stoneworks, %s! Enjoy your stay, and feel free to ask if you have any questions :)";
    private static final long PROMPT_COOLDOWN_MILLIS = 5 * 60 * 1000;
    private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9_]{3,16}");
    private static final List<Pattern> FIRST_JOIN_PATTERNS = List.of(
            Pattern.compile("welcome,? ([A-Za-z0-9_]{3,16}),? to (?:the )?(?:server|stoneworks)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("([A-Za-z0-9_]{3,16}) (?:has )?joined (?:the server |us )?for the first time", Pattern.CASE_INSENSITIVE)
    );

    private final Map<String, Long> recentPrompts = new HashMap<>();
    private boolean enabled = true;

    public GreeterModule() {
        super(StaffRank.HELPER);
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
        ServerMessageRouter.subscribe(EnumSet.of(MessageOrigin.SERVER_SYSTEM), this::handleServerMessage);
    }

    public boolean enabled() {
        return enabled;
    }

    /** Enables or disables the first-join prompts for this session. */
    public void setEnabled(MinecraftClient client, boolean enabled) {
        this.enabled = enabled;
        ChatUtils.sendTranslatedMessage(client, PREFIX,
                enabled ? "dmls.chat.greeter.enabled" : "dmls.chat.greeter.disabled");
    }

    /** Sends the public welcome message for the given player. */
    public void greet(MinecraftClient client, String ign) {
        if (!hasRequiredRank(client)) {
            return;
        }

        if (!USERNAME.matcher(ign).matches()) {
            ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.common.invalid_ign");
            return;
        }

        ClientUtils.sendChatMessage(client, GREETING.formatted(ign));
    }

    private void handleServerMessage(ServerMessage message) {
        if (!enabled) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (ClientUtils.isNotConnected(client)) {
            return;
        }

        Optional<String> parsedName = parseFirstJoin(message.cleanText());
        if (parsedName.isEmpty()) {
            return;
        }

        String name = parsedName.get();
        if (name.equalsIgnoreCase(client.player.getName().getString())) {
            return;
        }

        long now = System.currentTimeMillis();
        Long lastPrompt = recentPrompts.get(name.toLowerCase(Locale.ROOT));
        if (lastPrompt != null && now - lastPrompt < PROMPT_COOLDOWN_MILLIS) {
            return;
        }
        recentPrompts.put(name.toLowerCase(Locale.ROOT), now);

        Text button = Text.translatable("dmls.chat.greeter.button").formatted(Formatting.GREEN)
                .styled(style -> style
                        .withClickEvent(new ClickEvent.RunCommand("/dmls greet " + name))
                        .withHoverEvent(new HoverEvent.ShowText(Text.translatable("dmls.chat.greeter.button.hover", name))));
        ChatUtils.sendClientMessage(client, Text.literal(PREFIX)
                .append(ChatUtils.translated("dmls.chat.greeter.detected", name))
                .append(" ")
                .append(button));
    }

    private static Optional<String> parseFirstJoin(String message) {
        for (Pattern pattern : FIRST_JOIN_PATTERNS) {
            Matcher matcher = pattern.matcher(message);
            if (matcher.find()) {
                return Optional.of(matcher.group(1));
            }
        }
        return Optional.empty();
    }
}
