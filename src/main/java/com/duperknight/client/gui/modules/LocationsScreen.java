package com.duperknight.client.gui.modules;

import com.duperknight.client.gui.DMLSMenuScreen;
import com.duperknight.client.gui.widgets.RemoveButtonWidget;
import com.duperknight.client.modules.LocationsModule;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/** Lists all saved locations with teleport and delete buttons. */
public final class LocationsScreen extends DMLSMenuScreen {
    private static final int ROW_HEIGHT_UNSCALED = 24;

    private final LocationsModule module;
    private Component status = Component.empty();

    public LocationsScreen(Screen parent, LocationsModule module) {
        super(Component.translatable("dmls.module.locations.name"), parent);
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
            registerCommandControl(addScrollableChild(Button.builder(
                            Component.literal(name + " §8(" + location.x() + ", " + location.y() + ", " + location.z()
                                    + " · " + location.world() + " @ " + server + ")"),
                            button -> {
                                status = Component.empty();
                                LocationsModule.Outcome outcome = module.teleport(minecraft, name);
                                if (outcome == LocationsModule.Outcome.SENT || outcome == LocationsModule.Outcome.SIMULATED) {
                                    closeToGame();
                                } else {
                                    status = Component.translatable("dmls.validation.locations.teleport_blocked");
                                }
                            })
                    .bounds(formX, contentY(offset), formWidth - deleteWidth - scaled(4), STANDARD_BUTTON_HEIGHT).build(), offset));
            addScrollableChild(new RemoveButtonWidget(
                    formX + formWidth - deleteWidth, contentY(offset), deleteWidth, STANDARD_BUTTON_HEIGHT,
                    button -> {
                        status = Component.empty();
                        if (module.delete(minecraft, name) == LocationsModule.Outcome.DELETED) {
                            rebuildWidgets();
                        } else {
                            status = Component.translatable("dmls.validation.locations.action_blocked");
                        }
                    }), offset);
            row++;
        }

        addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, button -> onClose())
                .bounds(width / 2 - scaled(75), footerButtonY(), scaled(150), STANDARD_BUTTON_HEIGHT).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        renderMenuBackground(context);
        renderModuleHeader(context, module);
        beginContentScissor(context);
        if (module.entries().isEmpty()) {
            int emptyY = contentY(scaled(14));
            if (isContentVisible(emptyY, font.lineHeight)) {
                context.centeredText(font, Component.translatable("dmls.screen.locations.empty"),
                        width / 2, emptyY, 0xFFAAAAAA);
            }
        }
        endContentScissor(context);
        if (!status.getString().isEmpty()) {
            context.centeredText(font, status, width / 2,
                    footerButtonY() - scaled(13), 0xFFFF5555);
        }
        super.extractRenderState(context, mouseX, mouseY, delta);
    }
}
