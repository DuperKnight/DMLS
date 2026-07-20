package com.duperknight.client.gui.modules;

import com.duperknight.client.gui.DMLSMenuScreen;
import com.duperknight.client.modules.PunishmentHelperModule;
import com.duperknight.client.rulebook.RulebookRule;
import com.duperknight.client.rulebook.RulebookService;
import com.duperknight.client.rulebook.RulebookSnapshot;
import com.duperknight.client.rulebook.RulebookStatus;
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
    private long observedRevision = -1;

    public PunishmentHelperScreen(Screen parent, PunishmentHelperModule module) {
        super(Text.translatable("dmls.module.punish.name"), parent);
        this.module = module;
    }

    @Override
    protected void init() {
        RulebookSnapshot snapshot = RulebookService.shared().snapshot();
        observedRevision = snapshot.revision();
        if (!snapshot.hasDocument()) {
            configureScrollableContent(module, scaled(64));
            addDrawableChild(ButtonWidget.builder(ScreenTexts.BACK, button -> close())
                    .dimensions(leftPairedButtonX(), footerButtonY(), pairedButtonWidth(), STANDARD_BUTTON_HEIGHT).build());
            ButtonWidget retry = addDrawableChild(ButtonWidget.builder(
                            Text.translatable(snapshot.refreshing() ? "dmls.rulebook.loading" : "dmls.rulebook.retry"),
                            button -> RulebookService.shared().refresh())
                    .dimensions(rightPairedButtonX(), footerButtonY(), pairedButtonWidth(), STANDARD_BUTTON_HEIGHT).build());
            retry.active = !snapshot.refreshing();
            return;
        }

        List<RulebookRule> matches = PunishmentHelperModule.search(query);
        int shown = Math.min(matches.size(), MAX_ROWS);
        configureScrollableContent(module, Math.max(scaled(60), shown * scaled(ROW_HEIGHT_UNSCALED) + scaled(12)));

        int formWidth = Math.min(scaled(380), width - scaled(40));
        int formX = (width - formWidth) / 2;

        for (int i = 0; i < shown; i++) {
            RulebookRule rule = matches.get(i);
            int offset = scaled(6) + i * scaled(ROW_HEIGHT_UNSCALED);
            addScrollableChild(ButtonWidget.builder(Text.literal(trim(rule.label(), 62)), button ->
                            client.setScreen(new RulebookScreen(this, rule.id())))
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

    @Override
    public void tick() {
        super.tick();
        if (RulebookService.shared().snapshot().revision() != observedRevision) clearAndInit();
    }

    private String trim(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max - 1) + "…";
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderMenuBackground(context);
        renderModuleHeader(context, module);
        beginContentScissor(context);
        RulebookSnapshot snapshot = RulebookService.shared().snapshot();
        if (!snapshot.hasDocument()) {
            int emptyY = contentY(scaled(14));
            if (isContentVisible(emptyY, textRenderer.fontHeight)) {
                Text message = snapshot.status() == RulebookStatus.LOADING
                        ? Text.translatable("dmls.rulebook.connecting")
                        : Text.translatable("dmls.rulebook.unavailable");
                context.drawCenteredTextWithShadow(textRenderer, message, width / 2, emptyY,
                        snapshot.status() == RulebookStatus.LOADING ? 0xFFFFFF55 : 0xFFFF5555);
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
