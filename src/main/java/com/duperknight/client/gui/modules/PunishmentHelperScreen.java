package com.duperknight.client.gui.modules;

import com.duperknight.client.gui.DMLSMenuScreen;
import com.duperknight.client.modules.PunishmentHelperModule;
import com.duperknight.client.modules.PunishmentHelperModule.Rule;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;

import java.util.List;

/** Searchable rulebook browser; each rule opens its detail and ban-log composer. */
public final class PunishmentHelperScreen extends DMLSMenuScreen {
    private static final int ROW_HEIGHT_UNSCALED = 22;
    private static final int MAX_ROWS = 40;

    private final PunishmentHelperModule module;
    private TextFieldWidget searchField;
    private String query = "";

    public PunishmentHelperScreen(Screen parent, PunishmentHelperModule module) {
        super(Text.translatable("dmls.module.punish.name"), parent);
        this.module = module;
    }

    @Override
    protected void init() {
        List<Rule> matches = PunishmentHelperModule.search(query);
        int shown = Math.min(matches.size(), MAX_ROWS);
        configureScrollableContent(module, Math.max(scaled(60), shown * scaled(ROW_HEIGHT_UNSCALED) + scaled(12)));

        int formWidth = Math.min(scaled(380), width - scaled(40));
        int formX = (width - formWidth) / 2;

        for (int i = 0; i < shown; i++) {
            Rule rule = matches.get(i);
            int offset = scaled(6) + i * scaled(ROW_HEIGHT_UNSCALED);
            addScrollableChild(ButtonWidget.builder(Text.literal(trim(rule.label(), 62)), button ->
                            client.setScreen(new RuleDetailScreen(this, rule)))
                    .dimensions(formX, contentY(offset), formWidth, STANDARD_BUTTON_HEIGHT).build(), offset);
        }

        searchField = addDrawableChild(new TextFieldWidget(textRenderer, leftPairedButtonX(), footerButtonY(),
                pairedButtonWidth(), STANDARD_BUTTON_HEIGHT, Text.translatable("dmls.field.punish.search")));
        searchField.setMaxLength(64);
        searchField.setText(query);
        searchField.setSuggestion(query.isEmpty() ? Text.translatable("dmls.placeholder.punish.search").getString() : null);
        searchField.setChangedListener(value -> {
            query = value;
            searchField.setSuggestion(value.isEmpty() ? Text.translatable("dmls.placeholder.punish.search").getString() : null);
            clearAndInit();
        });

        addDrawableChild(ButtonWidget.builder(ScreenTexts.BACK, button -> close())
                .dimensions(rightPairedButtonX(), footerButtonY(), pairedButtonWidth(), STANDARD_BUTTON_HEIGHT).build());

        if (searchField.getText().equals(query) && !query.isEmpty()) {
            setFocused(searchField);
        }
    }

    private String trim(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max - 1) + "…";
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderMenuBackground(context);
        renderModuleHeader(context, module);
        beginContentScissor(context);
        if (PunishmentHelperModule.rulebookError().isPresent()) {
            int emptyY = contentY(scaled(14));
            if (isContentVisible(emptyY, textRenderer.fontHeight)) {
                context.drawCenteredTextWithShadow(textRenderer, Text.translatable("dmls.screen.punish.load_failed"),
                        width / 2, emptyY, 0xFFFF5555);
            }
        } else if (PunishmentHelperModule.search(query).isEmpty()) {
            int emptyY = contentY(scaled(14));
            if (isContentVisible(emptyY, textRenderer.fontHeight)) {
                context.drawCenteredTextWithShadow(textRenderer, Text.translatable("dmls.screen.punish.no_match"),
                        width / 2, emptyY, 0xFFAAAAAA);
            }
        }
        endContentScissor(context);
        super.render(context, mouseX, mouseY, delta);
    }
}
