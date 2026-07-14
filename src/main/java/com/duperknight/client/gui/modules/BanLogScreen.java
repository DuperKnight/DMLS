package com.duperknight.client.gui.modules;

import com.duperknight.client.gui.DMLSMenuScreen;
import com.duperknight.client.gui.DangerReviewScreen;
import com.duperknight.client.modules.PunishmentHelperModule;
import com.duperknight.client.modules.PunishmentHelperModule.BanOutcome;
import com.duperknight.client.modules.PunishmentHelperModule.BanPreparation;
import com.duperknight.client.modules.PunishmentHelperModule.Rule;
import com.duperknight.client.session.PendingConfirmation;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;

import java.util.List;

/** Composes a ban log in the Stoneworks format, copies it, and can run the ban command. */
public final class BanLogScreen extends DMLSMenuScreen {
    private final Rule rule;
    private TextFieldWidget ignField;
    private TextFieldWidget discordField;
    private TextFieldWidget reasonField;
    private TextFieldWidget typeField;
    private TextFieldWidget durationField;
    private TextFieldWidget ticketField;
    private TextFieldWidget commentsField;
    private TextFieldWidget evidenceField;
    private ButtonWidget banButton;
    private PendingConfirmation<PunishmentHelperModule.BanRequest> pendingBan;
    private Text status = Text.empty();

    public BanLogScreen(Screen parent, Rule rule) {
        super(Text.translatable("dmls.screen.ban_log.title"), parent);
        this.rule = rule;
    }

    @Override
    protected void init() {
        String savedIgn = text(ignField);
        String savedDiscord = text(discordField);
        String savedReason = reasonField == null ? defaultReason() : reasonField.getText();
        String savedType = typeField == null ? defaultType() : typeField.getText();
        String savedDuration = text(durationField);
        String savedTicket = text(ticketField);
        String savedComments = text(commentsField);
        String savedEvidence = text(evidenceField);

        boolean canBan = PunishmentHelperModule.canBan();
        configureScrollableContent(HEADER_HEIGHT + scaled(10), scaled(canBan ? 420 : 384));
        int formWidth = Math.min(scaled(380), width - scaled(40));
        int formX = (width - formWidth) / 2;

        ignField = field(formX, scaled(12), formWidth, savedIgn, "PlayerName", 32);
        discordField = field(formX, scaled(56), formWidth, savedDiscord, "user#0000 / @user", 64);
        reasonField = field(formX, scaled(100), formWidth, savedReason, "Rule broken and summary", 200);
        typeField = field(formX, scaled(144), formWidth, savedType, "Warning / 7 day ban / permanent", 64);
        durationField = field(formX, scaled(188), formWidth, savedDuration, "7d / 2w / perm (for the ban command)", 32);
        ticketField = field(formX, scaled(232), formWidth, savedTicket, "#ticket-123 (Grief)", 64);
        commentsField = field(formX, scaled(276), formWidth, savedComments, "Notes for other staff", 200);
        evidenceField = field(formX, scaled(320), formWidth, savedEvidence, "Links / screenshots", 200);

        if (canBan) {
            banButton = addScrollableChild(ButtonWidget.builder(banLabel(), button -> tryBan())
                    .dimensions(formX, contentY(scaled(366)), formWidth, STANDARD_BUTTON_HEIGHT).build(), scaled(366));
        }

        addDrawableChild(ButtonWidget.builder(ScreenTexts.BACK, button -> close())
                .dimensions(leftPairedButtonX(), footerButtonY(), pairedButtonWidth(), STANDARD_BUTTON_HEIGHT).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("dmls.button.ban_log.copy"), button -> copy())
                .dimensions(rightPairedButtonX(), footerButtonY(), pairedButtonWidth(), STANDARD_BUTTON_HEIGHT).build());
    }

    private Text banLabel() {
        return Text.translatable("dmls.button.ban_log.ban");
    }

    private String defaultReason() {
        return rule.id() + " " + rule.title();
    }

    private String defaultType() {
        return rule.punishment();
    }

    private TextFieldWidget field(int x, int offset, int fieldWidth, String saved, String placeholder, int maxLength) {
        TextFieldWidget widget = addScrollableChild(new TextFieldWidget(textRenderer, x, contentY(offset), fieldWidth,
                STANDARD_BUTTON_HEIGHT, Text.empty()), offset);
        widget.setMaxLength(maxLength);
        widget.setText(saved);
        widget.setSuggestion(saved.isEmpty() ? placeholder : null);
        widget.setChangedListener(value -> {
            widget.setSuggestion(value.isEmpty() ? placeholder : null);
            disarmBan();
        });
        return widget;
    }

    private static String text(TextFieldWidget field) {
        return field == null ? "" : field.getText();
    }

    private void copy() {
        String log = PunishmentHelperModule.banLog(ignField.getText(), discordField.getText(), reasonField.getText(),
                typeField.getText(), ticketField.getText(), commentsField.getText(), evidenceField.getText());
        client.keyboard.setClipboard(log);
        status = Text.translatable("dmls.screen.ban_log.copied");
    }

    private void tryBan() {
        BanPreparation preparation = PunishmentHelperModule.prepareBan(
                ignField.getText(), durationField.getText(), reasonField.getText());
        if (!preparation.isValid()) {
            disarmBan();
            status = Text.translatable(PunishmentHelperModule.validationTranslationKey(preparation.validation()));
            return;
        }

        pendingBan = new PendingConfirmation<>(preparation.request());
        PunishmentHelperModule.BanRequest request = pendingBan.request();
        List<Text> preview = List.of(
                Text.translatable("dmls.field.ban_log.ign").append(Text.literal(" §f" + request.ign())),
                Text.translatable("dmls.field.ban_log.duration").append(Text.literal(" §f" + request.duration())),
                Text.translatable("dmls.field.ban_log.reason").append(Text.literal(" §f" + request.reason())),
                Text.literal("§7/" + request.command())
        );
        client.setScreen(new DangerReviewScreen(this, Text.translatable("dmls.screen.ban_log.title"), preview,
                Text.translatable("dmls.button.ban_log.ban"), this::confirmationActive,
                this::confirmPendingBan, this::disarmBan));
    }

    private boolean confirmationActive() {
        return pendingBan != null && pendingBan.isActive();
    }

    private boolean confirmPendingBan() {
        if (pendingBan == null) {
            return false;
        }
        PendingConfirmation.ConsumeResult<PunishmentHelperModule.BanRequest> confirmation =
                pendingBan.consume(pendingBan.token());
        if (confirmation.status() != PendingConfirmation.ConsumeStatus.CONFIRMED) {
            disarmBan();
            status = Text.translatable("dmls.screen.ban_log.confirm_expired");
            return false;
        }

        BanOutcome outcome = PunishmentHelperModule.ban(client, confirmation.request().orElseThrow());
        disarmBan();
        if (outcome == BanOutcome.SENT || outcome == BanOutcome.SIMULATED) {
            return true;
        } else if (outcome == BanOutcome.RANK_BLOCKED) {
            status = Text.translatable("dmls.validation.required_rank");
        } else if (outcome == BanOutcome.SERVER_BLOCKED) {
            status = Text.translatable("dmls.chat.command.not_sent");
        } else if (outcome == BanOutcome.BUSY) {
            status = Text.translatable("dmls.validation.operation.busy");
        }
        return false;
    }

    private void disarmBan() {
        if (pendingBan != null) {
            pendingBan.invalidate();
            pendingBan = null;
            if (banButton != null) {
                banButton.setMessage(banLabel());
            }
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderMenuBackground(context);
        int formX = ignField.getX();
        drawLabel(context, "dmls.field.ban_log.ign", formX, scaled(0));
        drawLabel(context, "dmls.field.ban_log.discord", formX, scaled(44));
        drawLabel(context, "dmls.field.ban_log.reason", formX, scaled(88));
        drawLabel(context, "dmls.field.ban_log.type", formX, scaled(132));
        drawLabel(context, "dmls.field.ban_log.duration", formX, scaled(176));
        drawLabel(context, "dmls.field.ban_log.ticket", formX, scaled(220));
        drawLabel(context, "dmls.field.ban_log.comments", formX, scaled(264));
        drawLabel(context, "dmls.field.ban_log.evidence", formX, scaled(308));
        if (!status.getString().isEmpty()) {
            context.drawCenteredTextWithShadow(textRenderer, status, width / 2, footerButtonY() - scaled(12), 0xFFFFFF55);
        }
        super.render(context, mouseX, mouseY, delta);
    }

    private void drawLabel(DrawContext context, String key, int x, int offset) {
        int y = contentY(offset);
        if (isContentVisible(y, textRenderer.fontHeight)) {
            context.drawTextWithShadow(textRenderer, Text.translatable(key), x, y, 0xFFCCCCCC);
        }
    }
}
