package com.duperknight.client.modules;

import com.duperknight.DMLS;
import com.duperknight.client.gui.modules.PunishmentHelperScreen;
import com.duperknight.client.session.CommandDispatch;
import com.duperknight.client.session.ManagedOperation;
import com.duperknight.client.session.OperationCoordinator;
import com.duperknight.client.session.OperationHandle;
import com.duperknight.client.session.OperationStartResult;
import com.duperknight.client.utils.ChatUtils;
import com.duperknight.client.utils.DMLSConfig;
import com.duperknight.client.utils.InputValidators;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Rulebook browser and ban-log composer built from the Stoneworks rulebook. */
public final class PunishmentHelperModule extends DMLSModule {
    /** The minimum rank required to actually issue a ban from the helper. */
    public static final StaffRank BAN_RANK = StaffRank.MODERATOR;
    public static final int MAX_BAN_REASON_LENGTH = 200;

    private static final String PREFIX = "§8[§6DMLS - Punish§8] §7";
    private static final Identifier RULEBOOK = Identifier.of(DMLS.MOD_ID.toLowerCase(Locale.ROOT), "rulebook.json");
    private static final Pattern DURATION = Pattern.compile("(?:[1-9][0-9]{0,3}(?:s|m|h|d|w|mo|y)|perm(?:anent)?)",
            Pattern.CASE_INSENSITIVE);
    private static final Set<String> RULE_FIELDS = Set.of("id", "section", "title", "punishment");

    private static volatile List<Rule> loadedRules = List.of();
    private static volatile String rulebookError = "";
    private static volatile boolean rulebookLoadAttempted;

    /** One validated rulebook entry. */
    public record Rule(String id, String section, String title, String punishment) {
        public Rule {
            id = requireRuleField(id, "id");
            section = requireRuleField(section, "section");
            title = requireRuleField(title, "title");
            punishment = requireRuleField(punishment, "punishment");
        }

        public String label() {
            return id + " " + title;
        }

        public boolean matches(String needle) {
            return id.toLowerCase(Locale.ROOT).contains(needle)
                    || section.toLowerCase(Locale.ROOT).contains(needle)
                    || title.toLowerCase(Locale.ROOT).contains(needle)
                    || punishment.toLowerCase(Locale.ROOT).contains(needle);
        }
    }

    /** An immutable, normalized request safe to interpolate into one /ban command. */
    public record BanRequest(String ign, String duration, String reason) {
        public BanRequest {
            ign = Objects.requireNonNullElse(ign, "").trim();
            duration = Objects.requireNonNullElse(duration, "").trim().toLowerCase(Locale.ROOT);
            reason = Objects.requireNonNullElse(reason, "").trim();
            BanValidation validation = validateBanFields(ign, duration, reason);
            if (validation != BanValidation.VALID) {
                throw new IllegalArgumentException("Invalid ban request: " + validation);
            }
        }

        public String command() {
            return "ban %s %s %s".formatted(ign, duration, reason);
        }
    }

    /** Validation result for a ban request. */
    public enum BanValidation {
        VALID,
        INVALID_IGN,
        INVALID_DURATION,
        INVALID_REASON
    }

    /** A prepared request, or a typed validation failure when request is null. */
    public record BanPreparation(BanRequest request, BanValidation validation) {
        public BanPreparation {
            Objects.requireNonNull(validation, "validation");
            if ((validation == BanValidation.VALID) != (request != null)) {
                throw new IllegalArgumentException("Valid preparations require a request and invalid ones must not have one");
            }
        }

        public boolean isValid() {
            return validation == BanValidation.VALID;
        }
    }

    /** Result of attempting to dispatch a prepared ban. */
    public enum BanOutcome {
        SENT,
        SIMULATED,
        INVALID,
        RANK_BLOCKED,
        SERVER_BLOCKED,
        BUSY
    }

    public PunishmentHelperModule() {
        super(StaffRank.HELPER);
    }

    @Override
    public Text displayName() {
        return Text.translatable("dmls.module.punish.name");
    }

    @Override
    public ItemStack icon() {
        return new ItemStack(Items.WRITTEN_BOOK);
    }

    @Override
    public List<Text> description() {
        return List.of(
                Text.translatable("dmls.module.punish.description.1"),
                Text.translatable("dmls.module.punish.description.2")
        );
    }

    @Override
    public void openScreen(MinecraftClient client, Screen parent) {
        client.setScreen(new PunishmentHelperScreen(parent, this));
    }

    @Override
    public void register() {
        loadRules();
    }

    /** Returns all rules, loading them on first use. */
    public static List<Rule> rules() {
        if (!rulebookLoadAttempted) {
            loadRules();
        }
        return loadedRules;
    }

    /** Describes the latest bundled-rulebook load failure, if any. */
    public static Optional<String> rulebookError() {
        return rulebookError.isEmpty() ? Optional.empty() : Optional.of(rulebookError);
    }

    /** Returns rules matching the query in id, section, title or punishment. */
    public static List<Rule> search(String query) {
        String needle = Objects.requireNonNullElse(query, "").trim().toLowerCase(Locale.ROOT);
        if (needle.isEmpty()) {
            return rules();
        }
        return rules().stream().filter(rule -> rule.matches(needle)).collect(Collectors.toList());
    }

    /** Whether the hub-detected rank may issue bans. */
    public static boolean canBan() {
        return DMLSConfig.staffRank().isAtLeast(BAN_RANK);
    }

    /** Normalizes and validates user-entered ban fields without dispatching anything. */
    public static BanPreparation prepareBan(String ign, String duration, String reason) {
        String cleanIgn = Objects.requireNonNullElse(ign, "").trim();
        String cleanDuration = Objects.requireNonNullElse(duration, "").trim().toLowerCase(Locale.ROOT);
        String cleanReason = Objects.requireNonNullElse(reason, "").trim();
        BanValidation validation = validateBanFields(cleanIgn, cleanDuration, cleanReason);
        if (validation != BanValidation.VALID) {
            return new BanPreparation(null, validation);
        }
        return new BanPreparation(new BanRequest(cleanIgn, cleanDuration, cleanReason), BanValidation.VALID);
    }

    /** Dispatches a previously prepared immutable request after rechecking rank and server safety. */
    public static BanOutcome ban(MinecraftClient client, BanRequest request) {
        if (request == null) {
            return BanOutcome.INVALID;
        }
        if (!canBan()) {
            ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.rank.required",
                    BAN_RANK.displayName(), DMLSConfig.staffRank().displayName());
            return BanOutcome.RANK_BLOCKED;
        }

        BanDispatchOperation operation = new BanDispatchOperation(request);
        OperationStartResult started = OperationCoordinator.global().start(
                client, "punishment-ban", "Ban confirmation", operation);
        if (started == OperationStartResult.BUSY) {
            String owner = OperationCoordinator.global().activeDescriptor()
                    .map(descriptor -> descriptor.displayName()).orElse("another operation");
            ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.operation.busy", owner);
            return BanOutcome.BUSY;
        }
        if (started != OperationStartResult.STARTED) {
            ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.command.not_sent");
            return BanOutcome.SERVER_BLOCKED;
        }

        CommandDispatch dispatch = operation.dispatch;
        if (dispatch == CommandDispatch.SENT) {
            ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.punish.banned", request.ign(), request.duration());
            return BanOutcome.SENT;
        }
        if (dispatch == CommandDispatch.SIMULATED) {
            return BanOutcome.SIMULATED;
        }
        ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.command.not_sent");
        return BanOutcome.SERVER_BLOCKED;
    }

    /** Sends the localized validation message used by both direct and GUI entrypoints. */
    public static void sendValidationFailure(MinecraftClient client, BanValidation validation) {
        ChatUtils.sendTranslatedMessage(client, PREFIX, validationTranslationKey(validation));
    }

    public static String validationTranslationKey(BanValidation validation) {
        return switch (validation) {
            case INVALID_IGN -> "dmls.validation.ban.ign";
            case INVALID_DURATION -> "dmls.validation.ban.duration";
            case INVALID_REASON -> "dmls.validation.ban.reason";
            case VALID -> throw new IllegalArgumentException("VALID is not a failure");
        };
    }

    /** Builds the ban-log text in the Stoneworks format. */
    public static String banLog(String ign, String discord, String reason, String type,
                                String ticket, String comments, String evidence) {
        return "Ban Format\n"
                + "IGN - " + ign.trim() + "\n"
                + "Discord - " + discord.trim() + "\n"
                + "Reason - " + reason.trim() + "\n"
                + "Type - " + type.trim() + "\n"
                + "Ticket & Category - " + ticket.trim() + "\n"
                + "Comments - " + comments.trim() + "\n"
                + "Evidence - " + evidence.trim();
    }

    /** Parses and validates a complete rulebook document using Gson's strict token stream. */
    public static List<Rule> parseRules(Reader reader) throws IOException {
        Objects.requireNonNull(reader, "reader");
        JsonReader json = new JsonReader(reader);
        try {
            requireToken(json, JsonToken.BEGIN_OBJECT, "Rulebook root must be an object");
            json.beginObject();
            Set<String> rootFields = new HashSet<>();
            List<Rule> parsed = null;
            while (json.hasNext()) {
                String field = json.nextName();
                if (!rootFields.add(field)) {
                    throw new IOException("Duplicate rulebook field: " + field);
                }
                if (!field.equals("rules")) {
                    throw new IOException("Unknown rulebook field: " + field);
                }
                parsed = readRulesArray(json);
            }
            json.endObject();
            requireToken(json, JsonToken.END_DOCUMENT, "Rulebook contains trailing JSON data");
            if (parsed == null || parsed.isEmpty()) {
                throw new IOException("Rulebook must contain a non-empty rules array");
            }
            return List.copyOf(parsed);
        } catch (IllegalStateException exception) {
            throw new IOException("Rulebook is not valid JSON", exception);
        }
    }

    private static List<Rule> readRulesArray(JsonReader json) throws IOException {
        requireToken(json, JsonToken.BEGIN_ARRAY, "Rulebook rules must be an array");
        json.beginArray();
        Set<String> ids = new HashSet<>();
        java.util.ArrayList<Rule> parsed = new java.util.ArrayList<>();
        while (json.hasNext()) {
            int index = parsed.size();
            Rule rule = readRule(json, index);
            if (!ids.add(rule.id().toLowerCase(Locale.ROOT))) {
                throw new IOException("Duplicate rule id: " + rule.id());
            }
            parsed.add(rule);
        }
        json.endArray();
        return parsed;
    }

    private static Rule readRule(JsonReader json, int index) throws IOException {
        requireToken(json, JsonToken.BEGIN_OBJECT, "Rulebook entry " + index + " must be an object");
        json.beginObject();
        java.util.Map<String, String> fields = new java.util.HashMap<>();
        while (json.hasNext()) {
            String field = json.nextName();
            if (!RULE_FIELDS.contains(field)) {
                throw new IOException("Unknown field in rulebook entry " + index + ": " + field);
            }
            if (fields.containsKey(field)) {
                throw new IOException("Duplicate field in rulebook entry " + index + ": " + field);
            }
            requireToken(json, JsonToken.STRING,
                    "Rulebook entry " + index + " field " + field + " must be a string");
            fields.put(field, json.nextString());
        }
        json.endObject();
        try {
            return new Rule(fields.get("id"), fields.get("section"), fields.get("title"), fields.get("punishment"));
        } catch (IllegalArgumentException exception) {
            throw new IOException("Invalid rulebook entry " + index + ": " + exception.getMessage(), exception);
        }
    }

    private static void requireToken(JsonReader json, JsonToken expected, String error) throws IOException {
        if (json.peek() != expected) throw new IOException(error);
    }

    private static synchronized void loadRules() {
        if (rulebookLoadAttempted) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getResourceManager() == null) {
            return;
        }
        rulebookLoadAttempted = true;

        try (InputStream in = client.getResourceManager().open(RULEBOOK);
             InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            loadedRules = parseRules(reader);
            rulebookError = "";
        } catch (Exception exception) {
            rulebookError = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
            DMLS.LOGGER.warn("Failed to load the rulebook: {}", rulebookError, exception);
        }
    }

    private static BanValidation validateBanFields(String ign, String duration, String reason) {
        if (!InputValidators.isUsername(ign)) {
            return BanValidation.INVALID_IGN;
        }
        if (!DURATION.matcher(duration).matches()) {
            return BanValidation.INVALID_DURATION;
        }
        if (reason.isEmpty() || reason.length() > MAX_BAN_REASON_LENGTH || reason.indexOf('§') >= 0
                || reason.chars().anyMatch(Character::isISOControl)) {
            return BanValidation.INVALID_REASON;
        }
        return BanValidation.VALID;
    }

    private static String requireRuleField(String value, String field) {
        String clean = Objects.requireNonNullElse(value, "").trim();
        if (clean.isEmpty()) {
            throw new IllegalArgumentException("missing " + field);
        }
        return clean;
    }

    /** Acquires coordinator exclusivity for the exact dispatch, then releases it immediately. */
    private static final class BanDispatchOperation implements ManagedOperation {
        private final BanRequest request;
        private CommandDispatch dispatch = CommandDispatch.BLOCKED;

        private BanDispatchOperation(BanRequest request) {
            this.request = request;
        }

        @Override
        public void onStarted(OperationHandle handle, MinecraftClient client) {
            dispatch = handle.dispatchCommand(client, request.command());
            handle.complete();
        }
    }

}
