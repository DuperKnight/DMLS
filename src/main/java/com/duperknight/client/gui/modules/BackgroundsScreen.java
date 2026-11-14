package com.duperknight.client.gui.modules;

import com.duperknight.client.gui.DMLSMenuScreen;
import com.duperknight.client.modules.BackgroundsModule;
import com.duperknight.client.utils.DMLSConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Toggles for the animated DMLS menu backgrounds. */
public final class BackgroundsScreen extends DMLSMenuScreen {
    private final BackgroundsModule module;

    public BackgroundsScreen(Screen parent, BackgroundsModule module) {
        super(module.displayName(), parent);
        this.module = module;
    }

    @Override
    protected void init() {
        configureScrollableContent(module, scaled(60));
        int formWidth = Math.min(scaled(360), width - scaled(48));
        int formX = (width - formWidth) / 2;

        int nodeY = scaled(14);
        int logoY = nodeY + STANDARD_BUTTON_HEIGHT + scaled(6); // NEW

        addScrollableChild(Button.builder(nodeNetworkLabel(), button -> {
                    DMLSConfig.setNodeNetworkBackground(!DMLSConfig.nodeNetworkBackground());
                    button.setMessage(nodeNetworkLabel());
                })
                .bounds(formX, contentY(nodeY), formWidth, STANDARD_BUTTON_HEIGHT).build(), nodeY);

        // NEW
        addScrollableChild(Button.builder(bouncingLogoLabel(), button -> {
                    DMLSConfig.setBouncingLogoBackground(!DMLSConfig.bouncingLogoBackground());
                    button.setMessage(bouncingLogoLabel());
                })
                .bounds(formX, contentY(logoY), formWidth, STANDARD_BUTTON_HEIGHT).build(), logoY);

        addRenderableWidget(Button.builder(Component.translatable("dmls.backgrounds.button.back"),
                        button -> minecraft.gui.setScreen(parent))
                .bounds(width / 2 - scaled(75), footerButtonY(), scaled(150), STANDARD_BUTTON_HEIGHT).build());
    }

    private static Component nodeNetworkLabel() {
        return onOffLabel("dmls.backgrounds.node_network", DMLSConfig.nodeNetworkBackground());
    }

    // NEW
    private static Component bouncingLogoLabel() {
        return onOffLabel("dmls.backgrounds.bouncing_logo", DMLSConfig.bouncingLogoBackground());
    }

    // NEW
    private static Component onOffLabel(String key, boolean enabled) {
        return Component.translatable(key,
                Component.translatable(enabled ? "dmls.option.on" : "dmls.option.off"));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        renderMenuBackground(context);
        renderModuleHeader(context, module);
        beginContentScissor(context);
        endContentScissor(context);
        super.extractRenderState(context, mouseX, mouseY, delta);
    }
}