package com.duperknight.client.war;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Versioned durable state. Fields are intentionally mutable for Gson and state-machine checkpoints. */
public final class WarManagerState {
    public static final int CURRENT_VERSION = 3;

    public int version = CURRENT_VERSION;
    public Home home;
    public boolean purgeApplied;
    public String purgeServer = "";
    public PurgeTransition purgeTransition = PurgeTransition.NONE;
    public int purgeCommandIndex;
    public List<War> wars = new ArrayList<>();

    public void normalize() {
        version = CURRENT_VERSION;
        purgeServer = Objects.requireNonNullElse(purgeServer, "");
        purgeTransition = purgeTransition == null ? PurgeTransition.NONE : purgeTransition;
        purgeCommandIndex = Math.max(0, purgeCommandIndex);
        if (wars == null) wars = new ArrayList<>();
        wars.removeIf(Objects::isNull);
        wars.forEach(War::normalize);
    }

    public static final class Home {
        public String server = "";
        public int x;
        public int y;
        public int z;

        public Home() {
        }

        public Home(String server, int x, int y, int z) {
            this.server = Objects.requireNonNullElse(server, "");
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public String coordinates() {
            return x + " " + y + " " + z;
        }
    }

    public static final class War {
        public String id = "";
        public String server = "";
        public String attacker = "";
        public String defender = "";
        public int countdownMinutes;
        public long scheduledStartMillis;
        public long warStartMillis;
        public long scheduledEndMillis;
        public long cancelledAtMillis;
        public Status status = Status.SETUP;
        public Phase phase = Phase.SET_PEACEFUL_ATTACKER;
        public String pendingCommand = "";
        public Phase pendingNextPhase;
        public String error = "";
        public Claim attackerClaim = new Claim();
        public Claim defenderClaim = new Claim();

        public void normalize() {
            id = Objects.requireNonNullElse(id, "");
            server = Objects.requireNonNullElse(server, "");
            attacker = Objects.requireNonNullElse(attacker, "");
            defender = Objects.requireNonNullElse(defender, "");
            status = status == null ? Status.PAUSED : status;
            phase = phase == null ? Phase.SET_PEACEFUL_ATTACKER : phase;
            pendingCommand = Objects.requireNonNullElse(pendingCommand, "");
            error = Objects.requireNonNullElse(error, "");
            attackerClaim = attackerClaim == null ? new Claim() : attackerClaim;
            defenderClaim = defenderClaim == null ? new Claim() : defenderClaim;
            attackerClaim.normalize(attacker);
            defenderClaim.normalize(defender);
        }

        public Claim claim(Side side) {
            return side == Side.ATTACKER ? attackerClaim : defenderClaim;
        }

        public boolean unfinished() {
            return status != Status.COMPLETED;
        }
    }

    public static final class Claim {
        public String name = "";
        public String originalNation = "";
        public String coloredNationName = "";
        public String transferredCapital = "";
        public Membership membership = Membership.UNKNOWN;
        public boolean restored;

        public void normalize(String fallbackName) {
            name = Objects.requireNonNullElse(name, "");
            if (name.isBlank()) name = Objects.requireNonNullElse(fallbackName, "");
            originalNation = Objects.requireNonNullElse(originalNation, "");
            coloredNationName = Objects.requireNonNullElse(coloredNationName, "");
            transferredCapital = Objects.requireNonNullElse(transferredCapital, "");
            membership = membership == null ? Membership.UNKNOWN : membership;
        }
    }

    public enum Side { ATTACKER, DEFENDER }
    public enum PurgeTransition { NONE, STARTING, ENDING }
    public enum Membership { UNKNOWN, NONE, LEFT, DELETED }
    public enum Status {
        SETUP,
        SCHEDULED,
        WAITING_FOR_WAR_START,
        ACTIVE,
        CANCELLING,
        RESTORING,
        COMPLETED,
        PAUSED
    }

    public enum Phase {
        SET_PEACEFUL_ATTACKER,
        SET_PEACEFUL_DEFENDER,
        EDIT_ATTACKER,
        LEAVE_ATTACKER,
        EDIT_DEFENDER,
        LEAVE_DEFENDER,
        CAPITAL_INFO,
        TRANSFER_CAPITAL,
        RETRY_LEAVE,
        DELETE_NATION,
        START_WAR,
        SETUP_COMPLETE,
        CANCEL_WAR,
        PURGE_START_KEEP_INVENTORY,
        PURGE_START_TELEPORT,
        PURGE_START_SPAWN,
        PURGE_START_BACK,
        PURGE_END_TELEPORT,
        PURGE_END_KEEP_INVENTORY,
        PURGE_END_KILL,
        RESTORE_ATTACKER_INFO,
        RESTORE_ATTACKER_COMMANDS,
        RESTORE_DEFENDER_INFO,
        RESTORE_DEFENDER_COMMANDS,
        CREATE_NATION_EDIT,
        CREATE_NATION_COMMAND,
        CREATE_NATION_DIALOG,
        COMPLETE
    }
}
