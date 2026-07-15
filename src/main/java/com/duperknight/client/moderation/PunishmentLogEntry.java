package com.duperknight.client.moderation;

import java.util.UUID;

public record PunishmentLogEntry(PunishmentType type, String playerName, UUID playerUuid, String staffName) {
}
