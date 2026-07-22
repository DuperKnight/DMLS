package com.duperknight.client.instaban;

import net.minecraft.text.MutableText;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** Marks result chat rows with a render-only translucent background tint. */
public final class InstaBanChatHighlight {
    public static final int NONE = -1;
    private static final String SAFE_MARKER = "dmls:instaban_background_safe";
    private static final String UNSURE_MARKER = "dmls:instaban_background_unsure";
    private static final String UNSAFE_MARKER = "dmls:instaban_background_unsafe";

    private InstaBanChatHighlight() {
    }

    public static MutableText tint(Text original, Formatting resultColor) {
        String marker = switch (resultColor) {
            case GREEN -> SAFE_MARKER;
            case YELLOW -> UNSURE_MARKER;
            case RED -> UNSAFE_MARKER;
            default -> null;
        };
        MutableText copy = original.copy();
        return marker == null ? copy : copy.styled(style -> style.withInsertion(marker));
    }

    public static int backgroundRgb(OrderedText text) {
        int[] color = {NONE};
        text.accept((index, style, codePoint) -> {
            color[0] = switch (style.getInsertion()) {
                case SAFE_MARKER -> Formatting.GREEN.getColorValue();
                case UNSURE_MARKER -> Formatting.YELLOW.getColorValue();
                case UNSAFE_MARKER -> Formatting.RED.getColorValue();
                case null, default -> NONE;
            };
            return color[0] == NONE;
        });
        return color[0];
    }
}
