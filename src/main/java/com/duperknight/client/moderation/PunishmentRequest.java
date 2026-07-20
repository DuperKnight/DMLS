package com.duperknight.client.moderation;

import com.duperknight.client.utils.InputValidators;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/** Validated punishment form contents and exact server command. */
public record PunishmentRequest(PunishmentType type, String ign, String duration, String reason) {
    public static final int MAX_REASON_LENGTH = 200;
    private static final Pattern DURATION = Pattern.compile(
            "(?:[1-9][0-9]{0,3}(?:s|m|h|d|w|mo|y)|perm(?:anent)?)", Pattern.CASE_INSENSITIVE);

    public PunishmentRequest {
        type = Objects.requireNonNull(type, "type");
        ign = Objects.requireNonNullElse(ign, "").trim();
        duration = Objects.requireNonNullElse(duration, "").trim().toLowerCase(Locale.ROOT);
        reason = Objects.requireNonNullElse(reason, "").trim();
        Validation validation = validate(type, ign, duration, reason);
        if (validation != Validation.VALID) throw new IllegalArgumentException(validation.name());
    }

    public String command() {
        if ((type == PunishmentType.MUTE || type == PunishmentType.BAN)
                && duration.matches("(?i)perm(?:anent)?")) {
            return "%s %s %s".formatted(type.command(), ign, reason);
        }
        return type.durationRequired()
                ? "%s %s %s %s".formatted(type.command(), ign, duration, reason)
                : "%s %s %s".formatted(type.command(), ign, reason);
    }

    public static Validation validate(PunishmentType type, String ign, String duration, String reason) {
        if (type == null) return Validation.INVALID_TYPE;
        if (!InputValidators.isUsername(Objects.requireNonNullElse(ign, "").trim())) return Validation.INVALID_IGN;
        if (type.durationRequired() && !DURATION.matcher(Objects.requireNonNullElse(duration, "").trim()).matches()) {
            return Validation.INVALID_DURATION;
        }
        String cleanReason = Objects.requireNonNullElse(reason, "").trim();
        if (cleanReason.isEmpty() || cleanReason.length() > MAX_REASON_LENGTH || cleanReason.indexOf('§') >= 0
                || cleanReason.chars().anyMatch(Character::isISOControl)) {
            return Validation.INVALID_REASON;
        }
        return Validation.VALID;
    }

    public enum Validation { VALID, INVALID_TYPE, INVALID_IGN, INVALID_DURATION, INVALID_REASON }
}
