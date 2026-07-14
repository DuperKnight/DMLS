package com.duperknight.client.modules;

import com.duperknight.DMLS;
import com.duperknight.client.gui.modules.UuidLookupScreen;
import com.duperknight.client.utils.ChatUtils;
import com.duperknight.client.utils.InputValidators;
import com.duperknight.client.utils.MojangProfileLookup;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Local Mojang API lookup; it does not send a server command. */
public final class UuidLookupModule extends DMLSModule {
    private static final String PREFIX = "§8[§6DMLS - UUID§8] §7";
    private final LookupService lookupService;
    private final AtomicBoolean lookupActive = new AtomicBoolean();

    public UuidLookupModule() {
        this(MojangProfileLookup::lookup);
    }

    public UuidLookupModule(LookupService lookupService) {
        super(StaffRank.HELPER);
        this.lookupService = Objects.requireNonNull(lookupService, "lookupService");
    }

    @Override
    public Text displayName() {
        return Text.translatable("dmls.module.uuid.name");
    }

    @Override
    public ItemStack icon() {
        return new ItemStack(Items.PLAYER_HEAD);
    }

    @Override
    public List<Text> description() {
        return List.of(
                Text.translatable("dmls.module.uuid.description.1"),
                Text.translatable("dmls.module.uuid.description.2"));
    }

    @Override
    public void openScreen(MinecraftClient client, Screen parent) {
        client.setScreen(new UuidLookupScreen(parent, this));
    }

    @Override
    public void register() {
        // Canonical command is registered under /dmls by DMLSClient.
    }

    /** Starts a lookup whose result is printed as one compact chat report. */
    public boolean submit(MinecraftClient client, String input) {
        ParsedInput parsed = parseInput(input);
        if (!reportInputError(client, parsed)) return false;
        if (lookupActive.get()) {
            ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.uuid.active");
            return false;
        }
        if (!beginLookup(client, parsed.usernames(), result -> sendChatReport(client, result))) return false;
        ChatUtils.sendTranslatedMessage(client, PREFIX,
                parsed.usernames().size() == 1 ? "dmls.chat.uuid.looking_up.one" : "dmls.chat.uuid.looking_up.many",
                parsed.usernames().size());
        return true;
    }

    /** Starts a lookup whose result stays in the calling screen instead of chat. */
    public StartResult submitToScreen(MinecraftClient client, String input, Consumer<MojangProfileLookup.BatchResult> consumer) {
        ParsedInput parsed = parseInput(input);
        if (parsed.status() != InputStatus.VALID) return new StartResult(parsed.status(), parsed.invalidNames());
        if (!beginLookup(client, parsed.usernames(), consumer)) return new StartResult(InputStatus.ACTIVE, List.of());
        return new StartResult(InputStatus.VALID, List.of());
    }

    public boolean isLookupActive() {
        return lookupActive.get();
    }

    public static ParsedInput parseInput(String input) {
        List<String> invalid = new ArrayList<>();
        List<String> usernames = InputValidators.uniqueUsernames(input, invalid);
        if (!invalid.isEmpty()) return new ParsedInput(InputStatus.INVALID, List.of(), List.copyOf(invalid));
        if (usernames.isEmpty()) return new ParsedInput(InputStatus.EMPTY, List.of(), List.of());
        if (usernames.size() > MojangProfileLookup.MAX_USERNAMES) {
            return new ParsedInput(InputStatus.TOO_MANY, List.copyOf(usernames), List.of());
        }
        return new ParsedInput(InputStatus.VALID, List.copyOf(usernames), List.of());
    }

    private boolean beginLookup(MinecraftClient client, List<String> usernames,
                                Consumer<MojangProfileLookup.BatchResult> consumer) {
        Objects.requireNonNull(consumer, "consumer");
        if (!lookupActive.compareAndSet(false, true)) return false;

        final CompletableFuture<MojangProfileLookup.BatchResult> future;
        try {
            future = Objects.requireNonNull(lookupService.lookup(List.copyOf(usernames)),
                    "lookup service returned null");
        } catch (RuntimeException exception) {
            lookupActive.set(false);
            deliver(client, consumer, MojangProfileLookup.BatchResult.networkError());
            return true;
        }

        try {
            future.whenComplete((result, error) -> {
                MojangProfileLookup.BatchResult completed = error == null && result != null
                        ? result
                        : MojangProfileLookup.failureResult(error == null
                        ? new IllegalStateException("lookup completed without a result") : error);
                lookupActive.set(false);
                deliver(client, consumer, completed);
            });
        } catch (RuntimeException exception) {
            lookupActive.set(false);
            deliver(client, consumer, MojangProfileLookup.BatchResult.networkError());
        }
        return true;
    }

    private static void deliver(MinecraftClient client, Consumer<MojangProfileLookup.BatchResult> consumer,
                                MojangProfileLookup.BatchResult result) {
        Runnable callback = () -> {
            try {
                consumer.accept(result);
            } catch (RuntimeException exception) {
                DMLS.LOGGER.warn("UUID lookup result consumer failed", exception);
            }
        };
        if (client == null) {
            callback.run();
            return;
        }
        try {
            client.execute(callback);
        } catch (RuntimeException exception) {
            DMLS.LOGGER.warn("Could not schedule UUID lookup result on the client thread", exception);
        }
    }

    private boolean reportInputError(MinecraftClient client, ParsedInput parsed) {
        switch (parsed.status()) {
            case VALID -> { return true; }
            case EMPTY -> ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.common.invalid_igns");
            case INVALID -> ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.uuid.invalid_names",
                    String.join(", ", parsed.invalidNames()));
            case TOO_MANY -> ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.uuid.too_many",
                    MojangProfileLookup.MAX_USERNAMES);
            case ACTIVE -> ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.uuid.active");
        }
        return false;
    }

    private void sendChatReport(MinecraftClient client, MojangProfileLookup.BatchResult result) {
        if (result.status() == MojangProfileLookup.Status.RATE_LIMITED) {
            ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.uuid.rate_limited");
            return;
        }
        if (result.status() != MojangProfileLookup.Status.SUCCESS) {
            ChatUtils.sendTranslatedMessage(client, PREFIX, errorTranslationKey(result.status()));
            return;
        }

        String header = PREFIX + Text.translatable("dmls.chat.uuid.header").getString() + " ";
        ChatUtils.sendClientMessage(client, header + ChatUtils.separatorForChatWidth(client, header));
        for (MojangProfileLookup.Entry entry : result.entries()) {
            MutableText line = Text.literal("§8• §6" + entry.username() + "§7: ");
            if (entry.found()) line.append(clickableUuid(entry.uuid()));
            else line.append(Text.translatable("dmls.chat.uuid.not_found_short").formatted(net.minecraft.util.Formatting.RED));
            ChatUtils.sendClientMessage(client, line);
        }
        ChatUtils.sendClientMessage(client, "§7" + ChatUtils.separatorForChatWidth(client, ""));
    }

    private static Text clickableUuid(String uuid) {
        return Text.literal(uuid).styled(style -> style
                .withColor(0xFFAA00)
                .withClickEvent(new ClickEvent.CopyToClipboard(uuid))
                .withHoverEvent(new HoverEvent.ShowText(Text.translatable("dmls.chat.uuid.copy"))));
    }

    public static String errorTranslationKey(MojangProfileLookup.Status status) {
        return switch (status) {
            case RATE_LIMITED -> "dmls.chat.uuid.rate_limited";
            case MALFORMED_RESPONSE -> "dmls.chat.uuid.malformed_response";
            case TIMEOUT -> "dmls.chat.uuid.timeout";
            case NETWORK_ERROR -> "dmls.chat.uuid.network_error";
            case INVALID_REQUEST -> "dmls.chat.uuid.error";
            case SUCCESS -> throw new IllegalArgumentException("SUCCESS is not an error");
        };
    }

    public enum InputStatus { VALID, EMPTY, INVALID, TOO_MANY, ACTIVE }
    public record ParsedInput(InputStatus status, List<String> usernames, List<String> invalidNames) { }
    public record StartResult(InputStatus status, List<String> invalidNames) { }

    @FunctionalInterface
    public interface LookupService {
        CompletableFuture<MojangProfileLookup.BatchResult> lookup(List<String> usernames);
    }
}
