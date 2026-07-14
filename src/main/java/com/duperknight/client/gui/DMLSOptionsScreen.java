package com.duperknight.client.gui;

import com.duperknight.client.gui.widgets.DropdownWidget;
import com.duperknight.client.modules.StaffRank;
import com.duperknight.client.utils.DMLSConfig;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;

import java.util.List;

/** Persisted mod-wide settings that do not belong to an individual module. */
public final class DMLSOptionsScreen extends DMLSMenuScreen {
    public DMLSOptionsScreen(Screen parent) {
        super(Text.translatable("dmls.title.options"), parent);
    }

    @Override
    protected void init() {
        int controlWidth = scaled(200);
        int x = width / 2 - controlWidth / 2;
        int y = height / 2 - scaled(36);
        addDrawableChild(ButtonWidget.builder(Text.translatable("dmls.option.allowed_servers"),
                        button -> client.setScreen(new AllowedServersScreen(this)))
                .dimensions(x, y + scaled(30), controlWidth, STANDARD_BUTTON_HEIGHT).build());
        addDrawableChild(ButtonWidget.builder(ScreenTexts.BACK, button -> close())
                .dimensions(width / 2 - scaled(75), footerButtonY(), scaled(150), STANDARD_BUTTON_HEIGHT).build());

        addDropdownChild(DropdownWidget.builder(
                        Text.translatable("dmls.option.staff_rank"),
                        List.of(StaffRank.values()),
                        DMLSConfig.staffRank(),
                        StaffRank::displayName,
                        (dropdown, value) -> DMLSConfig.setStaffRank(value))
                .dimensions(x, y, controlWidth, STANDARD_BUTTON_HEIGHT)
                .maxVisibleRows(5)
                .showOptionLabel(false)
                .build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderMenuBackground(context);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, HEADER_HEIGHT + scaled(16), 0xFFFFFFFF);
        super.render(context, mouseX, mouseY, delta);
    }
}
