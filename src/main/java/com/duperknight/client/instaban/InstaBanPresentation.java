package com.duperknight.client.instaban;

import net.minecraft.ChatFormatting;

/** Pure UI mapping kept separate from transport and chat mutation. */
public record InstaBanPresentation(ChatFormatting color, String statusLabel, String detail, boolean manualReview) {
    public static InstaBanPresentation forResult(InstaBanResult result) {
        InstaBanStatus status = result == null ? InstaBanStatus.UNSURE : result.status();
        InstaBanReason reason = result == null ? InstaBanReason.UNKNOWN : result.reason();
        if (status == null || reason == InstaBanReason.UNKNOWN) status = InstaBanStatus.UNSURE;

        return switch (status) {
            case SAFE -> new InstaBanPresentation(ChatFormatting.GREEN, "safe", "Do Not Insta Ban", false);
            case UNSAFE -> new InstaBanPresentation(ChatFormatting.RED, "unsafe", switch (reason) {
                case REMOVED -> "Removed from Do Not Insta Ban";
                case EXPLICITLY_BANNED -> "Explicitly excluded";
                case NOT_FOUND -> "No Do-Not-Insta-Ban record found";
                default -> "Unsafe";
            }, false);
            case UNSURE -> new InstaBanPresentation(ChatFormatting.YELLOW, "unsure", switch (reason) {
                case VPN -> "VPN evidence; manual review required";
                case INDEX_STALE -> "Do-Not-Insta-Ban index unavailable";
                default -> "Manual review required";
            }, true);
        };
    }
}
