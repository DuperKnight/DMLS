package com.duperknight.client.moderation;

import net.minecraft.text.Text;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/** Shares same-event internal-message suppression between ordered Fabric allow listeners. */
public final class ModerationMessageSuppression {
    private static final Set<Text> MARKED = Collections.newSetFromMap(new IdentityHashMap<>());

    private ModerationMessageSuppression() {
    }

    public static void mark(Text text) {
        MARKED.add(text);
    }

    static boolean consume(Text text) {
        return MARKED.remove(text);
    }
}
