package com.duperknight.client.reimbursement;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/** Mutable connection-scoped editor state. Execution always consumes an immutable snapshot. */
public final class ReimbursementDraft {
    private final List<ReimbursementEntry> entries = new ArrayList<>();
    private String ign = "";
    private String discordUsername = "";
    private String discordId = "";
    private String reason = "";
    private String ticket = "";
    private String lastAutomaticIgn = "";

    public List<ReimbursementEntry> entries() {
        return List.copyOf(entries);
    }

    public void add(ReimbursementEntry entry) {
        entries.add(Objects.requireNonNull(entry, "entry"));
        refreshAutomaticIgn();
    }

    public void set(int index, ReimbursementEntry entry) {
        entries.set(index, Objects.requireNonNull(entry, "entry"));
        refreshAutomaticIgn();
    }

    public void remove(int index) {
        entries.remove(index);
        refreshAutomaticIgn();
    }

    public void move(int index, int delta) {
        int target = index + delta;
        if (index < 0 || index >= entries.size() || target < 0 || target >= entries.size()) return;
        ReimbursementEntry moved = entries.remove(index);
        entries.add(target, moved);
    }

    public int removeContainersIf(Predicate<ContainerTarget> remove) {
        Objects.requireNonNull(remove, "remove");
        int removed = 0;
        for (int index = 0; index < entries.size(); index++) {
            ReimbursementEntry entry = entries.get(index);
            if (!(entry instanceof ItemEntry item) || item.containers().isEmpty()) continue;
            List<ContainerTarget> retained = item.containers().stream()
                    .filter(target -> !remove.test(target))
                    .toList();
            removed += item.containers().size() - retained.size();
            if (retained.size() != item.containers().size()) {
                entries.set(index, item.withContainers(retained));
            }
        }
        return removed;
    }

    public String ign() {
        return ign;
    }

    public void setIgn(String value) {
        ign = Objects.requireNonNullElse(value, "");
    }

    public String discordUsername() {
        return discordUsername;
    }

    public void setDiscordUsername(String value) {
        discordUsername = Objects.requireNonNullElse(value, "");
    }

    public String discordId() {
        return discordId;
    }

    public void setDiscordId(String value) {
        discordId = Objects.requireNonNullElse(value, "");
    }

    public String reason() {
        return reason;
    }

    public void setReason(String value) {
        reason = Objects.requireNonNullElse(value, "");
    }

    public String ticket() {
        return ticket;
    }

    public void setTicket(String value) {
        ticket = Objects.requireNonNullElse(value, "");
    }

    public boolean hasContent() {
        return !entries.isEmpty() || !ign.isBlank() || !discordUsername.isBlank()
                || !discordId.isBlank() || !reason.isBlank() || !ticket.isBlank();
    }

    public void clear() {
        entries.clear();
        ign = "";
        discordUsername = "";
        discordId = "";
        reason = "";
        ticket = "";
        lastAutomaticIgn = "";
    }

    public Snapshot snapshot() {
        return new Snapshot(entries(), ign.trim(), discordUsername.trim(), discordId.trim(),
                reason.trim(), ticket.trim());
    }

    private void refreshAutomaticIgn() {
        String automatic = playerTargets(entries);
        if (ign.isBlank() || ign.equals(lastAutomaticIgn)) {
            ign = automatic;
        }
        lastAutomaticIgn = automatic;
    }

    public static String playerTargets(List<ReimbursementEntry> entries) {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        for (ReimbursementEntry entry : entries) {
            if (entry.destination() == Destination.PLAYER && !entry.playerIgn().isBlank()) {
                names.add(entry.playerIgn().trim());
            }
        }
        return String.join(", ", names);
    }

    public record Snapshot(
            List<ReimbursementEntry> entries,
            String ign,
            String discordUsername,
            String discordId,
            String reason,
            String ticket
    ) {
        public Snapshot {
            entries = List.copyOf(entries);
        }
    }
}
