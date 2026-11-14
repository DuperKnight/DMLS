package com.duperknight.client.gui.modules;

import com.mojang.blaze3d.platform.InputConstants;

import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import com.duperknight.client.gui.DMLSMenuScreen;
import com.duperknight.client.gui.DangerReviewScreen;
import com.duperknight.client.modules.WarManagerModule;
import com.duperknight.client.modules.WarManagerModule.CancelStageResult;
import com.duperknight.client.modules.WarManagerModule.HomeView;
import com.duperknight.client.modules.WarManagerModule.StageResult;
import com.duperknight.client.modules.WarManagerModule.WarView;
import com.duperknight.client.war.CompactDurationFormatter;
import com.duperknight.client.war.WarTimestampParser;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/** Configuration and durable-status screen for War Manager. */
public final class WarManagerScreen extends DMLSMenuScreen {
    private static final int SUGGESTION_ROW_HEIGHT = 12;
    private static final int MAX_VISIBLE_SUGGESTIONS = 8;
    private static final int WAR_TITLE_TOP = 248;
    private static final int WAR_LIST_TOP = 264;
    private static final int WAR_ROW_HEIGHT = 40;
    private static final int WAR_ROW_STRIDE = 46;
    private static final int WAR_ROW_TEXT_INSET = 8;
    private static final int WAR_ROW_ACTION_INSET = 7;
    private static final Identifier REMOVE_TEXTURE = Identifier.withDefaultNamespace("pending_invite/reject");
    private static final Identifier REMOVE_HIGHLIGHTED_TEXTURE =
            Identifier.withDefaultNamespace("pending_invite/reject_highlighted");
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm")
            .withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter FULL_TIME =
            DateTimeFormatter.ofPattern("EEE, d MMM yyyy HH:mm:ss z")
                    .withZone(ZoneId.systemDefault());
    private final WarManagerModule module;
    private EditBox attackerField;
    private EditBox defenderField;
    private EditBox timestampField;
    private TimeSlider timeSlider;
    private Button setHomeButton;
    private Button scheduleButton;
    private String selectedWarId = "";
    private List<String> attackerSuggestions = List.of();
    private List<String> defenderSuggestions = List.of();
    private int attackerSuggestionIndex;
    private int defenderSuggestionIndex;
    private long attackerSuggestionRequest;
    private long defenderSuggestionRequest;
    private Component validation = Component.empty();

    public WarManagerScreen(Screen parent, WarManagerModule module) {
        super(Component.translatable("dmls.module.war_manager.name"), parent);
        this.module = module;
    }

    @Override
    protected void init() {
        List<WarView> wars = module.wars();
        long now = System.currentTimeMillis();
        WarView selectedWar = wars.stream()
                .filter(war -> war.id().equals(selectedWarId) && war.scheduledCancelable(now))
                .findFirst().orElse(null);
        if (selectedWar == null) selectedWarId = "";
        attackerSuggestions = List.of();
        defenderSuggestions = List.of();
        configureScrollableContent(module,
                scaled(WAR_LIST_TOP + Math.max(1, wars.size()) * WAR_ROW_STRIDE + 8));
        int formWidth = Math.min(scaled(360), width - scaled(48));
        int formX = width / 2 - formWidth / 2;

        attackerField = addScrollableChild(new EditBox(font, formX, contentY(scaled(14)),
                formWidth, STANDARD_BUTTON_HEIGHT, Component.translatable("dmls.field.war.attacker")), scaled(14));
        attackerField.setMaxLength(64);
        attackerField.setHint(Component.translatable("dmls.field.war.attacker.placeholder"));
        attackerField.setResponder(value -> {
            validation = Component.empty();
            requestClaimSuggestions(true, value);
        });
        setInitialFocus(attackerField);

        defenderField = addScrollableChild(new EditBox(font, formX, contentY(scaled(60)),
                formWidth, STANDARD_BUTTON_HEIGHT, Component.translatable("dmls.field.war.defender")), scaled(60));
        defenderField.setMaxLength(64);
        defenderField.setHint(Component.translatable("dmls.field.war.defender.placeholder"));
        defenderField.setResponder(value -> {
            validation = Component.empty();
            requestClaimSuggestions(false, value);
        });

        timestampField = addScrollableChild(new EditBox(font, formX,
                contentY(scaled(106)), formWidth, STANDARD_BUTTON_HEIGHT,
                Component.translatable("dmls.field.war.timestamp")), scaled(106));
        timestampField.setMaxLength(256);
        timestampField.setHint(Component.translatable("dmls.field.war.timestamp.placeholder"));
        timestampField.setResponder(value -> validation = Component.empty());

        timeSlider = addScrollableChild(new TimeSlider(formX, contentY(scaled(162)), formWidth,
                STANDARD_BUTTON_HEIGHT), scaled(162));
        if (selectedWar != null) {
            attackerField.setValue(selectedWar.attacker());
            defenderField.setValue(selectedWar.defender());
            timestampField.setValue("<t:" + selectedWar.setupMillis() / 1000L + ":F>");
            timeSlider.setMinutes(selectedWar.countdownMinutes());
        }

        setHomeButton = addScrollableChild(Button.builder(homeLabel(), button -> setHome())
                .bounds(formX, contentY(scaled(200)), formWidth, STANDARD_BUTTON_HEIGHT).build(), scaled(200));

        int rowIndex = 0;
        for (WarView war : wars) {
            int rowButtonOffset = scaled(WAR_LIST_TOP + rowIndex * WAR_ROW_STRIDE)
                    + (scaled(WAR_ROW_HEIGHT) - STANDARD_BUTTON_HEIGHT) / 2;
            if (war.status() == com.duperknight.client.war.WarManagerState.Status.PAUSED) {
                int retryWidth = scaled(62);
                int cancelWidth = scaled(62);
                int actionGap = scaled(4);
                int cancelX = formX + formWidth - cancelWidth - scaled(WAR_ROW_ACTION_INSET);
                addScrollableChild(registerCommandControl(Button.builder(
                                Component.translatable("dmls.button.war.retry"),
                                button -> {
                                    if (module.retry(minecraft, war.id())) {
                                        validation = Component.empty();
                                        button.active = false;
                                    } else {
                                        validation = Component.translatable("dmls.validation.war.retry");
                                    }
                                })
                        .bounds(cancelX - actionGap - retryWidth,
                                contentY(rowButtonOffset),
                                retryWidth, STANDARD_BUTTON_HEIGHT).build()), rowButtonOffset);
                addScrollableChild(registerCommandControl(Button.builder(
                                Component.translatable("dmls.button.war.cancel_paused"),
                                button -> {
                                    if (module.cancelPaused(minecraft, war.id())) {
                                        validation = Component.empty();
                                        rebuildWidgets();
                                    } else {
                                        validation = Component.translatable(
                                                "dmls.validation.war.cancel_paused");
                                    }
                                })
                        .bounds(cancelX, contentY(rowButtonOffset),
                                cancelWidth, STANDARD_BUTTON_HEIGHT).build()), rowButtonOffset);
            } else if (war.scheduledCancelable(now)) {
                int cancelWidth = scaled(76);
                addScrollableChild(Button.builder(
                                Component.translatable("dmls.button.war.cancel_scheduled"),
                                button -> {
                                    if (module.cancelScheduled(minecraft, war.id())) {
                                        validation = Component.empty();
                                        rebuildWidgets();
                                    } else {
                                        validation = Component.translatable("dmls.validation.war.cancel_scheduled");
                                    }
                                })
                        .bounds(formX + formWidth - cancelWidth - scaled(WAR_ROW_ACTION_INSET),
                                contentY(rowButtonOffset),
                                cancelWidth, STANDARD_BUTTON_HEIGHT).build(), rowButtonOffset);
            } else if (war.endEarlyAvailable()) {
                int cancelWidth = scaled(76);
                addScrollableChild(registerCommandControl(Button.builder(
                                Component.translatable("dmls.button.war.cancel_early"),
                                button -> cancelEarly(war.id()))
                        .bounds(formX + formWidth - cancelWidth - scaled(WAR_ROW_ACTION_INSET),
                                contentY(rowButtonOffset),
                                cancelWidth, STANDARD_BUTTON_HEIGHT).build()), rowButtonOffset);
            }
            rowIndex++;
        }

        addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, button -> onClose())
                .bounds(leftPairedButtonX(), footerButtonY(), pairedButtonWidth(), STANDARD_BUTTON_HEIGHT).build());
        scheduleButton = registerCommandControl(addRenderableWidget(Button.builder(
                        scheduleLabel(), button -> schedule())
                .bounds(rightPairedButtonX(), footerButtonY(), pairedButtonWidth(),
                        STANDARD_BUTTON_HEIGHT).build()));
    }

    private Component homeLabel() {
        return module.home()
                .<Component>map(home -> Component.translatable("dmls.button.war.home_set", home.coordinates()))
                .orElseGet(() -> Component.translatable("dmls.button.war.set_home"));
    }

    private Component scheduleLabel() {
        return Component.translatable(selectedWarId.isBlank()
                ? "dmls.button.war.schedule" : "dmls.button.war.update");
    }

    private void setHome() {
        WarManagerModule.SaveHomeResult result = module.saveHome(minecraft);
        validation = switch (result) {
            case SAVED -> Component.empty();
            case RANK_BLOCKED -> Component.translatable("dmls.validation.required_rank");
            case SERVER_BLOCKED -> Component.translatable("dmls.validation.server_blocked");
            case IO_ERROR -> Component.translatable("dmls.validation.war.storage");
        };
        if (result == WarManagerModule.SaveHomeResult.SAVED) setHomeButton.setMessage(homeLabel());
    }

    private void schedule() {
        StageResult result = module.stage(minecraft, attackerField.getValue(), defenderField.getValue(),
                timeSlider.minutes(), timestampField.getValue(), selectedWarId);
        if (!result.staged()) {
            validation = Component.translatable(switch (result.status()) {
                case INVALID -> "dmls.validation.war.claims";
                case INVALID_TIMESTAMP -> "dmls.validation.war.timestamp";
                case TIMESTAMP_NOT_FUTURE -> "dmls.validation.war.timestamp_future";
                case BLOCKED -> "dmls.validation.server_blocked";
                case NO_HOME -> "dmls.validation.war.home";
                case WRONG_SERVER -> "dmls.validation.war.home_server";
                case CLAIM_RESERVED -> "dmls.validation.war.reserved";
                case EDIT_UNAVAILABLE -> "dmls.validation.war.edit_unavailable";
                case BUSY -> "dmls.validation.war.busy";
                case STORAGE_ERROR -> "dmls.validation.war.storage";
                case STAGED -> throw new IllegalStateException();
            });
            return;
        }

        WarManagerModule.WarDraft draft = result.draft();
        long projectedSetup = draft.scheduledStartMillis();
        long projectedStart = WarManagerModule.projectedWarStartMillis(
                projectedSetup, draft.countdownMinutes());
        long projectedEnd = projectedStart + WarManagerModule.warDurationMillis();
        HomeView home = module.home().orElseThrow();
        List<Component> preview = new ArrayList<>();
        preview.add(Component.translatable(draft.immediate()
                ? "dmls.review.war.immediate" : "dmls.review.war.queued"));
        preview.add(Component.translatable("dmls.review.war.claims", draft.attacker(), draft.defender()));
        preview.add(Component.translatable("dmls.review.war.countdown",
                CompactDurationFormatter.formatMinutes(draft.countdownMinutes())));
        preview.add(Component.translatable("dmls.review.war.timestamp",
                projectedSetup / 1000L, FULL_TIME.format(Instant.ofEpochMilli(projectedSetup))));
        preview.add(Component.translatable("dmls.review.war.actual_start",
                projectedStart / 1000L, FULL_TIME.format(Instant.ofEpochMilli(projectedStart))));
        preview.add(Component.translatable("dmls.review.war.window",
                CompactDurationFormatter.formatRemaining(WarManagerModule.warDurationMillis()),
                CLOCK.format(Instant.ofEpochMilli(projectedStart)),
                CLOCK.format(Instant.ofEpochMilli(projectedEnd))));
        preview.add(Component.translatable("dmls.review.war.home", home.coordinates()));
        preview.add(Component.literal("/lands admin land " + draft.attacker() + " setflag peaceful false false"));
        preview.add(Component.literal("/lands admin land " + draft.defender() + " setflag peaceful false false"));
        preview.add(Component.literal("/la edit … → /lands nation leave confirm (both claims)"));
        preview.add(Component.literal("/war admin start " + draft.attacker() + " " + draft.defender()
                + " 0 " + CompactDurationFormatter.formatMinutes(draft.countdownMinutes())));
        preview.add(Component.literal("At war start: /gamerule, /broadcastraw, /tp, /mm spawn, /back"));
        preview.add(Component.literal("At war end: purge cleanup and nation restoration"));

        minecraft.gui.setScreen(new DangerReviewScreen(this,
                Component.translatable(draft.editingWarId().isBlank()
                        ? "dmls.review.war.title" : "dmls.review.war.edit_title"), preview,
                Component.translatable(draft.editingWarId().isBlank()
                        ? "dmls.button.war.confirm" : "dmls.button.war.update"),
                () -> module.isPending(result.token()),
                () -> module.confirm(minecraft, result.token()),
                () -> module.invalidatePending(result.token())));
    }

    private void cancelEarly(String warId) {
        CancelStageResult result = module.stageCancel(minecraft, warId);
        if (!result.staged()) {
            validation = Component.translatable(switch (result.status()) {
                case NOT_CANCELLABLE -> "dmls.validation.war.cancel_unavailable";
                case BLOCKED -> "dmls.validation.server_blocked";
                case BUSY -> "dmls.validation.war.busy";
                case STORAGE_ERROR -> "dmls.validation.war.storage";
                case STAGED -> throw new IllegalStateException();
            });
            return;
        }

        WarView war = result.war();
        List<Component> preview = new ArrayList<>();
        preview.add(Component.translatable("dmls.review.war.cancel_claims", war.attacker(), war.defender()));
        preview.add(Component.literal("/lands admin war end " + war.attacker()));
        preview.add(Component.translatable("dmls.review.war.cancel_timer"));
        preview.add(Component.translatable("dmls.review.war.cancel_purge"));
        preview.add(Component.translatable("dmls.review.war.cancel_restore"));
        minecraft.gui.setScreen(new DangerReviewScreen(this,
                Component.translatable("dmls.review.war.cancel_title"), preview,
                Component.translatable("dmls.button.war.cancel_confirm"),
                () -> module.isCancelPending(result.token()),
                () -> module.confirmCancel(minecraft, result.token()),
                () -> module.invalidateCancelPending(result.token())));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        renderMenuBackground(context);
        renderModuleHeader(context, module);
        beginContentScissor(context);
        drawLabel(context, "dmls.field.war.attacker.label", attackerField.getX(), scaled(0));
        drawLabel(context, "dmls.field.war.defender.label", defenderField.getX(), scaled(46));
        drawLabel(context, "dmls.field.war.timestamp.label", timestampField.getX(), scaled(92));
        drawLabel(context, "dmls.field.war.time.label", timeSlider.getX(), scaled(148));
        drawTimestampPreview(context);

        int y = scaled(WAR_TITLE_TOP);
        context.text(font, Component.translatable("dmls.war.active.title"),
                attackerField.getX(), contentY(y), 0xFFFFAA00);
        List<WarView> wars = module.wars();
        if (wars.isEmpty()) {
            context.text(font, Component.translatable("dmls.war.active.none"),
                    attackerField.getX(), contentY(y + scaled(18)), 0xFFAAAAAA);
        } else {
            long now = System.currentTimeMillis();
            int index = 0;
            for (WarView war : wars) {
                int screenRowY = contentY(scaled(WAR_LIST_TOP + index++ * WAR_ROW_STRIDE));
                int rowHeight = scaled(WAR_ROW_HEIGHT);
                boolean hovered = mouseX >= attackerField.getX()
                        && mouseX < attackerField.getX() + attackerField.getWidth()
                        && mouseY >= screenRowY && mouseY < screenRowY + rowHeight;
                boolean selected = war.id().equals(selectedWarId);
                if (selected) {
                    context.fill(attackerField.getX(), screenRowY,
                            attackerField.getX() + attackerField.getWidth(), screenRowY + rowHeight,
                            0xFFFFFFFF);
                    context.fill(attackerField.getX() + 1, screenRowY + 1,
                            attackerField.getX() + attackerField.getWidth() - 1,
                            screenRowY + rowHeight - 1, 0xFF000000);
                } else if (hovered) {
                    context.fill(attackerField.getX(), screenRowY,
                            attackerField.getX() + attackerField.getWidth(), screenRowY + rowHeight,
                            0x40202020);
                }
                String timer = war.cancelledAtMillis() > 0 ? " · ended early"
                        : switch (war.status()) {
                            case SCHEDULED -> " · setup in "
                                    + CompactDurationFormatter.formatRemaining(war.setupMillis() - now);
                            case WAITING_FOR_WAR_START -> " · war starts in "
                                    + CompactDurationFormatter.formatRemaining(war.warStartMillis() - now);
                            case ACTIVE -> " · ends in "
                                    + CompactDurationFormatter.formatRemaining(war.endMillis() - now);
                            default -> "";
                        };
                int lineStep = scaled(11);
                int lineCount = war.error().isBlank() ? 2 : 3;
                int textBlockHeight = (lineCount - 1) * lineStep + font.lineHeight;
                int textY = screenRowY + (rowHeight - textBlockHeight) / 2;
                context.text(font,
                        Component.literal(war.attacker() + " vs " + war.defender()),
                        attackerField.getX() + scaled(WAR_ROW_TEXT_INSET),
                        textY, 0xFFFFFFFF);
                context.text(font,
                        Component.literal(war.status().name().toLowerCase().replace('_', ' ') + timer),
                        attackerField.getX() + scaled(WAR_ROW_TEXT_INSET),
                        textY + lineStep, statusColor(war));
                if (!war.error().isBlank()) {
                    context.text(font, Component.literal(war.error()),
                            attackerField.getX() + scaled(WAR_ROW_TEXT_INSET),
                            textY + lineStep * 2, 0xFFFF5555);
                }
                if (war.dismissible() && hovered) {
                    int removeX = attackerField.getX() + attackerField.getWidth()
                            - 18 - scaled(WAR_ROW_ACTION_INSET);
                    int removeY = screenRowY + (rowHeight - 18) / 2;
                    boolean removeHovered = mouseX >= removeX && mouseX < removeX + 18
                            && mouseY >= removeY && mouseY < removeY + 18;
                    context.blitSprite(RenderPipelines.GUI_TEXTURED,
                            removeHovered ? REMOVE_HIGHLIGHTED_TEXTURE : REMOVE_TEXTURE,
                            removeX, removeY, 18, 18);
                }
            }
        }
        if (!validation.getString().isEmpty()) {
            context.centeredText(font, validation, width / 2,
                    contentY(scaled(230)), 0xFFFF5555);
        }
        endContentScissor(context);
        super.extractRenderState(context, mouseX, mouseY, delta);
        renderClaimSuggestions(context, mouseX, mouseY);
    }

    private void drawLabel(GuiGraphicsExtractor context, String key, int x, int offset) {
        int y = contentY(offset);
        if (isContentVisible(y, font.lineHeight)) {
            context.text(font, Component.translatable(key), x, y, 0xFFCCCCCC);
        }
    }

    private void drawTimestampPreview(GuiGraphicsExtractor context) {
        int y = contentY(scaled(132));
        if (!isContentVisible(y, font.lineHeight)) return;
        if (timestampField.getValue().isBlank()) return;
        var parsed = WarTimestampParser.parseEpochMillis(timestampField.getValue());
        int centerX = timestampField.getX() + timestampField.getWidth() / 2;
        if (parsed.isEmpty()) {
            context.centeredText(font,
                    Component.translatable("dmls.war.timestamp.hint"), centerX, y, 0xFFAAAAAA);
            return;
        }
        long millis = parsed.getAsLong();
        long now = System.currentTimeMillis();
        String relative = millis > now
                ? CompactDurationFormatter.formatRemaining(millis - now) + " from now"
                : CompactDurationFormatter.formatRemaining(now - millis) + " ago";
        context.centeredText(font,
                Component.literal(FULL_TIME.format(Instant.ofEpochMilli(millis)) + " · " + relative),
                centerX, y, millis > now ? 0xFF55FF55 : 0xFFFF5555);
    }

    private void requestClaimSuggestions(boolean attacker, String value) {
        long request = attacker ? ++attackerSuggestionRequest : ++defenderSuggestionRequest;
        String input = value == null ? "" : value.trim();
        if (input.isEmpty() || minecraft == null || minecraft.getConnection() == null
                || !minecraft.getConnection().isAcceptingMessages()) {
            setClaimSuggestions(attacker, request, List.of());
            return;
        }
        String command = "lands info land " + input;
        ClientSuggestionProvider source = minecraft.getConnection().getSuggestionsProvider();
        ParseResults<ClientSuggestionProvider> parse =
                minecraft.getConnection().getCommands().parse(command, source);
        minecraft.getConnection().getCommands()
                .getCompletionSuggestions(parse, command.length())
                .thenAccept(result -> minecraft.execute(() -> {
                    EditBox field = attacker ? attackerField : defenderField;
                    if (field == null || !field.getValue().trim().equals(input)) return;
                    List<String> values = result.getList().stream()
                            .map(Suggestion::getText)
                            .filter(suggestion -> !suggestion.isBlank())
                            .distinct()
                            .limit(MAX_VISIBLE_SUGGESTIONS)
                            .toList();
                    setClaimSuggestions(attacker, request, values);
                }));
    }

    private void setClaimSuggestions(boolean attacker, long request, List<String> values) {
        if (attacker) {
            if (request != attackerSuggestionRequest) return;
            attackerSuggestions = values;
            attackerSuggestionIndex = 0;
        } else {
            if (request != defenderSuggestionRequest) return;
            defenderSuggestions = values;
            defenderSuggestionIndex = 0;
        }
    }

    private void renderClaimSuggestions(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        SuggestionPopup popup = activeSuggestionPopup();
        if (popup == null) return;
        int rows = popup.values().size();
        int height = rows * SUGGESTION_ROW_HEIGHT;
        context.fill(popup.x(), popup.y(), popup.x() + popup.width(),
                popup.y() + height, 0xF0000000);
        for (int i = 0; i < rows; i++) {
            int rowY = popup.y() + i * SUGGESTION_ROW_HEIGHT;
            boolean hovered = mouseX >= popup.x() && mouseX < popup.x() + popup.width()
                    && mouseY >= rowY && mouseY < rowY + SUGGESTION_ROW_HEIGHT;
            if (i == popup.selected() || hovered) {
                context.fill(popup.x(), rowY, popup.x() + popup.width(),
                        rowY + SUGGESTION_ROW_HEIGHT, 0xFF2F2F2F);
            }
            context.text(font,
                    Component.literal(font.plainSubstrByWidth(popup.values().get(i),
                            popup.width() - 6)),
                    popup.x() + 3, rowY + 2, i == popup.selected() ? 0xFFFFFF55 : 0xFFAAAAAA);
        }
    }

    private SuggestionPopup activeSuggestionPopup() {
        if (attackerField != null && attackerField.isFocused() && !attackerSuggestions.isEmpty()) {
            return new SuggestionPopup(true, attackerField.getX(),
                    attackerField.getY() + attackerField.getHeight(), attackerField.getWidth(),
                    attackerSuggestions, attackerSuggestionIndex);
        }
        if (defenderField != null && defenderField.isFocused() && !defenderSuggestions.isEmpty()) {
            return new SuggestionPopup(false, defenderField.getX(),
                    defenderField.getY() + defenderField.getHeight(), defenderField.getWidth(),
                    defenderSuggestions, defenderSuggestionIndex);
        }
        return null;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        SuggestionPopup popup = activeSuggestionPopup();
        if (popup != null && click.x() >= popup.x() && click.x() < popup.x() + popup.width()
                && click.y() >= popup.y()
                && click.y() < popup.y() + popup.values().size() * SUGGESTION_ROW_HEIGHT) {
            int index = (int) (click.y() - popup.y()) / SUGGESTION_ROW_HEIGHT;
            acceptClaimSuggestion(popup.attacker(), index);
            return true;
        }
        if (click.button() == InputConstants.MOUSE_BUTTON_LEFT && dismissCompletedAt(click.x(), click.y())) return true;
        if (super.mouseClicked(click, doubled)) return true;
        if (click.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            WarView war = scheduledWarAt(click.x(), click.y());
            if (war != null) {
                selectWar(war);
                return true;
            }
            if (!selectedWarId.isBlank()) {
                deselectWar();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean keyPressed(KeyEvent input) {
        SuggestionPopup popup = activeSuggestionPopup();
        if (popup != null) {
            if (input.isCycleFocus() || input.key() == InputConstants.KEY_RETURN
                    || input.key() == InputConstants.KEY_NUMPADENTER) {
                acceptClaimSuggestion(popup.attacker(), popup.selected());
                return true;
            }
            if (input.key() == InputConstants.KEY_DOWN) {
                setSuggestionIndex(popup.attacker(),
                        Math.min(popup.values().size() - 1, popup.selected() + 1));
                return true;
            }
            if (input.key() == InputConstants.KEY_UP) {
                setSuggestionIndex(popup.attacker(), Math.max(0, popup.selected() - 1));
                return true;
            }
        }
        return super.keyPressed(input);
    }

    private void setSuggestionIndex(boolean attacker, int index) {
        if (attacker) attackerSuggestionIndex = index;
        else defenderSuggestionIndex = index;
    }

    private void acceptClaimSuggestion(boolean attacker, int index) {
        List<String> values = attacker ? attackerSuggestions : defenderSuggestions;
        if (index < 0 || index >= values.size()) return;
        EditBox field = attacker ? attackerField : defenderField;
        field.setValue(values.get(index));
        field.moveCursorToEnd(false);
        if (attacker) attackerSuggestions = List.of();
        else defenderSuggestions = List.of();
    }

    private boolean dismissCompletedAt(double mouseX, double mouseY) {
        List<WarView> wars = module.wars();
        for (int index = 0; index < wars.size(); index++) {
            WarView war = wars.get(index);
            if (!war.dismissible()) continue;
            int screenRowY = contentY(scaled(WAR_LIST_TOP + index * WAR_ROW_STRIDE));
            int rowHeight = scaled(WAR_ROW_HEIGHT);
            int removeX = attackerField.getX() + attackerField.getWidth()
                    - 18 - scaled(WAR_ROW_ACTION_INSET);
            int removeY = screenRowY + (rowHeight - 18) / 2;
            if (mouseX >= removeX && mouseX < removeX + 18
                    && mouseY >= removeY && mouseY < removeY + 18) {
                if (module.dismissCompleted(minecraft, war.id())) {
                    validation = Component.empty();
                    if (selectedWarId.equals(war.id())) selectedWarId = "";
                    rebuildWidgets();
                } else {
                    validation = Component.translatable("dmls.validation.war.dismiss");
                }
                return true;
            }
        }
        return false;
    }

    private WarView scheduledWarAt(double mouseX, double mouseY) {
        long now = System.currentTimeMillis();
        List<WarView> wars = module.wars();
        for (int index = 0; index < wars.size(); index++) {
            WarView war = wars.get(index);
            int rowY = contentY(scaled(WAR_LIST_TOP + index * WAR_ROW_STRIDE));
            if (mouseX >= attackerField.getX()
                    && mouseX < attackerField.getX() + attackerField.getWidth()
                    && mouseY >= rowY && mouseY < rowY + scaled(WAR_ROW_HEIGHT)
                    && war.scheduledCancelable(now)) return war;
        }
        return null;
    }

    private void selectWar(WarView war) {
        selectedWarId = war.id();
        attackerField.setValue(war.attacker());
        defenderField.setValue(war.defender());
        timestampField.setValue("<t:" + war.setupMillis() / 1000L + ":F>");
        timeSlider.setMinutes(war.countdownMinutes());
        scheduleButton.setMessage(scheduleLabel());
        validation = Component.empty();
    }

    private void deselectWar() {
        selectedWarId = "";
        attackerField.setValue("");
        defenderField.setValue("");
        timestampField.setValue("");
        timeSlider.setMinutes(0);
        scheduleButton.setMessage(scheduleLabel());
        validation = Component.empty();
    }

    private static int statusColor(WarView war) {
        return switch (war.status()) {
            case ACTIVE -> 0xFFFFAA00;
            case WAITING_FOR_WAR_START -> 0xFFFFFF55;
            case CANCELLING -> 0xFFFF5555;
            case COMPLETED -> 0xFF55FF55;
            case PAUSED -> 0xFFFF5555;
            default -> 0xFFAAAAAA;
        };
    }

    private static final class TimeSlider extends AbstractSliderButton {
        private int minutes;

        private TimeSlider(int x, int y, int width, int height) {
            super(x, y, width, height, Component.empty(), 0.0);
            updateMessage();
        }

        int minutes() {
            return minutes;
        }

        void setMinutes(int minutes) {
            this.minutes = Math.clamp(minutes, 0, WarManagerModule.MAX_COUNTDOWN_MINUTES);
            value = this.minutes / (double) WarManagerModule.MAX_COUNTDOWN_MINUTES;
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.translatable("dmls.slider.war.time",
                    CompactDurationFormatter.formatMinutes(minutes)));
        }

        @Override
        protected void applyValue() {
            minutes = (int) Math.round(value * WarManagerModule.MAX_COUNTDOWN_MINUTES);
            value = minutes / (double) WarManagerModule.MAX_COUNTDOWN_MINUTES;
            updateMessage();
        }
    }

    private record SuggestionPopup(boolean attacker, int x, int y, int width,
                                   List<String> values, int selected) {
    }
}
