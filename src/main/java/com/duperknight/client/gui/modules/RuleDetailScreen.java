package com.duperknight.client.gui.modules;

import com.duperknight.client.gui.DMLSMenuScreen;
import com.duperknight.client.modules.PunishmentHelperModule.Rule;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/** Shows a single rule's text and ban ladder, with a button to compose a ban log for it. */
public final class RuleDetailScreen extends DMLSMenuScreen {
    private final Rule rule;
    private List<OrderedText> lines = List.of();
    private int contentWidth;

    public RuleDetailScreen(Screen parent, Rule rule) {
        super(Text.literal(rule.id()), parent);
        this.rule = rule;
    }

    @Override
    protected void init() {
        contentWidth = Math.min(scaled(380), width - scaled(40));
        List<OrderedText> built = new ArrayList<>();
        built.addAll(textRenderer.wrapLines(Text.literal("§6§l" + rule.id() + " §r§7(" + rule.section() + ")"), contentWidth));
        built.add(OrderedText.EMPTY);
        built.addAll(textRenderer.wrapLines(Text.literal("§f" + rule.title()), contentWidth));
        built.add(OrderedText.EMPTY);
        built.addAll(textRenderer.wrapLines(Text.translatable("dmls.screen.punish.ladder").styled(s -> s), contentWidth));
        built.addAll(textRenderer.wrapLines(Text.literal("§e" + rule.punishment()), contentWidth));
        lines = built;

        configureScrollableContent(HEADER_HEIGHT + scaled(10), Math.max(scaled(60), lines.size() * (textRenderer.fontHeight + 2) + scaled(8)));

        addDrawableChild(ButtonWidget.builder(ScreenTexts.BACK, button -> close())
                .dimensions(leftPairedButtonX(), footerButtonY(), pairedButtonWidth(), STANDARD_BUTTON_HEIGHT).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("dmls.button.punish.make_log"), button ->
                        client.setScreen(new BanLogScreen(this, rule)))
                .dimensions(rightPairedButtonX(), footerButtonY(), pairedButtonWidth(), STANDARD_BUTTON_HEIGHT).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderMenuBackground(context);
        beginContentScissor(context);
        int x = (width - contentWidth) / 2;
        int lineHeight = textRenderer.fontHeight + 2;
        for (int i = 0; i < lines.size(); i++) {
            int y = contentY(i * lineHeight);
            if (isContentVisible(y, textRenderer.fontHeight)) {
                context.drawTextWithShadow(textRenderer, lines.get(i), x, y, 0xFFFFFFFF);
            }
        }
        endContentScissor(context);
        super.render(context, mouseX, mouseY, delta);
    }
}
