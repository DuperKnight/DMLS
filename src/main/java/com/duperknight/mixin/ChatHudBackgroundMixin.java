package com.duperknight.mixin;

import com.duperknight.client.instaban.InstaBanChatHighlight;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.util.math.ColorHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Reuses vanilla chat geometry, opacity, and fading with a result-specific RGB tint. */
@Mixin(ChatHud.class)
public abstract class ChatHudBackgroundMixin {
    @Inject(method = "method_75802", at = @At("HEAD"), cancellable = true)
    private static void dmls$renderResultBackground(
            int bottom, int lineHeight, ChatHud.Backend backend, int width, float backgroundOpacity,
            ChatHudLine.Visible line, int lineIndex, float lineOpacity, CallbackInfo callback
    ) {
        int rgb = InstaBanChatHighlight.backgroundRgb(line.content());
        if (rgb == InstaBanChatHighlight.NONE) return;

        int lineBottom = bottom - lineIndex * lineHeight;
        int lineTop = lineBottom - lineHeight;
        int color = ColorHelper.toAlpha(lineOpacity * backgroundOpacity) | rgb;
        backend.fill(-4, lineTop, width + 8, lineBottom, color);
        callback.cancel();
    }
}
