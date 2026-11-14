package com.duperknight.client.instaban;

import com.duperknight.client.utils.LegacyColors;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;

/** Marks result chat rows with a render-only translucent background tint. */
public final class InstaBanChatHighlight {
    public static final int NONE = -1;
    private static final String SAFE_MARKER = "dmls:instaban_background_safe";
    private static final String UNSURE_MARKER = "dmls:instaban_background_unsure";
    private static final String UNSAFE_MARKER = "dmls:instaban_background_unsafe";

    private InstaBanChatHighlight() {
    }

    public static MutableComponent tint(Component original, ChatFormatting resultColor) {
        String marker = switch (resultColor) {
            case GREEN -> SAFE_MARKER;
            case YELLOW -> UNSURE_MARKER;
            case RED -> UNSAFE_MARKER;
            default -> null;
        };
        MutableComponent copy = original.copy();
        return marker == null ? copy : copy.withStyle(style -> style.withInsertion(marker));
    }

    public static int backgroundRgb(FormattedCharSequence text) {
        int[] color = {NONE};
        text.accept((index, style, codePoint) -> {
            color[0] = switch (style.getInsertion()) {
                case SAFE_MARKER -> LegacyColors.rgb(ChatFormatting.GREEN);
                case UNSURE_MARKER -> LegacyColors.rgb(ChatFormatting.YELLOW);
                case UNSAFE_MARKER -> LegacyColors.rgb(ChatFormatting.RED);
                case null, default -> NONE;
            };
            return color[0] == NONE;
        });
        return color[0];
    }
}
