package com.duperknight.client.modules;

import com.duperknight.client.gui.modules.PunishmentHelperScreen;
import com.duperknight.client.moderation.ModerationActions;
import com.duperknight.client.moderation.PunishmentRequest;
import com.duperknight.client.moderation.PunishmentType;
import com.duperknight.client.rulebook.RulebookRule;
import com.duperknight.client.rulebook.RulebookService;
import com.duperknight.client.utils.ChatUtils;
import com.duperknight.client.utils.DMLSConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Rulebook browser and ban-log composer built from the Stoneworks rulebook. */
public final class PunishmentHelperModule extends DMLSModule {
    /** The minimum rank required to actually issue a ban from the helper. */
    public static final StaffRank BAN_RANK = StaffRank.MODERATOR;

    private static final String PREFIX = "§8[§6DMLS - Punish§8] §7";
    private static final Pattern DURATION = Pattern.compile(
            "([1-9][0-9]{0,3})(s|m|h|d|w|mo|y)", Pattern.CASE_INSENSITIVE);

    /**
     * Fixed actions shown by the rulebook punishment composer. Mutes and bans get their duration from the form.
     */
    public enum PunishmentOption {
        WARNING("Warning", PunishmentType.WARNING),
        MUTE("Mute", PunishmentType.MUTE),
        BAN("Ban", PunishmentType.BAN);

        private final String label;
        private final PunishmentType type;

        PunishmentOption(String label, PunishmentType type) {
            this.label = label;
            this.type = type;
        }

        public String label() {
            return label;
        }

        public PunishmentType type() {
            return type;
        }

        public Text displayText() {
            return Text.literal(label);
        }
    }

    /** A prepared request, or a typed validation failure when request is null. */
    public record PunishmentPreparation(PunishmentRequest request, PunishmentRequest.Validation validation) {
        public PunishmentPreparation {
            Objects.requireNonNull(validation, "validation");
            if ((validation == PunishmentRequest.Validation.VALID) != (request != null)) {
                throw new IllegalArgumentException("Valid preparations require a request and invalid ones must not have one");
            }
        }

        public boolean isValid() {
            return validation == PunishmentRequest.Validation.VALID;
        }
    }

    /** Result of attempting to dispatch a prepared punishment. */
    public enum PunishmentOutcome {
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
    public ModuleCategory category() {
        return ModuleCategory.GENERAL;
    }

    @Override
    public void openScreen(MinecraftClient client, Screen parent) {
        client.setScreen(new PunishmentHelperScreen(parent, this));
    }

    @Override
    public void register() {
        RulebookService.shared().register();
    }

    public static List<RulebookRule> rules() {
        return search("");
    }

    /** Returns rules matching the query in id, section, title or punishment. */
    public static List<RulebookRule> search(String query) {
        return RulebookService.shared().search(query).stream().filter(RulebookRule::punishable).toList();
    }

    /** Whether the hub-detected rank may issue bans. Retained for callers that only care about bans. */
    public static boolean canBan() {
        return DMLSConfig.staffRank().isAtLeast(BAN_RANK);
    }

    /** Whether the hub-detected rank may issue the selected punishment. */
    public static boolean canPunish(PunishmentOption option) {
        return option != null && DMLSConfig.staffRank().isStaff()
                && DMLSConfig.staffRank().isAtLeast(option.type().minimumRank());
    }

    /** Normalizes and validates the selected fixed punishment without dispatching anything. */
    public static PunishmentPreparation preparePunishment(String ign, PunishmentOption option,
                                                           String duration, String reason) {
        String cleanIgn = Objects.requireNonNullElse(ign, "").trim();
        String cleanReason = Objects.requireNonNullElse(reason, "").trim();
        PunishmentType type = option == null ? null : option.type();
        String cleanDuration = normalizedDuration(option, duration);
        PunishmentRequest.Validation validation = PunishmentRequest.validate(type, cleanIgn, cleanDuration, cleanReason);
        if (validation != PunishmentRequest.Validation.VALID) {
            return new PunishmentPreparation(null, validation);
        }
        return new PunishmentPreparation(new PunishmentRequest(type, cleanIgn, cleanDuration, cleanReason),
                PunishmentRequest.Validation.VALID);
    }

    /** Formats the selected command duration for the copied staff log (for example, 4w becomes 4 week ban). */
    public static String readablePunishment(PunishmentOption option, String duration) {
        if (option == null) return "";
        if (option.type() == PunishmentType.WARNING) return "warning";

        String cleanDuration = normalizedDuration(option, duration);
        String action = option.type() == PunishmentType.MUTE ? "mute" : "ban";
        if (cleanDuration.equals("perm") || cleanDuration.equals("permanent")) {
            return "permanent " + action;
        }

        Matcher matcher = DURATION.matcher(cleanDuration);
        if (!matcher.matches()) {
            return cleanDuration + " " + action;
        }
        String unit = switch (matcher.group(2).toLowerCase(Locale.ROOT)) {
            case "s" -> "second";
            case "m" -> "minute";
            case "h" -> "hour";
            case "d" -> "day";
            case "w" -> "week";
            case "mo" -> "month";
            case "y" -> "year";
            default -> throw new IllegalStateException("Validated duration has an unknown suffix");
        };
        return matcher.group(1) + " " + unit + " " + action;
    }

    /** Dispatches a previously prepared immutable request after rechecking rank and server safety. */
    public static PunishmentOutcome punish(MinecraftClient client, PunishmentRequest request) {
        if (request == null) {
            return PunishmentOutcome.INVALID;
        }
        if (!DMLSConfig.staffRank().isStaff()
                || !DMLSConfig.staffRank().isAtLeast(request.type().minimumRank())) {
            ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.rank.required",
                    request.type().minimumRank().displayName(), DMLSConfig.staffRank().displayName());
            return PunishmentOutcome.RANK_BLOCKED;
        }

        ModerationActions.Outcome outcome = ModerationActions.punish(client, request);
        return switch (outcome) {
            case SENT -> {
                sendSuccessMessage(client, request, false);
                yield PunishmentOutcome.SENT;
            }
            case SIMULATED -> {
                sendSuccessMessage(client, request, true);
                yield PunishmentOutcome.SIMULATED;
            }
            case INVALID -> PunishmentOutcome.INVALID;
            case RANK_BLOCKED -> PunishmentOutcome.RANK_BLOCKED;
            case BUSY -> PunishmentOutcome.BUSY;
            case BLOCKED -> {
                ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.command.not_sent");
                yield PunishmentOutcome.SERVER_BLOCKED;
            }
        };
    }

    /** Sends the localized validation message used by both direct and GUI entrypoints. */
    public static void sendValidationFailure(MinecraftClient client, PunishmentRequest.Validation validation) {
        ChatUtils.sendTranslatedMessage(client, PREFIX, validationTranslationKey(validation));
    }

    public static String validationTranslationKey(PunishmentRequest.Validation validation) {
        return switch (validation) {
            case INVALID_IGN -> "dmls.validation.ban.ign";
            case INVALID_DURATION -> "dmls.validation.ban.duration";
            case INVALID_REASON -> "dmls.validation.ban.reason";
            case INVALID_TYPE -> "dmls.validation.punishment.type";
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

    private static String normalizedDuration(PunishmentOption option, String duration) {
        if (option == null || !option.type().durationRequired()) return "";
        String clean = Objects.requireNonNullElse(duration, "").trim().toLowerCase(Locale.ROOT);
        return clean.isEmpty() ? "perm" : clean;
    }

    private static void sendSuccessMessage(MinecraftClient client, PunishmentRequest request, boolean simulated) {
        switch (request.type()) {
            case BAN -> ChatUtils.sendTranslatedMessage(client, PREFIX,
                    simulated ? "dmls.chat.punish.ban_simulated" : "dmls.chat.punish.banned",
                    request.ign(), request.duration());
            case MUTE -> ChatUtils.sendTranslatedMessage(client, PREFIX,
                    simulated ? "dmls.chat.punish.mute_simulated" : "dmls.chat.punish.muted",
                    request.ign(), request.duration());
            case WARNING -> ChatUtils.sendTranslatedMessage(client, PREFIX,
                    simulated ? "dmls.chat.punish.warning_simulated" : "dmls.chat.punish.warned",
                    request.ign());
            case KICK -> throw new IllegalArgumentException("The rulebook composer does not offer kicks");
        }
    }
}
