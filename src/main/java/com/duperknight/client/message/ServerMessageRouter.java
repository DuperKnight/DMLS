package com.duperknight.client.message;

import com.duperknight.DMLS;
import com.duperknight.client.utils.ChatUtils;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/** Small, client-thread message router. DMLS-local messages bypass these Fabric receive events. */
public final class ServerMessageRouter {
    private static final long CROSS_EVENT_DUPLICATE_NANOS = 250_000_000L;
    private static final List<RoutedSubscription> SUBSCRIPTIONS = new ArrayList<>();
    private static String previousText = "";
    private static MessageOrigin previousOrigin;
    private static long previousAt;
    private static boolean registered;

    private ServerMessageRouter() {
    }

    public static void register() {
        if (registered) return;
        registered = true;
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> route(message,
                overlay ? MessageOrigin.OVERLAY : MessageOrigin.SERVER_SYSTEM, overlay));
        ClientReceiveMessageEvents.CHAT.register((message, signed, sender, params, timestamp) ->
                route(message, MessageOrigin.PLAYER_CHAT, false));
    }

    public static SubscriptionHandle subscribe(EnumSet<MessageOrigin> origins, Consumer<ServerMessage> consumer) {
        Objects.requireNonNull(origins, "origins");
        if (origins.isEmpty()) throw new IllegalArgumentException("origins");
        RoutedSubscription subscription = new RoutedSubscription(
                EnumSet.copyOf(origins), Objects.requireNonNull(consumer));
        SUBSCRIPTIONS.add(subscription);
        return () -> SUBSCRIPTIONS.remove(subscription);
    }

    static boolean isCrossEventDuplicate(String text, MessageOrigin origin, long now) {
        boolean duplicate = origin != previousOrigin && text.equals(previousText) && now - previousAt <= CROSS_EVENT_DUPLICATE_NANOS;
        previousText = text;
        previousOrigin = origin;
        previousAt = now;
        return duplicate;
    }

    static void resetDuplicateStateForTests() {
        previousText = "";
        previousOrigin = null;
        previousAt = 0;
    }

    private static void route(Text text, MessageOrigin origin, boolean overlay) {
        String clean = ChatUtils.cleanLine(text.getString());
        long now = System.nanoTime();
        if (isCrossEventDuplicate(clean, origin, now)) return;
        ServerMessage message = new ServerMessage(text, clean, origin, overlay, now);
        route(message);
    }

    /** Routes a pre-built message and isolates each subscriber from failures in the others. */
    static void route(ServerMessage message) {
        for (RoutedSubscription subscription : List.copyOf(SUBSCRIPTIONS)) {
            if (!subscription.origins.contains(message.origin())) continue;
            try {
                subscription.consumer.accept(message);
            } catch (RuntimeException exception) {
                DMLS.LOGGER.error("A server-message subscriber failed; continuing routing", exception);
            }
        }
    }

    static void clearSubscriptionsForTests() {
        SUBSCRIPTIONS.clear();
    }

    @FunctionalInterface
    public interface SubscriptionHandle extends AutoCloseable {
        void unsubscribe();

        @Override
        default void close() {
            unsubscribe();
        }
    }

    private record RoutedSubscription(EnumSet<MessageOrigin> origins, Consumer<ServerMessage> consumer) {
    }
}
