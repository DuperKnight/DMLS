package com.duperknight.client.parser;

import com.duperknight.client.utils.InputValidators;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Stateful, target-correlated parser for the known Stoneworks /alts output shapes. */
public final class AltsOutputParser {
    private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9_]{3,16}");
    private static final Pattern USERNAME_LIST = Pattern.compile(
            "[A-Za-z0-9_]{3,16}(?:\\s*,\\s*[A-Za-z0-9_]{3,16})*");

    private final String target;
    private final Pattern targetMention;
    private final LinkedHashMap<String, String> accounts = new LinkedHashMap<>();
    private boolean listStarted;
    private boolean sawAccountLine;

    public AltsOutputParser(String target) {
        if (!InputValidators.isUsername(target)) throw new IllegalArgumentException("target");
        this.target = target;
        this.targetMention = Pattern.compile("(?i)(?<![A-Za-z0-9_])" + Pattern.quote(target)
                + "(?![A-Za-z0-9_])");
    }

    public Event accept(String message) {
        String value = message == null ? "" : message.trim();
        String lower = value.toLowerCase(Locale.ROOT);

        if (targetMention.matcher(value).find()
                && (lower.contains("no known alts") || lower.contains("no alts")
                || lower.contains("no other accounts"))) {
            return Event.NO_ALTS;
        }
        if (isStatusHeader(lower)) {
            listStarted = true;
            return Event.LIST_STARTED;
        }
        if (listStarted && USERNAME_LIST.matcher(value).matches()) {
            sawAccountLine = true;
            addAccounts(value);
            return Event.ACCOUNT_LINE;
        }

        int colon = value.indexOf(':');
        if (colon < 0) return Event.UNRELATED;
        String prefix = value.substring(0, colon);
        if (!prefix.toLowerCase(Locale.ROOT).contains("alt") || !targetMention.matcher(prefix).find()) {
            return Event.UNRELATED;
        }
        String suffix = value.substring(colon + 1).trim();
        if (suffix.equalsIgnoreCase("none")) return Event.NO_ALTS;
        if (!USERNAME_LIST.matcher(suffix).matches()) return Event.MALFORMED;
        addAccounts(suffix);
        return Event.INLINE_RESULT;
    }

    public ListResult finishList() {
        if (!listStarted || !sawAccountLine) return new ListResult(ListStatus.NO_RESPONSE, List.of());
        List<String> alts = alts();
        return alts.isEmpty()
                ? new ListResult(ListStatus.NO_ALTS, List.of())
                : new ListResult(ListStatus.FOUND, alts);
    }

    public List<String> alts() {
        return accounts.values().stream().filter(name -> !name.equalsIgnoreCase(target)).toList();
    }

    private void addAccounts(String value) {
        Matcher matcher = USERNAME.matcher(value);
        while (matcher.find()) {
            String name = matcher.group();
            accounts.putIfAbsent(name.toLowerCase(Locale.ROOT), name);
        }
    }

    private static boolean isStatusHeader(String lower) {
        return lower.contains("[online]") && lower.contains("[offline]")
                && lower.contains("[banned]") && lower.contains("[ipbanned]");
    }

    public enum Event { UNRELATED, LIST_STARTED, ACCOUNT_LINE, INLINE_RESULT, NO_ALTS, MALFORMED }

    public enum ListStatus { FOUND, NO_ALTS, NO_RESPONSE }

    public record ListResult(ListStatus status, List<String> alts) {
        public ListResult {
            alts = List.copyOf(alts);
        }
    }
}
