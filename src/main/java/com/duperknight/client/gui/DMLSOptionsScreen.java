package com.duperknight.client.gui;

import com.duperknight.client.modules.StaffRank;
import com.duperknight.client.utils.ChatUtils;
import com.duperknight.client.utils.DMLSConfig;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;

/** Persisted mod-wide settings that do not belong to an individual module. */
public final class DMLSOptionsScreen extends DMLSMenuScreen {
    public DMLSOptionsScreen(Screen parent) {
        super(Text.literal("Mod Options"), parent);
    }

    @Override
    protected void init() {
        int controlWidth = scaled(200);
        int x = width / 2 - controlWidth / 2;
        int y = height / 2 - scaled(22);
        addDrawableChild(CyclingButtonWidget.builder((StaffRank rank) -> Text.literal(ChatUtils.stripFormatting(rank.displayName())), DMLSConfig.staffRank())
                .values(StaffRank.values())
                .build(x, y, controlWidth, STANDARD_BUTTON_HEIGHT, Text.literal("Staff Rank"), (button, value) -> DMLSConfig.setStaffRank(value)));
        addDrawableChild(ButtonWidget.builder(ScreenTexts.BACK, button -> close())
                .dimensions(width / 2 - scaled(75), footerButtonY(), scaled(150), STANDARD_BUTTON_HEIGHT).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderMenuBackground(context);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, HEADER_HEIGHT + scaled(16), 0xFFFFFFFF);
        super.render(context, mouseX, mouseY, delta);
    }
}
