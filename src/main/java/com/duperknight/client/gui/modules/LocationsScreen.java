package com.duperknight.client.gui.modules;

import com.duperknight.client.gui.DMLSMenuScreen;
import com.duperknight.client.modules.LocationsModule;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;

import java.util.Map;

/** Lists all saved locations with teleport and delete buttons. */
public final class LocationsScreen extends DMLSMenuScreen {
    private static final int ROW_HEIGHT_UNSCALED = 24;

    private final LocationsModule module;
    private Text status = Text.empty();

    public LocationsScreen(Screen parent, LocationsModule module) {
        super(Text.translatable("dmls.module.locations.name"), parent);
        this.module = module;
    }

    @Override
    protected void init() {
        Map<String, LocationsModule.SavedLocation> entries = module.entries();
        configureScrollableContent(module, Math.max(scaled(44), entries.size() * scaled(ROW_HEIGHT_UNSCALED) + scaled(8)));

        int formWidth = Math.min(scaled(360), width - scaled(48));
        int formX = (width - formWidth) / 2;
        int deleteWidth = scaled(20);
        int row = 0;
        for (Map.Entry<String, LocationsModule.SavedLocation> entry : entries.entrySet()) {
            String name = entry.getKey();
            LocationsModule.SavedLocation location = entry.getValue();
            int offset = row * scaled(ROW_HEIGHT_UNSCALED);
            String server = location.isBound() ? location.server() : "unbound";
            registerCommandControl(addScrollableChild(ButtonWidget.builder(
                            Text.literal(name + " §8(" + location.x() + ", " + location.y() + ", " + location.z()
                                    + " · " + location.world() + " @ " + server + ")"),
                            button -> {
                                status = Text.empty();
                                LocationsModule.Outcome outcome = module.teleport(client, name);
                                if (outcome == LocationsModule.Outcome.SENT || outcome == LocationsModule.Outcome.SIMULATED) {
                                    closeToGame();
                                } else {
                                    status = Text.translatable("dmls.validation.locations.teleport_blocked");
                                }
                            })
                    .dimensions(formX, contentY(offset), formWidth - deleteWidth - scaled(4), STANDARD_BUTTON_HEIGHT).build(), offset));
            addScrollableChild(ButtonWidget.builder(Text.literal("✕"), button -> {
                        status = Text.empty();
                        if (module.delete(client, name) == LocationsModule.Outcome.DELETED) {
                            clearAndInit();
                        } else {
                            status = Text.translatable("dmls.validation.locations.action_blocked");
                        }
                    })
                    .dimensions(formX + formWidth - deleteWidth, contentY(offset), deleteWidth, STANDARD_BUTTON_HEIGHT).build(), offset);
            row++;
        }

        addDrawableChild(ButtonWidget.builder(ScreenTexts.BACK, button -> close())
                .dimensions(width / 2 - scaled(75), footerButtonY(), scaled(150), STANDARD_BUTTON_HEIGHT).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderMenuBackground(context);
        renderModuleHeader(context, module);
        beginContentScissor(context);
        if (module.entries().isEmpty()) {
            int emptyY = contentY(scaled(14));
            if (isContentVisible(emptyY, textRenderer.fontHeight)) {
                context.drawCenteredTextWithShadow(textRenderer, Text.translatable("dmls.screen.locations.empty"),
                        width / 2, emptyY, 0xFFAAAAAA);
            }
        }
        endContentScissor(context);
        if (!status.getString().isEmpty()) {
            context.drawCenteredTextWithShadow(textRenderer, status, width / 2,
                    footerButtonY() - scaled(13), 0xFFFF5555);
        }
        super.render(context, mouseX, mouseY, delta);
    }
}
