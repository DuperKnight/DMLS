package com.duperknight.client.gui;

import com.duperknight.client.utils.DMLSConfig;
import com.duperknight.client.utils.ServerGuard;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/** Editable exact-host and explicit wildcard allowlist. */
public final class AllowedServersScreen extends DMLSMenuScreen {
    private EditBox serverField;
    private Component validationMessage = Component.empty();

    public AllowedServersScreen(Screen parent) {
        super(Component.translatable("dmls.title.allowed_servers"), parent);
    }

    @Override
    protected void init() {
        String savedInput = serverField == null ? "" : serverField.getValue();
        List<String> servers = DMLSConfig.allowedServers();
        int formWidth = Math.min(scaled(360), width - scaled(48));
        int formX = (width - formWidth) / 2;
        int addWidth = scaled(64);
        int fieldWidth = formWidth - addWidth - scaled(4);
        int viewportTop = headerHeight() + scaled(72);
        int contentHeight = scaled(44) + servers.size() * scaled(28) + scaled(34);
        configureScrollableContent(viewportTop, contentHeight);

        serverField = addScrollableChild(new EditBox(font, formX, contentY(0), fieldWidth,
                STANDARD_BUTTON_HEIGHT, Component.translatable("dmls.field.server_address")), 0);
        serverField.setMaxLength(260);
        serverField.setValue(savedInput);
        serverField.setSuggestion(savedInput.isEmpty() ? Component.translatable("dmls.placeholder.server_address").getString() : null);
        serverField.setResponder(value -> {
            validationMessage = Component.empty();
            serverField.setSuggestion(value.isEmpty() ? Component.translatable("dmls.placeholder.server_address").getString() : null);
        });
        setInitialFocus(serverField);

        addScrollableChild(Button.builder(Component.translatable("dmls.button.add"), button -> addServer())
                .bounds(formX + fieldWidth + scaled(4), contentY(0), addWidth, STANDARD_BUTTON_HEIGHT).build(), 0);

        for (int index = 0; index < servers.size(); index++) {
            String rule = servers.get(index);
            int offset = scaled(34) + index * scaled(28);
            boolean mandatory = ServerGuard.DEFAULT_ALLOWED_SERVERS.contains(rule);
            Button serverButton = Button.builder(
                            Component.translatable(mandatory
                                    ? "dmls.option.allowed_servers.required"
                                    : "dmls.option.allowed_servers.remove", rule),
                            button -> removeServer(rule))
                    .bounds(formX, contentY(offset), formWidth, STANDARD_BUTTON_HEIGHT).build();
            serverButton.active = !mandatory;
            addScrollableChild(serverButton, offset);
        }

        addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, button -> onClose())
                .bounds(width / 2 - scaled(75), footerButtonY(), scaled(150), STANDARD_BUTTON_HEIGHT).build());
    }

    private void addServer() {
        Optional<String> normalized = ServerGuard.normalizeRule(serverField.getValue());
        if (normalized.isEmpty()) {
            validationMessage = Component.translatable("dmls.validation.server_address");
            return;
        }
        List<String> servers = new ArrayList<>(DMLSConfig.allowedServers());
        if (servers.contains(normalized.get())) {
            validationMessage = Component.translatable("dmls.validation.server_duplicate");
            return;
        }
        servers.add(normalized.get());
        DMLSConfig.setAllowedServers(servers);
        serverField.setValue("");
        rebuildWidgets();
    }

    private void removeServer(String rule) {
        if (ServerGuard.DEFAULT_ALLOWED_SERVERS.contains(rule)) return;
        List<String> servers = new ArrayList<>(DMLSConfig.allowedServers());
        servers.remove(rule);
        DMLSConfig.setAllowedServers(servers);
        rebuildWidgets();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        renderMenuBackground(context);
        context.centeredText(font, title, width / 2, headerHeight() + scaled(16), 0xFFFFFFFF);
        int descriptionWidth = Math.min(scaled(360), width - scaled(48));
        List<FormattedCharSequence> description = font.split(Component.translatable("dmls.option.allowed_servers.description"), descriptionWidth);
        int y = headerHeight() + scaled(32);
        for (FormattedCharSequence line : description) {
            context.centeredText(font, line, width / 2, y, 0xFFCCCCCC);
            y += scaled(11);
        }
        beginContentScissor(context);
        if (!validationMessage.getString().isEmpty()) {
            int messageY = contentY(scaled(38) + DMLSConfig.allowedServers().size() * scaled(28));
            if (isContentVisible(messageY, font.lineHeight)) {
                context.centeredText(font, validationMessage, width / 2, messageY, 0xFFFF5555);
            }
        }
        endContentScissor(context);
        super.extractRenderState(context, mouseX, mouseY, delta);
    }
}
