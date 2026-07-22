package com.duperknight.client.modules;

import com.duperknight.client.gui.modules.DoNotInstaBanScreen;
import com.duperknight.client.instaban.DoNotInstaBanService;
import com.duperknight.client.instaban.InstaBanLookupOutcome;
import com.duperknight.client.instaban.InstaBanChatHighlight;
import com.duperknight.client.moderation.ModerationChatService;
import com.duperknight.client.instaban.InstaBanPresentation;
import com.duperknight.client.instaban.InstaBanResult;
import com.duperknight.client.utils.ChatUtils;
import com.duperknight.client.utils.DMLSConfig;
import com.duperknight.client.utils.InputValidators;
import com.duperknight.client.utils.ServerGuard;
import com.duperknight.mixin.ChatHudAccessor;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Checks green online names from a staff-scan chat block against the DMLS index. */
public final class DoNotInstaBanModule extends DMLSModule {
    private static final String PREFIX = "§8[§6DMLS - Do Not Insta Ban§8] §7";
    private static final Pattern SCANNING_LINE = Pattern.compile("^Scanning [A-Za-z0-9_]{3,16}\\.?$");
    private static final Pattern STATUS_LINE = Pattern.compile(
            "^\\[Online] \\[Offline] \\[Banned] \\[IPBanned]$", Pattern.CASE_INSENSITIVE);
    private static final Pattern GREEN_TOKEN = Pattern.compile("[A-Za-z0-9_]+");
    private static final String[] SPINNER = {"⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏"};
    private static final int MAX_SEEN_BLOCKS = 256;

    private final DoNotInstaBanService service;
    private final List<PendingLookup> pending = new ArrayList<>();
    private final Set<String> seenBlocks = new LinkedHashSet<>();
    private Object connectionIdentity;
    private long ticks;

    public DoNotInstaBanModule() {
        this(DoNotInstaBanService.shared());
    }

    DoNotInstaBanModule(DoNotInstaBanService service) {
        super(StaffRank.HELPER);
        this.service = service;
    }

    @Override
    public Text displayName() {
        return Text.translatable("dmls.module.do_not_insta_ban.name");
    }

    @Override
    public ItemStack icon() {
        return new ItemStack(Items.SHIELD);
    }

    @Override
    public List<Text> description() {
        return List.of(
                Text.translatable("dmls.module.do_not_insta_ban.description.1"),
                Text.translatable("dmls.module.do_not_insta_ban.description.2"));
    }

    @Override
    public ModuleCategory category() {
        return ModuleCategory.GENERAL;
    }

    @Override
    public boolean requiresDiscordLink() {
        return true;
    }

    @Override
    public void openScreen(MinecraftClient client, Screen parent) {
        client.setScreen(new DoNotInstaBanScreen(parent, this));
    }

    @Override
    public void register() {
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }

    public boolean enabled() {
        return DMLSConfig.doNotInstaBanEnabled();
    }

    public boolean setEnabled(MinecraftClient client, boolean enabled) {
        if (enabled && !hasRequiredIntegrations(client)) return false;
        if (!DMLSConfig.setDoNotInstaBanEnabled(enabled)) {
            ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.config.save_failed");
            return false;
        }
        if (!enabled) restorePending(client);
        return true;
    }

    private void tick(MinecraftClient client) {
        ticks++;
        Object currentConnection = client.getNetworkHandler();
        if (connectionIdentity != currentConnection) {
            restorePending(client);
            pending.clear();
            seenBlocks.clear();
            connectionIdentity = currentConnection;
        }
        if (!enabled() || !isAvailableToDetectedRank() || !isEnabledForClient(client) || client.inGameHud == null
                || currentConnection == null || !ServerGuard.check(client).allowed()) {
            if (!pending.isEmpty()) restorePending(client);
            return;
        }

        if (ticks % 2 == 0) animateSpinners(client);
        detectNewestBlock(client);
    }

    private void detectNewestBlock(MinecraftClient client) {
        ChatHud chat = client.inGameHud.getChatHud();
        List<ChatHudLine> messages = ((ChatHudAccessor) chat).dmls$getMessages();
        int limit = Math.min(messages.size(), 80);
        for (int index = 0; index < limit; index++) {
            ChatHudLine names;
            ChatHudLine statuses;
            ChatHudLine scanning;
            List<String> greenNames;
            List<String> bannedNames;
            ChatHudLine current = messages.get(index);
            if (isCompleteScanBlock(current.content().getString())) {
                names = current;
                statuses = current;
                scanning = current;
                greenNames = extractGreenTokensFromLogicalLine(current.content(), 2);
                bannedNames = extractRedTokensFromLogicalLine(current.content(), 2);
            } else {
                if (index + 1 >= messages.size()) continue;
                names = current;
                ChatHudLine next = messages.get(index + 1);
                if (isCombinedScanHeader(next.content().getString())) {
                    scanning = next;
                    statuses = next;
                } else {
                    if (index + 2 >= messages.size()) continue;
                    statuses = next;
                    scanning = messages.get(index + 2);
                    if (!isStatusLine(statuses.content().getString())
                            || !isScanningLine(scanning.content().getString())) continue;
                }
                greenNames = extractGreenTokens(names.content());
                bannedNames = extractRedTokens(names.content());
            }

            String key = scanning.creationTick() + "|" + statuses.creationTick() + "|" + names.creationTick()
                    + "|" + stableBlockIdentity(scanning.content(), greenNames);
            if (!seenBlocks.add(key)) continue;
            trimSeenBlocks();

            if (!shouldStartLookup(greenNames, bannedNames)) return;
            Optional<String> invalid = greenNames.stream().filter(name -> !InputValidators.isUsername(name)).findFirst();
            if (invalid.isPresent()) {
                ChatUtils.sendTranslatedMessage(client, PREFIX,
                        "dmls.chat.do_not_insta_ban.invalid_ign", invalid.get());
                return;
            }
            startLookup(client, scanning, statuses, names, greenNames);
            return;
        }
    }

    private void startLookup(MinecraftClient client, ChatHudLine scanning, ChatHudLine statuses,
                             ChatHudLine names, List<String> players) {
        UUID minecraftUuid = client.getSession().getUuidOrNull();
        if (minecraftUuid == null) {
            ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.do_not_insta_ban.identity");
            return;
        }
        LineHandle scanningHandle = new LineHandle(scanning);
        LineHandle statusesHandle = statuses == scanning ? null : new LineHandle(statuses);
        LineHandle namesHandle = names == scanning ? scanningHandle : new LineHandle(names);
        PendingLookup lookup = new PendingLookup(connectionIdentity,
                scanningHandle, statusesHandle, namesHandle, List.copyOf(players));
        pending.add(lookup);
        updateSpinner(client, lookup);
        service.check(minecraftUuid, players).whenComplete((outcome, error) -> client.execute(() -> {
            if (!pending.remove(lookup)) return;
            if (lookup.connectionIdentity != connectionIdentity || !enabled()) {
                restoreStatus(client, lookup);
                return;
            }
            if (error != null || outcome == null) {
                restoreStatus(client, lookup);
                showFailure(client, InstaBanLookupOutcome.Type.TEMPORARY_ERROR);
                return;
            }
            applyOutcome(client, lookup, outcome);
        }));
    }

    private void applyOutcome(MinecraftClient client, PendingLookup lookup, InstaBanLookupOutcome outcome) {
        if (outcome.type() != InstaBanLookupOutcome.Type.SUCCESS || outcome.response() == null) {
            restoreStatus(client, lookup);
            showFailure(client, outcome.type());
            return;
        }
        List<InstaBanResult> results = outcome.response().results();
        if (results.size() != lookup.players.size()) {
            restoreStatus(client, lookup);
            showFailure(client, InstaBanLookupOutcome.Type.MALFORMED_RESPONSE);
            return;
        }

        if (results.size() == 1) {
            applySingleResult(client, lookup, results.getFirst());
            if (outcome.response().stale()) {
                ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.do_not_insta_ban.index_stale");
            }
            return;
        }

        restoreStatus(client, lookup);
        for (InstaBanResult result : results) {
            Text message = multiResultButton(result);
            ChatUtils.sendClientMessage(client, message);
            ModerationChatService.captureClientMessage(message);
        }
        if (outcome.response().stale()) {
            ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.do_not_insta_ban.index_stale");
        }
    }

    private void applySingleResult(MinecraftClient client, PendingLookup lookup, InstaBanResult result) {
        InstaBanPresentation presentation = InstaBanPresentation.forResult(result);
        MutableText scanning = InstaBanChatHighlight.tint(lookup.scanning.original, presentation.color());
        if (lookup.names == lookup.scanning) {
            scanning.append(" ").append(statusButton(result));
            replace(client, lookup.scanning, scanning);
            ModerationChatService.replaceRecentText(lookup.scanning.original, scanning);
            refresh(client);
            return;
        }
        MutableText names = InstaBanChatHighlight.tint(lookup.names.original, presentation.color());
        if (lookup.statuses == null) {
            scanning.append(" ").append(statusButton(result));
            replace(client, lookup.scanning, scanning);
            ModerationChatService.replaceRecentText(lookup.scanning.original, scanning);
        } else {
            MutableText statuses = InstaBanChatHighlight.tint(lookup.statuses.original, presentation.color())
                    .append(" ").append(statusButton(result));
            replace(client, lookup.scanning, scanning);
            replace(client, lookup.statuses, statuses);
            ModerationChatService.replaceRecentText(lookup.scanning.original, scanning);
            ModerationChatService.replaceRecentText(lookup.statuses.original, statuses);
        }
        replace(client, lookup.names, names);
        ModerationChatService.replaceRecentText(lookup.names.original, names);
        refresh(client);
    }

    private static Text statusButton(InstaBanResult result) {
        InstaBanPresentation presentation = InstaBanPresentation.forResult(result);
        MutableText button = Text.literal("[" + presentation.statusLabel() + "]")
                .formatted(Formatting.BOLD)
                .styled(style -> style.withHoverEvent(new HoverEvent.ShowText(
                        Text.literal(hoverText(presentation, result.messageUrl() != null)))));
        if (result.messageUrl() != null) {
            button.styled(style -> style.withClickEvent(new ClickEvent.OpenUrl(result.messageUrl()))
                    .withUnderline(true));
        }
        return button;
    }

    private static Text multiResultButton(InstaBanResult result) {
        InstaBanPresentation presentation = InstaBanPresentation.forResult(result);
        MutableText button = Text.literal("[" + result.ign() + ": " + presentation.statusLabel() + "]")
                .formatted(Formatting.BOLD)
                .styled(style -> style.withHoverEvent(new HoverEvent.ShowText(
                        Text.literal(hoverText(presentation, result.messageUrl() != null)))));
        if (result.messageUrl() != null) {
            button.styled(style -> style.withClickEvent(new ClickEvent.OpenUrl(result.messageUrl()))
                    .withUnderline(true));
        }
        return InstaBanChatHighlight.tint(Text.literal("§8[§6DMLS§8] ").append(button), presentation.color());
    }

    private static String hoverText(InstaBanPresentation presentation, boolean hasEvidence) {
        return presentation.detail() + (hasEvidence ? "\nClick to open Discord evidence." : "\nNo evidence link is available.");
    }

    private void showFailure(MinecraftClient client, InstaBanLookupOutcome.Type failure) {
        String key = switch (failure) {
            case NOT_LINKED, INVALID_TOKEN -> "dmls.chat.do_not_insta_ban.not_linked";
            case AUTHORIZATION_STALE -> "dmls.chat.do_not_insta_ban.authorization_stale";
            case RATE_LIMITED -> "dmls.chat.do_not_insta_ban.rate_limited";
            case BAD_REQUEST -> "dmls.chat.do_not_insta_ban.bad_request";
            case CONFIGURATION_ERROR -> "dmls.chat.do_not_insta_ban.configuration";
            case MALFORMED_RESPONSE -> "dmls.chat.do_not_insta_ban.malformed";
            default -> "dmls.chat.do_not_insta_ban.temporary_error";
        };
        ChatUtils.sendTranslatedMessage(client, PREFIX, key);
    }

    private void animateSpinners(MinecraftClient client) {
        for (PendingLookup lookup : List.copyOf(pending)) updateSpinner(client, lookup);
    }

    private void updateSpinner(MinecraftClient client, PendingLookup lookup) {
        String frame = SPINNER[(int) ((ticks / 2) % SPINNER.length)];
        LineHandle target = lookup.statuses == null ? lookup.scanning : lookup.statuses;
        Text content = target.original.copy()
                .append(Text.literal(" " + frame).formatted(Formatting.WHITE));
        if (replace(client, target, content)) refresh(client);
    }

    private void restorePending(MinecraftClient client) {
        for (PendingLookup lookup : List.copyOf(pending)) restoreStatus(client, lookup);
        pending.clear();
    }

    private void restoreStatus(MinecraftClient client, PendingLookup lookup) {
        LineHandle target = lookup.statuses == null ? lookup.scanning : lookup.statuses;
        if (replace(client, target, target.original)) refresh(client);
    }

    private static boolean replace(MinecraftClient client, LineHandle handle, Text replacement) {
        if (client.inGameHud == null) return false;
        List<ChatHudLine> messages = ((ChatHudAccessor) client.inGameHud.getChatHud()).dmls$getMessages();
        int index = findLine(messages, handle.current);
        if (index < 0) return false;
        ChatHudLine current = messages.get(index);
        ChatHudLine updated = new ChatHudLine(current.creationTick(), replacement, current.signature(), current.indicator());
        messages.set(index, updated);
        handle.current = updated;
        return true;
    }

    private static int findLine(List<ChatHudLine> messages, ChatHudLine target) {
        for (int index = 0; index < messages.size(); index++) {
            if (messages.get(index) == target) return index;
        }
        for (int index = 0; index < messages.size(); index++) {
            ChatHudLine candidate = messages.get(index);
            if (candidate.creationTick() == target.creationTick()
                    && java.util.Objects.equals(candidate.signature(), target.signature())
                    && candidate.content().getString().equals(target.content().getString())) return index;
        }
        return -1;
    }

    private static void refresh(MinecraftClient client) {
        ((ChatHudAccessor) client.inGameHud.getChatHud()).dmls$refresh();
    }

    static List<String> extractGreenTokens(Text text) {
        return extractColoredTokensFromLogicalLine(text, -1, Formatting.GREEN);
    }

    static List<String> extractGreenTokensFromLogicalLine(Text text, int targetLine) {
        return extractColoredTokensFromLogicalLine(text, targetLine, Formatting.GREEN);
    }

    static List<String> extractRedTokens(Text text) {
        return extractRedTokensFromLogicalLine(text, -1);
    }

    static List<String> extractRedTokensFromLogicalLine(Text text, int targetLine) {
        return extractColoredTokensFromLogicalLine(text, targetLine, Formatting.RED);
    }

    static boolean shouldStartLookup(List<String> greenNames, List<String> bannedNames) {
        return greenNames != null && !greenNames.isEmpty() && bannedNames != null && !bannedNames.isEmpty();
    }

    private static List<String> extractColoredTokensFromLogicalLine(
            Text text, int targetLine, Formatting color
    ) {
        List<String> names = new ArrayList<>();
        Integer expectedColor = color.getColorValue();
        int[] line = {0};
        text.visit((style, segment) -> {
            String[] pieces = segment.split("\\R", -1);
            for (int index = 0; index < pieces.length; index++) {
                if ((targetLine < 0 || line[0] == targetLine) && isColor(style, expectedColor)) {
                    Matcher matcher = GREEN_TOKEN.matcher(pieces[index]);
                    while (matcher.find()) names.add(matcher.group());
                }
                if (index + 1 < pieces.length) line[0]++;
            }
            return Optional.empty();
        }, Style.EMPTY);
        return names;
    }

    private static boolean isColor(Style style, Integer expected) {
        return expected != null && style.getColor() != null && style.getColor().getRgb() == expected;
    }

    private static String normalized(Text text) {
        return normalizedLine(text.getString());
    }

    /** Stable across spinner frames and appended result buttons, which preserve the scan header and green names. */
    static String stableBlockIdentity(Text scanning, List<String> greenNames) {
        String header = logicalLines(scanning == null ? "" : scanning.getString()).stream()
                .filter(DoNotInstaBanModule::isScanningLine)
                .findFirst()
                .orElseGet(() -> normalized(scanning == null ? Text.empty() : scanning));
        return header + "|" + String.join("\u001F", greenNames == null ? List.of() : greenNames);
    }

    static boolean isCombinedScanHeader(String text) {
        List<String> lines = logicalLines(text);
        return lines.size() == 2 && isScanningLine(lines.get(0)) && isStatusLine(lines.get(1));
    }

    static boolean isCompleteScanBlock(String text) {
        List<String> lines = logicalLines(text);
        return lines.size() == 3 && isScanningLine(lines.get(0)) && isStatusLine(lines.get(1));
    }

    static boolean isScanningLine(String text) {
        return SCANNING_LINE.matcher(normalizedLine(text)).matches();
    }

    static boolean isStatusLine(String text) {
        return STATUS_LINE.matcher(normalizedLine(text)).matches();
    }

    private static String normalizedLine(String text) {
        return ChatUtils.cleanLine(text == null ? "" : text).replaceAll("\\s+", " ");
    }

    private static List<String> logicalLines(String text) {
        if (text == null) return List.of();
        return java.util.Arrays.stream(text.replace("\\n", "\n").split("\\R"))
                .map(DoNotInstaBanModule::normalizedLine)
                .filter(line -> !line.isEmpty())
                .toList();
    }

    private void trimSeenBlocks() {
        while (seenBlocks.size() > MAX_SEEN_BLOCKS) {
            Iterator<String> iterator = seenBlocks.iterator();
            iterator.next();
            iterator.remove();
        }
    }

    private static final class LineHandle {
        private final Text original;
        private ChatHudLine current;

        private LineHandle(ChatHudLine current) {
            this.original = current.content();
            this.current = current;
        }
    }

    private static final class PendingLookup {
        private final Object connectionIdentity;
        private final LineHandle scanning;
        private final LineHandle statuses;
        private final LineHandle names;
        private final List<String> players;

        private PendingLookup(Object connectionIdentity, LineHandle scanning, LineHandle statuses,
                              LineHandle names, List<String> players) {
            this.connectionIdentity = connectionIdentity;
            this.scanning = scanning;
            this.statuses = statuses;
            this.names = names;
            this.players = players;
        }
    }
}
