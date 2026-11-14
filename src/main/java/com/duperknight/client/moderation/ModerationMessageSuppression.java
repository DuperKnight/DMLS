package com.duperknight.client.moderation;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import net.minecraft.network.chat.Component;

/** Shares same-event internal-message suppression between ordered Fabric allow listeners. */
public final class ModerationMessageSuppression {
    private static final Set<Component> MARKED = Collections.newSetFromMap(new IdentityHashMap<>());

    private ModerationMessageSuppression() {
    }

    public static void mark(Component text) {
        MARKED.add(text);
    }

    static boolean consume(Component text) {
        return MARKED.remove(text);
    }
}
