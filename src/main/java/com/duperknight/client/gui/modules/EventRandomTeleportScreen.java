package com.duperknight.client.gui.modules;

import com.duperknight.client.gui.DMLSMenuScreen;
import com.duperknight.client.modules.EventRandomTeleportModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;

/** Settings local to the Event Random Teleport module. */
public final class EventRandomTeleportScreen extends DMLSMenuScreen {
    private final EventRandomTeleportModule module;
    private Text lastResult;

    public EventRandomTeleportScreen(Screen parent, EventRandomTeleportModule module) {
        super(Text.translatable("dmls.module.event_random_teleport.name"), parent);
        this.module = module;
        this.lastResult = Text.translatable("dmls.module.event_random_teleport.idle");
    }

    @Override
    protected void init() {
        configureScrollableContent(module, scaled(78));
        int controlWidth = scaled(200);
        int x = width / 2 - controlWidth / 2;

        registerCommandControl(addScrollableChild(ButtonWidget.builder(
                Text.translatable("dmls.module.event_random_teleport.teleport"), button -> {
            MinecraftClient client = MinecraftClient.getInstance();
            EventRandomTeleportModule.TeleportResult result = module.teleport(client);
            lastResult = switch (result.status()) {
                case SENT -> Text.translatable("dmls.chat.event_random_teleport.teleported", result.target());
                case SIMULATED -> Text.translatable("dmls.chat.event_random_teleport.simulated", result.target());
                case NO_PLAYERS -> Text.translatable("dmls.chat.event_random_teleport.no_players");
                case BLOCKED -> Text.translatable("dmls.chat.event_random_teleport.blocked");
            };
        }).dimensions(x, contentY(0), controlWidth, STANDARD_BUTTON_HEIGHT).build(), 0));

        addDrawableChild(ButtonWidget.builder(ScreenTexts.BACK, button -> close())
                .dimensions(width / 2 - scaled(75), footerButtonY(), scaled(150), STANDARD_BUTTON_HEIGHT).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderMenuBackground(context);
        renderModuleHeader(context, module);
        int statusY = contentY(scaled(32));
        if (isContentVisible(statusY, textRenderer.fontHeight)) {
            context.drawCenteredTextWithShadow(textRenderer, lastResult, width / 2, statusY, 0xFFAAAAAA);
        }
        super.render(context, mouseX, mouseY, delta);
    }
}
