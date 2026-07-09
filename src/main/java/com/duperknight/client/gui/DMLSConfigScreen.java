package com.duperknight.client.gui;

import com.duperknight.client.modules.ChatAlertsModule;
import com.duperknight.client.modules.StaffRank;
import com.duperknight.client.utils.ChatUtils;
import com.duperknight.client.utils.DMLSConfig;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;

public class DMLSConfigScreen extends Screen {
    private final Screen parent;
    private Text wordlistStatus;

    public DMLSConfigScreen(Screen parent) {
        super(Text.literal("DMLS Settings"));
        this.parent = parent;
        this.wordlistStatus = wordlistStatusText();
    }

    @Override
    protected void init() {
        int x = width / 2 - 100;
        int y = height / 2 - 40;

        addDrawableChild(CyclingButtonWidget.builder(
                        (StaffRank rank) -> Text.literal(ChatUtils.stripFormatting(rank.displayName())),
                        DMLSConfig.staffRank())
                .values(StaffRank.values())
                .build(x, y, 200, 20, Text.literal("Staff Rank"), (button, value) -> DMLSConfig.setStaffRank(value)));

        addDrawableChild(CyclingButtonWidget.onOffBuilder(DMLSConfig.alertsEnabled())
                .build(x, y + 25, 200, 20, Text.literal("Chat Alerts"), (button, value) -> DMLSConfig.setAlertsEnabled(value)));

        addDrawableChild(ButtonWidget.builder(Text.literal("Reload Alert Wordlist"), button -> {
            ChatAlertsModule.reloadWordlist();
            wordlistStatus = wordlistStatusText();
        }).dimensions(x, y + 50, 200, 20).build());

        addDrawableChild(ButtonWidget.builder(ScreenTexts.DONE, button -> close())
                .dimensions(x, y + 85, 200, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, height / 2 - 65, 0xFFFFFFFF);
        context.drawCenteredTextWithShadow(textRenderer, wordlistStatus, width / 2, height / 2 + 38, 0xFFAAAAAA);
    }

    @Override
    public void close() {
        client.setScreen(parent);
    }

    private Text wordlistStatusText() {
        int count = ChatAlertsModule.wordCount();
        return Text.literal(count + " alert word" + (count == 1 ? "" : "s") + " loaded (config/dmls-alerts.txt)");
    }
}
