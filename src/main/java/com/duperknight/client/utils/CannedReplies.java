package com.duperknight.client.utils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The built-in replies for /dmls say. Edit this map to change the available replies.
 */
public final class CannedReplies {
    private static final Map<String, String> REPLIES = new LinkedHashMap<>();

    static {
        REPLIES.put("rules", "Please make sure to read the server rules, you can find our rules here: https://docs.google.com/document/d/1raHKuMt59czFlqpvvBPZWHpoagiPiNOiNy9FKO70Olo/edit?tab=t.0");
        REPLIES.put("appeal", "You can appeal your punishment by opening an appeal in the Stoneworks Discord.");
        REPLIES.put("help", "If you need staff assistance, please open a ticket in the appropriate ticket category in the Stoneworks Discord.");
        REPLIES.put("invite", "Here's an invite to the Stoneworks Discord: https://discord.gg/stoneworks");
        REPLIES.put("suicide", "If you are feeling down or suicidal please contact a local suicide hotline: https://en.wikipedia.org/wiki/List_of_suicide_crisis_lines <3");
        REPLIES.put("brb", "I'm away right now. I'll reply when I'm back.");
        REPLIES.put("busy", "I'm busy right now. If it's urgent, please use /helpop.");
    }

    private CannedReplies() {
    }

    /**
     * Returns the message for the given reply name.
     *
     * @param name the reply name
     * @return the message, or empty if no reply with that name exists
     */
    public static Optional<String> get(String name) {
        return Optional.ofNullable(REPLIES.get(name));
    }

    /**
     * Returns the names of all replies.
     *
     * @return the reply names
     */
    public static List<String> names() {
        return List.copyOf(REPLIES.keySet());
    }
}
