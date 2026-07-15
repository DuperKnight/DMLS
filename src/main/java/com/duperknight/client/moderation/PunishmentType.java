package com.duperknight.client.moderation;

import com.duperknight.client.modules.StaffRank;
import net.minecraft.text.Text;

public enum PunishmentType {
    BAN("dmls.moderation.punishment.ban", "ban", true, StaffRank.MODERATOR, 0xFF9B2020),
    MUTE("dmls.moderation.punishment.mute", "mute", true, StaffRank.HELPER, 0xFF26733A),
    KICK("dmls.moderation.punishment.kick", "kick", false, StaffRank.HELPER, 0xFF285C9A),
    WARNING("dmls.moderation.punishment.warning", "warn", false, StaffRank.HELPER, 0xFF9A7A20);

    private final String translationKey;
    private final String command;
    private final boolean durationRequired;
    private final StaffRank minimumRank;
    private final int accentColor;

    PunishmentType(String translationKey, String command, boolean durationRequired,
                   StaffRank minimumRank, int accentColor) {
        this.translationKey = translationKey;
        this.command = command;
        this.durationRequired = durationRequired;
        this.minimumRank = minimumRank;
        this.accentColor = accentColor;
    }

    public Text displayName() { return Text.translatable(translationKey); }
    public String command() { return command; }
    public boolean durationRequired() { return durationRequired; }
    public StaffRank minimumRank() { return minimumRank; }
    public int accentColor() { return accentColor; }
}
