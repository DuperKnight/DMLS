package com.duperknight.client.moderation;

import java.util.List;

@FunctionalInterface
public interface PunishmentLogSource {
    PunishmentLogSource EMPTY = List::of;

    List<PunishmentLogEntry> latest();
}
