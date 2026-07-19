package com.duperknight.client.gui;

import com.duperknight.client.utils.DMLSConfig;
import com.duperknight.client.utils.ServerGuard;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Editable exact-host and explicit wildcard allowlist. */
public final class AllowedServersScreen extends DMLSMenuScreen {
    private TextFieldWidget serverField;
    private Text validationMessage = Text.empty();

    public AllowedServersScreen(Screen parent) {
        super(Text.translatable("dmls.title.allowed_servers"), parent);
    }

    @Override
    protected void init() {
        String savedInput = serverField == null ? "" : serverField.getText();
        List<String> servers = DMLSConfig.allowedServers();
        int formWidth = Math.min(scaled(360), width - scaled(48));
        int formX = (width - formWidth) / 2;
        int addWidth = scaled(64);
        int fieldWidth = formWidth - addWidth - scaled(4);
        int viewportTop = HEADER_HEIGHT + scaled(72);
        int contentHeight = scaled(44) + servers.size() * scaled(28) + scaled(34);
        configureScrollableContent(viewportTop, contentHeight);

        serverField = addScrollableChild(new TextFieldWidget(textRenderer, formX, contentY(0), fieldWidth,
                STANDARD_BUTTON_HEIGHT, Text.translatable("dmls.field.server_address")), 0);
        serverField.setMaxLength(260);
        serverField.setText(savedInput);
        serverField.setSuggestion(savedInput.isEmpty() ? Text.translatable("dmls.placeholder.server_address").getString() : null);
        serverField.setChangedListener(value -> {
            validationMessage = Text.empty();
            serverField.setSuggestion(value.isEmpty() ? Text.translatable("dmls.placeholder.server_address").getString() : null);
        });
        setInitialFocus(serverField);

        addScrollableChild(ButtonWidget.builder(Text.translatable("dmls.button.add"), button -> addServer())
                .dimensions(formX + fieldWidth + scaled(4), contentY(0), addWidth, STANDARD_BUTTON_HEIGHT).build(), 0);

        for (int index = 0; index < servers.size(); index++) {
            String rule = servers.get(index);
            int offset = scaled(34) + index * scaled(28);
            boolean mandatory = ServerGuard.DEFAULT_ALLOWED_SERVERS.contains(rule);
            ButtonWidget serverButton = ButtonWidget.builder(
                            Text.translatable(mandatory
                                    ? "dmls.option.allowed_servers.required"
                                    : "dmls.option.allowed_servers.remove", rule),
                            button -> removeServer(rule))
                    .dimensions(formX, contentY(offset), formWidth, STANDARD_BUTTON_HEIGHT).build();
            serverButton.active = !mandatory;
            addScrollableChild(serverButton, offset);
        }

        addDrawableChild(ButtonWidget.builder(ScreenTexts.BACK, button -> close())
                .dimensions(width / 2 - scaled(75), footerButtonY(), scaled(150), STANDARD_BUTTON_HEIGHT).build());
    }

    private void addServer() {
        Optional<String> normalized = ServerGuard.normalizeRule(serverField.getText());
        if (normalized.isEmpty()) {
            validationMessage = Text.translatable("dmls.validation.server_address");
            return;
        }
        List<String> servers = new ArrayList<>(DMLSConfig.allowedServers());
        if (servers.contains(normalized.get())) {
            validationMessage = Text.translatable("dmls.validation.server_duplicate");
            return;
        }
        servers.add(normalized.get());
        DMLSConfig.setAllowedServers(servers);
        serverField.setText("");
        clearAndInit();
    }

    private void removeServer(String rule) {
        if (ServerGuard.DEFAULT_ALLOWED_SERVERS.contains(rule)) return;
        List<String> servers = new ArrayList<>(DMLSConfig.allowedServers());
        servers.remove(rule);
        DMLSConfig.setAllowedServers(servers);
        clearAndInit();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderMenuBackground(context);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, HEADER_HEIGHT + scaled(16), 0xFFFFFFFF);
        int descriptionWidth = Math.min(scaled(360), width - scaled(48));
        List<OrderedText> description = textRenderer.wrapLines(Text.translatable("dmls.option.allowed_servers.description"), descriptionWidth);
        int y = HEADER_HEIGHT + scaled(32);
        for (OrderedText line : description) {
            context.drawCenteredTextWithShadow(textRenderer, line, width / 2, y, 0xFFCCCCCC);
            y += scaled(11);
        }
        beginContentScissor(context);
        if (!validationMessage.getString().isEmpty()) {
            int messageY = contentY(scaled(38) + DMLSConfig.allowedServers().size() * scaled(28));
            if (isContentVisible(messageY, textRenderer.fontHeight)) {
                context.drawCenteredTextWithShadow(textRenderer, validationMessage, width / 2, messageY, 0xFFFF5555);
            }
        }
        endContentScissor(context);
        super.render(context, mouseX, mouseY, delta);
    }
}
