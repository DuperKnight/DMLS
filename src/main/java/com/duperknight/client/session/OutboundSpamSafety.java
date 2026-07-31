package com.duperknight.client.session;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

/**
 * Conservative client-side mirror of vanilla's 20-point/200-threshold chat cooldown.
 * Commands are deliberately counted as well for proxy and plugin safety.
 */
public final class OutboundSpamSafety {
    public static final int COST = 20;
    public static final int SAFE_CEILING = 160;
    public static final int MIN_COMMAND_GAP_TICKS = 20;

    private static int debt;
    private static int ticksSinceOutbound = Integer.MAX_VALUE;
    private static boolean registered;

    private OutboundSpamSafety() {
    }

    public static synchronized void register() {
        if (registered) return;
        registered = true;
        ClientSendMessageEvents.CHAT.register(message -> recordOutbound());
        ClientSendMessageEvents.COMMAND.register(command -> recordOutbound());
        ClientTickEvents.END_CLIENT_TICK.register(client -> tick());
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> reset());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
    }

    public static synchronized boolean canDispatch(boolean admin) {
        return admin || (ticksSinceOutbound >= MIN_COMMAND_GAP_TICKS && debt + COST <= SAFE_CEILING);
    }

    public static synchronized int ticksUntilSafe(boolean admin) {
        if (admin) return 0;
        int gap = Math.max(0, MIN_COMMAND_GAP_TICKS - ticksSinceOutbound);
        int debtWait = Math.max(0, debt + COST - SAFE_CEILING);
        return Math.max(gap, debtWait);
    }

    public static synchronized int debt() {
        return debt;
    }

    static synchronized void recordOutbound() {
        debt += COST;
        ticksSinceOutbound = 0;
    }

    static synchronized void tick() {
        if (debt > 0) debt--;
        if (ticksSinceOutbound < Integer.MAX_VALUE) ticksSinceOutbound++;
    }

    static synchronized void reset() {
        debt = 0;
        ticksSinceOutbound = Integer.MAX_VALUE;
    }
}
