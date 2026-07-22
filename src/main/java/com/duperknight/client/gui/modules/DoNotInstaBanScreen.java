package com.duperknight.client.gui.modules;

import com.duperknight.client.gui.DMLSMenuScreen;
import com.duperknight.client.modules.DoNotInstaBanModule;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** The module deliberately has one setting: enabled or disabled. */
public final class DoNotInstaBanScreen extends DMLSMenuScreen {
    private final DoNotInstaBanModule module;
    private Text saveStatus = Text.empty();

    public DoNotInstaBanScreen(Screen parent, DoNotInstaBanModule module) {
        super(Text.translatable("dmls.module.do_not_insta_ban.name"), parent);
        this.module = module;
    }

    @Override
    protected void init() {
        configureScrollableContent(module, scaled(60));
        int controlWidth = scaled(200);
        int x = width / 2 - controlWidth / 2;
        addScrollableChild(CyclingButtonWidget.builder((Boolean value) -> Text.translatable(
                                value ? "dmls.option.on" : "dmls.option.off")
                        .formatted(value ? Formatting.GREEN : Formatting.RED), module.enabled()).values(true, false)
                .build(x, contentY(0), controlWidth, STANDARD_BUTTON_HEIGHT,
                        Text.translatable("dmls.module.do_not_insta_ban.toggle"), (button, value) -> {
                            if (module.setEnabled(client, value)) {
                                saveStatus = Text.empty();
                            } else {
                                button.setValue(module.enabled());
                                saveStatus = Text.translatable("dmls.validation.config.save_failed");
                            }
                        }), 0);
        addDrawableChild(ButtonWidget.builder(ScreenTexts.BACK, button -> close())
                .dimensions(width / 2 - scaled(75), footerButtonY(), scaled(150), STANDARD_BUTTON_HEIGHT).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderMenuBackground(context);
        renderModuleHeader(context, module);
        beginContentScissor(context);
        int statusY = contentY(scaled(32));
        if (!saveStatus.getString().isEmpty() && isContentVisible(statusY, textRenderer.fontHeight)) {
            context.drawCenteredTextWithShadow(textRenderer, saveStatus, width / 2, statusY, 0xFFFF5555);
        }
        endContentScissor(context);
        super.render(context, mouseX, mouseY, delta);
    }
}
