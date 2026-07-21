package com.duperknight.client.gui.modules;

import com.duperknight.client.gui.DMLSMenuScreen;
import com.duperknight.client.modules.MiniMeHudModule;
import com.duperknight.client.modules.MiniMeHudPreferences;
import com.duperknight.client.utils.DMLSConfig;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.function.BiFunction;

/** Settings screen for AdMinis shown on the in-game HUD. */
public final class MiniMeHudScreen extends DMLSMenuScreen {
    private final MiniMeHudModule module;
    private Text saveStatus = Text.empty();

    public MiniMeHudScreen(Screen parent, MiniMeHudModule module) {
        super(Text.translatable("dmls.module.mini_me_hud.name"), parent);
        this.module = module;
    }

    @Override
    protected void init() {
        configureScrollableContent(module, scaled(190));
        MiniMeHudPreferences preferences = DMLSConfig.miniMeHudPreferences();
        addToggle(0, "dmls.module.mini_me_hud.dupey", preferences.dupeyHud(), MiniMeHudPreferences::withDupeyHud);
        addToggle(30, "dmls.module.mini_me_hud.siaffy", preferences.siaffyHud(), MiniMeHudPreferences::withSiaffyHud);
        addToggle(60, "dmls.module.mini_me_hud.beany", preferences.beanyHud(), MiniMeHudPreferences::withBeanyHud);
        addToggle(90, "dmls.module.mini_me_hud.morvy", preferences.morvyHud(), MiniMeHudPreferences::withMorvyHud);
        addToggle(120, "dmls.module.mini_me_hud.biggy", preferences.biggyHud(), MiniMeHudPreferences::withBiggyHud);
        addToggle(160, "dmls.module.mini_me_hud.chaos_mode", preferences.chaosMode(), MiniMeHudPreferences::withChaosMode);
        addDrawableChild(ButtonWidget.builder(ScreenTexts.BACK, button -> close())
                .dimensions(width / 2 - scaled(75), footerButtonY(), scaled(150), STANDARD_BUTTON_HEIGHT).build());
    }

    private void addToggle(int offset, String labelKey, boolean initialValue,
                           BiFunction<MiniMeHudPreferences, Boolean, MiniMeHudPreferences> update) {
        int controlWidth = scaled(200);
        int x = width / 2 - controlWidth / 2;
        int scaledOffset = scaled(offset);
        addScrollableChild(CyclingButtonWidget.builder((Boolean value) -> Text.translatable(
                                value ? "dmls.option.on" : "dmls.option.off")
                        .formatted(value ? Formatting.GREEN : Formatting.RED), initialValue).values(true, false)
                .build(x, contentY(scaledOffset), controlWidth, STANDARD_BUTTON_HEIGHT,
                        Text.translatable(labelKey), (button, value) -> {
                            MiniMeHudPreferences previous = DMLSConfig.miniMeHudPreferences();
                            if (!DMLSConfig.setMiniMeHudPreferences(update.apply(previous, value))) {
                                button.setValue(previousValue(previous, labelKey));
                                saveStatus = Text.translatable("dmls.validation.config.save_failed");
                            } else {
                                saveStatus = Text.empty();
                            }
                        }), scaledOffset);
    }

    private boolean previousValue(MiniMeHudPreferences preferences, String labelKey) {
        return switch (labelKey) {
            case "dmls.module.mini_me_hud.dupey" -> preferences.dupeyHud();
            case "dmls.module.mini_me_hud.siaffy" -> preferences.siaffyHud();
            case "dmls.module.mini_me_hud.beany" -> preferences.beanyHud();
            case "dmls.module.mini_me_hud.morvy" -> preferences.morvyHud();
            case "dmls.module.mini_me_hud.biggy" -> preferences.biggyHud();
            default -> preferences.chaosMode();
        };
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderMenuBackground(context);
        renderModuleHeader(context, module);
        beginContentScissor(context);
        int statusY = contentY(scaled(185));
        if (!saveStatus.getString().isEmpty() && isContentVisible(statusY, textRenderer.fontHeight)) {
            context.drawCenteredTextWithShadow(textRenderer, saveStatus, width / 2, statusY, 0xFFFF5555);
        }
        endContentScissor(context);
        super.render(context, mouseX, mouseY, delta);
    }
}
