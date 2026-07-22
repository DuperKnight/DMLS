package com.duperknight.client.instaban;

import net.minecraft.util.Formatting;

/** Pure UI mapping kept separate from transport and chat mutation. */
public record InstaBanPresentation(Formatting color, String statusLabel, String detail, boolean manualReview) {
    public static InstaBanPresentation forResult(InstaBanResult result) {
        InstaBanStatus status = result == null ? InstaBanStatus.UNSURE : result.status();
        InstaBanReason reason = result == null ? InstaBanReason.UNKNOWN : result.reason();
        if (status == null || reason == InstaBanReason.UNKNOWN) status = InstaBanStatus.UNSURE;

        return switch (status) {
            case SAFE -> new InstaBanPresentation(Formatting.GREEN, "safe", "Do Not Insta Ban", false);
            case UNSAFE -> new InstaBanPresentation(Formatting.RED, "unsafe", switch (reason) {
                case REMOVED -> "Removed from Do Not Insta Ban";
                case EXPLICITLY_BANNED -> "Explicitly excluded";
                case NOT_FOUND -> "No Do-Not-Insta-Ban record found";
                default -> "Unsafe";
            }, false);
            case UNSURE -> new InstaBanPresentation(Formatting.YELLOW, "unsure", switch (reason) {
                case VPN -> "VPN evidence; manual review required";
                case INDEX_STALE -> "Do-Not-Insta-Ban index unavailable";
                default -> "Manual review required";
            }, true);
        };
    }
}
