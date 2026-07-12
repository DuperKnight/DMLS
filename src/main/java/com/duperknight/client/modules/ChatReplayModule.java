package com.duperknight.client.modules;

import com.duperknight.client.gui.ChatReplayScreen;
import com.duperknight.client.message.MessageOrigin;
import com.duperknight.client.message.ServerMessage;
import com.duperknight.client.message.ServerMessageRouter;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumSet;
import java.util.List;

/** Keeps the session chat in a scrollable, filterable log. */
public final class ChatReplayModule extends DMLSModule {
    private static final int MAX_ENTRIES = 1000;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    private static final Deque<Entry> ENTRIES = new ArrayDeque<>();

    /** One captured chat line with its arrival time. */
    public record Entry(String time, Text text, String cleanText) {
    }

    public ChatReplayModule() {
        super(StaffRank.HELPER);
    }

    @Override
    public Text displayName() {
        return Text.translatable("dmls.module.chat_replay.name");
    }

    @Override
    public ItemStack icon() {
        return new ItemStack(Items.WRITABLE_BOOK);
    }

    @Override
    public List<Text> description() {
        return List.of(Text.translatable("dmls.module.chat_replay.description"));
    }

    @Override
    public void openScreen(MinecraftClient client, Screen parent) {
        client.setScreen(new ChatReplayScreen(parent, this, ""));
    }

    /** Opens the replay screen with a prefilled filter. */
    public void openScreenWithFilter(MinecraftClient client, String filter) {
        // next tick, otherwise the closing chat screen overrides it
        client.send(() -> client.setScreen(new ChatReplayScreen(null, this, filter)));
    }

    @Override
    public void register() {
        ServerMessageRouter.subscribe(EnumSet.of(MessageOrigin.PLAYER_CHAT, MessageOrigin.SERVER_SYSTEM), ChatReplayModule::capture);
    }

    private static void capture(ServerMessage message) {
        if (message.cleanText().isEmpty()) {
            return;
        }

        ENTRIES.addLast(new Entry(LocalTime.now().format(TIME_FORMAT), message.text(), message.cleanText()));
        while (ENTRIES.size() > MAX_ENTRIES) {
            ENTRIES.removeFirst();
        }
    }

    /** Returns a snapshot of all captured chat lines, oldest first. */
    public static List<Entry> entries() {
        return List.copyOf(ENTRIES);
    }

    /** Returns the number of captured chat lines. */
    public static int entryCount() {
        return ENTRIES.size();
    }
}
