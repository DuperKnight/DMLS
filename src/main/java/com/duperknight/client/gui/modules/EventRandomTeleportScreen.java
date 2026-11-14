package com.duperknight.client.gui.modules;

import com.duperknight.client.gui.DMLSMenuScreen;
import com.duperknight.client.modules.EventRandomTeleportModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/** Settings local to the Event Random Teleport module. */
public final class EventRandomTeleportScreen extends DMLSMenuScreen {
    private final EventRandomTeleportModule module;
    private Component lastResult;

    public EventRandomTeleportScreen(Screen parent, EventRandomTeleportModule module) {
        super(Component.translatable("dmls.module.event_random_teleport.name"), parent);
        this.module = module;
        this.lastResult = Component.translatable("dmls.module.event_random_teleport.idle");
    }

    @Override
    protected void init() {
        configureScrollableContent(module, scaled(78));
        int controlWidth = scaled(200);
        int x = width / 2 - controlWidth / 2;

        registerCommandControl(addScrollableChild(Button.builder(
                Component.translatable("dmls.module.event_random_teleport.teleport"), button -> {
            Minecraft client = Minecraft.getInstance();
            EventRandomTeleportModule.TeleportResult result = module.teleport(client);
            lastResult = switch (result.status()) {
                case SENT -> Component.translatable("dmls.chat.event_random_teleport.teleported", result.target());
                case QUEUED -> Component.translatable("dmls.chat.event_random_teleport.queued", result.target());
                case SIMULATED -> Component.translatable("dmls.chat.event_random_teleport.simulated", result.target());
                case NO_PLAYERS -> Component.translatable("dmls.chat.event_random_teleport.no_players");
                case BLOCKED -> Component.translatable("dmls.chat.event_random_teleport.blocked");
            };
        }).bounds(x, contentY(0), controlWidth, STANDARD_BUTTON_HEIGHT).build(), 0));

        addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, button -> onClose())
                .bounds(width / 2 - scaled(75), footerButtonY(), scaled(150), STANDARD_BUTTON_HEIGHT).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        renderMenuBackground(context);
        renderModuleHeader(context, module);
        int statusY = contentY(scaled(32));
        if (isContentVisible(statusY, font.lineHeight)) {
            context.centeredText(font, lastResult, width / 2, statusY, 0xFFAAAAAA);
        }
        super.extractRenderState(context, mouseX, mouseY, delta);
    }
}
