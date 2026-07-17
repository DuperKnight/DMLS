package com.duperknight.client.moderation;

import net.minecraft.text.Text;

import java.util.List;

/** Channels understood by the moderation view, including non-selectable server output. */
public enum ChatChannel {
    GLOBAL("dmls.moderation.channel.global", "", "g", true),
    LOCAL("dmls.moderation.channel.local", "[L]", "local", true),
    TRADE("dmls.moderation.channel.trade", "[T]", "tradec", true),
    RP("dmls.moderation.channel.rp", "[RP]", "rpchat", true),
    STAFF("dmls.moderation.channel.staff", "[SC]", "staffc", true),
    ADMIN("dmls.moderation.channel.admin", "[AC]", "adminc", true),
    SERVER("dmls.moderation.channel.server", "", "", false);

    private static final List<ChatChannel> PREFIXED_PLAYER_CHANNELS = List.of(LOCAL, TRADE, RP, STAFF, ADMIN);
    private static final List<ChatChannel> ADMIN_SECONDARY_CHANNELS = List.of(LOCAL, TRADE, RP, STAFF, ADMIN);
    private static final List<ChatChannel> STAFF_SECONDARY_CHANNELS = List.of(LOCAL, TRADE, RP, STAFF);

    private final String translationKey;
    private final String incomingPrefix;
    private final String sendCommand;
    private final boolean selectable;

    ChatChannel(String translationKey, String incomingPrefix, String sendCommand, boolean selectable) {
        this.translationKey = translationKey;
        this.incomingPrefix = incomingPrefix;
        this.sendCommand = sendCommand;
        this.selectable = selectable;
    }

    public Text displayName() {
        return Text.translatable(translationKey);
    }

    public String sendCommand() {
        return sendCommand;
    }

    public boolean selectable() {
        return selectable;
    }

    /** Staff-only channels always display the sender's actual Minecraft username. */
    public boolean visibleUsernameIsIgn() {
        return this == STAFF || this == ADMIN;
    }

    /** Returns channels that the current rank is allowed to send to or inspect directly. */
    public static List<ChatChannel> selectableFor(boolean admin) {
        return admin ? ADMIN_SECONDARY_CHANNELS : STAFF_SECONDARY_CHANNELS;
    }

    public static ChatChannel classifyPlayerLine(String cleanText) {
        if (cleanText != null) {
            for (ChatChannel channel : PREFIXED_PLAYER_CHANNELS) {
                if (cleanText.startsWith(channel.incomingPrefix)) {
                    return channel;
                }
            }
        }
        return GLOBAL;
    }
}
