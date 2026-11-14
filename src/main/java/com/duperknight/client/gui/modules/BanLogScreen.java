package com.duperknight.client.gui.modules;

import com.duperknight.client.gui.DMLSMenuScreen;
import com.duperknight.client.gui.DangerReviewScreen;
import com.duperknight.client.gui.widgets.DropdownWidget;
import com.duperknight.client.moderation.PunishmentRequest;
import com.duperknight.client.moderation.PunishmentWorkflow;
import com.duperknight.client.moderation.PunishmentWorkflow.PunishmentOption;
import com.duperknight.client.moderation.PunishmentWorkflow.PunishmentOutcome;
import com.duperknight.client.moderation.PunishmentWorkflow.PunishmentPreparation;
import com.duperknight.client.rulebook.RulebookRule;
import com.duperknight.client.session.PendingConfirmation;
import java.util.Arrays;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/** Composes a Stoneworks punishment log and can run a fixed warning, mute, or ban command. */
public final class BanLogScreen extends DMLSMenuScreen {
    private final RulebookRule rule;
    private EditBox ignField;
    private EditBox discordField;
    private EditBox reasonField;
    private DropdownWidget<PunishmentOption> punishmentDropdown;
    private EditBox durationField;
    private EditBox ticketField;
    private EditBox commentsField;
    private EditBox evidenceField;
    private Button punishButton;
    private PendingConfirmation<PunishmentRequest> pendingPunishment;
    private String durationDraft = "";
    private Component status = Component.empty();

    public BanLogScreen(Screen parent, RulebookRule rule) {
        super(Component.translatable("dmls.screen.ban_log.title"), parent);
        this.rule = rule;
    }

    @Override
    protected void init() {
        String savedIgn = text(ignField);
        String savedDiscord = text(discordField);
        int savedReasonCursor = reasonField == null ? 0 : reasonField.getCursorPosition();
        String savedReason = reasonField == null ? defaultReason() : reasonField.getValue();
        PunishmentOption savedPunishment = punishmentDropdown == null
                ? PunishmentOption.BAN : punishmentDropdown.getValue();
        if (durationField != null) durationDraft = durationField.getValue();
        String savedTicket = text(ticketField);
        String savedComments = text(commentsField);
        String savedEvidence = text(evidenceField);

        boolean showDuration = savedPunishment.type().durationRequired();
        configureScrollableContent(headerHeight() + scaled(10), scaled(showDuration ? 384 : 340));
        int formWidth = Math.min(scaled(350), width - scaled(56));
        int formX = (width - formWidth) / 2;

        ignField = field(formX, scaled(12), formWidth, savedIgn, "PlayerName", 32);
        discordField = field(formX, scaled(56), formWidth, savedDiscord, "user#0000 / @user", 64);
        reasonField = field(formX, scaled(100), formWidth, savedReason, "Rule broken and summary", 200);
        reasonField.moveCursorTo(Math.clamp(savedReasonCursor, 0, savedReason.length()), false);
        List<PunishmentOption> punishmentOptions = Arrays.asList(PunishmentOption.values());
        punishmentDropdown = addScrollableDropdownChild(DropdownWidget.builder(
                        Component.translatable("dmls.field.ban_log.type"), punishmentOptions, savedPunishment,
                        PunishmentOption::displayText,
                        (dropdown, value) -> {
                            status = Component.empty();
                            if (durationField != null) durationDraft = durationField.getValue();
                            disarmPunishment();
                            rebuildWidgets();
                        })
                .dimensions(formX, contentY(scaled(144)), formWidth, STANDARD_BUTTON_HEIGHT)
                .maxVisibleRows(3)
                .build(), scaled(144));
        durationField = showDuration
                ? field(formX, scaled(188), formWidth, durationDraft,
                        "4w / 1d / perm (blank = permanent)", 32)
                : null;
        int ticketOffset = showDuration ? 232 : 188;
        ticketField = field(formX, scaled(ticketOffset), formWidth, savedTicket, "#ticket-123 (Grief)", 64);
        commentsField = field(formX, scaled(ticketOffset + 44), formWidth, savedComments, "Notes for other staff", 200);
        evidenceField = field(formX, scaled(ticketOffset + 88), formWidth, savedEvidence, "Links", 200);

        int footerGap = scaled(6);
        int footerMargin = scaled(12);
        int footerWidth = Math.min(scaled(150), (width - footerMargin * 2 - footerGap * 2) / 3);
        int footerX = (width - footerWidth * 3 - footerGap * 2) / 2;
        addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, button -> onClose())
                .bounds(footerX, footerButtonY(), footerWidth, STANDARD_BUTTON_HEIGHT).build());
        addRenderableWidget(Button.builder(Component.translatable("dmls.button.ban_log.copy"), button -> copy())
                .bounds(footerX + footerWidth + footerGap, footerButtonY(), footerWidth,
                        STANDARD_BUTTON_HEIGHT).build());
        punishButton = registerCommandControl(addRenderableWidget(Button.builder(
                        punishLabel(), button -> tryPunish())
                .bounds(footerX + (footerWidth + footerGap) * 2, footerButtonY(), footerWidth,
                        STANDARD_BUTTON_HEIGHT).build()),
                () -> PunishmentWorkflow.canPunish(punishmentDropdown.getValue()));
        updatePunishButton();
    }

    private Component punishLabel() {
        if (punishmentDropdown == null) {
            return Component.translatable("dmls.button.ban_log.ban");
        }
        return Component.translatable(switch (punishmentDropdown.getValue().type()) {
            case WARNING -> "dmls.button.ban_log.warn";
            case MUTE -> "dmls.button.ban_log.mute";
            case BAN -> "dmls.button.ban_log.ban";
            case KICK -> throw new IllegalStateException("The rulebook composer does not offer kicks");
        });
    }

    private String defaultReason() {
        return rule.reason();
    }

    private String selectedType() {
        return PunishmentWorkflow.readablePunishment(
                punishmentDropdown.getValue(), durationField == null ? durationDraft : durationField.getValue());
    }

    private EditBox field(int x, int offset, int fieldWidth, String saved, String placeholder, int maxLength) {
        EditBox widget = addScrollableChild(new EditBox(font, x, contentY(offset), fieldWidth,
                STANDARD_BUTTON_HEIGHT, Component.empty()), offset);
        widget.setMaxLength(maxLength);
        widget.setValue(saved);
        widget.setSuggestion(saved.isEmpty() ? placeholder : null);
        widget.setResponder(value -> {
            widget.setSuggestion(value.isEmpty() ? placeholder : null);
            status = Component.empty();
            disarmPunishment();
        });
        return widget;
    }

    private static String text(EditBox field) {
        return field == null ? "" : field.getValue();
    }

    private void copy() {
        String log = PunishmentWorkflow.banLog(ignField.getValue(), discordField.getValue(), reasonField.getValue(),
                selectedType(), ticketField.getValue(), commentsField.getValue(), evidenceField.getValue());
        minecraft.keyboardHandler.setClipboard(log);
        status = Component.translatable("dmls.screen.ban_log.copied");
    }

    private void tryPunish() {
        PunishmentPreparation preparation = PunishmentWorkflow.preparePunishment(
                ignField.getValue(), punishmentDropdown.getValue(),
                durationField == null ? durationDraft : durationField.getValue(), reasonField.getValue());
        if (!preparation.isValid()) {
            disarmPunishment();
            status = Component.translatable(PunishmentWorkflow.validationTranslationKey(preparation.validation()));
            return;
        }

        pendingPunishment = new PendingConfirmation<>(preparation.request());
        PunishmentRequest request = pendingPunishment.request();
        List<Component> preview = List.of(
                Component.translatable("dmls.field.ban_log.ign").append(Component.literal(" §f" + request.ign())),
                Component.translatable("dmls.field.ban_log.type").append(Component.literal(" §f" + selectedType())),
                Component.translatable("dmls.field.ban_log.reason").append(Component.literal(" §f" + request.reason())),
                Component.literal("§7/" + request.command())
        );
        minecraft.gui.setScreen(new DangerReviewScreen(this, Component.translatable("dmls.screen.ban_log.title"), preview,
                punishLabel(), this::confirmationActive,
                this::confirmPendingPunishment, this::disarmPunishment));
    }

    private boolean confirmationActive() {
        return pendingPunishment != null && pendingPunishment.isActive();
    }

    private boolean confirmPendingPunishment() {
        if (pendingPunishment == null) {
            return false;
        }
        PendingConfirmation.ConsumeResult<PunishmentRequest> confirmation =
                pendingPunishment.consume(pendingPunishment.token());
        if (confirmation.status() != PendingConfirmation.ConsumeStatus.CONFIRMED) {
            disarmPunishment();
            status = Component.translatable("dmls.screen.ban_log.confirm_expired");
            return false;
        }

        PunishmentOutcome outcome = PunishmentWorkflow.punish(minecraft, confirmation.request().orElseThrow());
        disarmPunishment();
        if (outcome == PunishmentOutcome.SENT || outcome == PunishmentOutcome.SIMULATED) {
            return true;
        } else if (outcome == PunishmentOutcome.RANK_BLOCKED) {
            status = Component.translatable("dmls.validation.required_rank");
        } else if (outcome == PunishmentOutcome.SERVER_BLOCKED) {
            status = Component.translatable("dmls.chat.command.not_sent");
        } else if (outcome == PunishmentOutcome.BUSY) {
            status = Component.translatable("dmls.validation.operation.busy");
        }
        return false;
    }

    private void disarmPunishment() {
        if (pendingPunishment != null) {
            pendingPunishment.invalidate();
            pendingPunishment = null;
        }
        updatePunishButton();
    }

    private void updatePunishButton() {
        if (punishButton == null || punishmentDropdown == null) return;
        punishButton.setMessage(punishLabel());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        renderMenuBackground(context);
        beginContentScissor(context);
        int formX = ignField.getX();
        drawLabel(context, "dmls.field.ban_log.ign", formX, scaled(0));
        drawLabel(context, "dmls.field.ban_log.discord", formX, scaled(44));
        drawLabel(context, "dmls.field.ban_log.reason", formX, scaled(88));
        drawLabel(context, "dmls.field.ban_log.type", formX, scaled(132));
        if (durationField != null) drawLabel(context, "dmls.field.ban_log.duration", formX, scaled(176));
        int ticketLabelOffset = durationField == null ? 176 : 220;
        drawLabel(context, "dmls.field.ban_log.ticket", formX, scaled(ticketLabelOffset));
        drawLabel(context, "dmls.field.ban_log.comments", formX, scaled(ticketLabelOffset + 44));
        drawLabel(context, "dmls.field.ban_log.evidence", formX, scaled(ticketLabelOffset + 88));
        endContentScissor(context);
        if (!status.getString().isEmpty()) {
            context.centeredText(font, status, width / 2, footerButtonY() - scaled(12), 0xFFFFFF55);
        }
        super.extractRenderState(context, mouseX, mouseY, delta);
    }

    private void drawLabel(GuiGraphicsExtractor context, String key, int x, int offset) {
        int y = contentY(offset);
        if (isContentVisible(y, font.lineHeight)) {
            context.text(font, Component.translatable(key), x, y, 0xFFCCCCCC);
        }
    }
}
