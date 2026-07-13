package com.duperknight.client.parser;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Stateful parser for Stoneworks' /history output for the currently checked account.
 */
public final class HistoryOutputParser {
    private static final Pattern HEADER = Pattern.compile("^history for ([A-Za-z0-9_]{3,16}) \\(limit: \\d+\\):$", Pattern.CASE_INSENSITIVE);
    private static final String NEVER_JOINED = "user has not joined before.";
    private final String username;
    private final Pattern punishmentRecord;
    private final Pattern targetHeader;
    private boolean headerSeen;
    private boolean neverJoined;
    private boolean failed;
    private int bans;
    private int mutes;
    private int warns;
    private int kicks;

    public HistoryOutputParser(String username) {
        this.username = username;
        this.punishmentRecord = Pattern.compile("(?s)^.*\\b" + Pattern.quote(username)
                + " was (muted|warned|kicked|banned) by .*$", Pattern.CASE_INSENSITIVE);
        this.targetHeader = Pattern.compile("^history\\s+for\\s+" + Pattern.quote(username)
                + "\\b.*$", Pattern.CASE_INSENSITIVE);
    }

    public Event accept(String line) {
        String value = line == null ? "" : line.trim();
        if (value.equalsIgnoreCase(NEVER_JOINED)) {
            neverJoined = true;
            return Event.COMPLETE;
        }
        Matcher header = HEADER.matcher(value);
        if (header.matches() && header.group(1).equalsIgnoreCase(username)) {
            headerSeen = true;
            return Event.RECOGNIZED;
        }
        if (targetHeader.matcher(value).matches()) {
            failed = true;
            return Event.FAILED;
        }
        if (!headerSeen) return Event.UNRELATED;
        Matcher record = punishmentRecord.matcher(value);
        if (!record.matches()) return Event.UNRELATED;
        String type = record.group(1).toLowerCase(Locale.ROOT);
        if (type.equals("banned")) bans++;
        else if (type.equals("muted")) mutes++;
        else if (type.equals("warned")) warns++;
        else if (type.equals("kicked")) kicks++;
        return Event.RECOGNIZED;
    }

    public Result result() {
        if (failed) return new Result(Status.MALFORMED, 0, 0, 0, 0);
        if (neverJoined) return new Result(Status.NEVER_JOINED, 0, 0, 0, 0);
        if (headerSeen && bans + mutes + warns + kicks == 0) return new Result(Status.CLEAN, 0, 0, 0, 0);
        if (headerSeen) return new Result(Status.PARSED, bans, mutes, warns, kicks);
        return new Result(Status.UNKNOWN, bans, mutes, warns, kicks);
    }

    public enum Event { UNRELATED, RECOGNIZED, COMPLETE, FAILED }
    public enum Status { CLEAN, PARSED, NEVER_JOINED, UNKNOWN, MALFORMED }
    public record Result(Status status, int bans, int mutes, int warns, int kicks) { }
}
