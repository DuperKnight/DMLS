package com.duperknight.client.moderation;

import com.duperknight.DMLS;
import com.duperknight.client.gui.DMLSMenuScreen;
import com.duperknight.client.gui.widgets.DropdownWidget;
import com.duperknight.client.modules.ChatAlertsModule;
import com.duperknight.client.modules.StaffRank;
import com.duperknight.client.session.CommandDispatch;
import com.duperknight.client.utils.ClientUtils;
import com.duperknight.client.utils.DMLSConfig;
import com.duperknight.client.utils.ServerGuard;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.gui.cursor.StandardCursors;
import net.minecraft.client.gui.screen.ChatInputSuggestor;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;

/** Standalone live moderation workspace; intentionally not a regular DMLS module screen. */
public final class ModerationScreen extends Screen {
    private static final Tooltip STONEWORKS_CONNECTION_TOOLTIP = Tooltip.of(
            Text.translatable("dmls.tooltip.connect_stoneworks"));
    private static final int MARGIN = 10;
    private static final int GAP = 8;
    private static final int INPUT_HEIGHT = 20;
    private static final int ROW_HEIGHT = 20;
    private static final int SETTINGS_SECTION_HEIGHT = 25;
    private static final int CONTEXT_ROW_HEIGHT = 23;
    private static final int LOG_CONTENT_TOP_OFFSET = 20;
    private static final int PANEL_BACKGROUND = 0xD0000000;
    private static final int PANEL_BORDER = 0xFF888888;
    private static final int HOVER_BACKGROUND = 0x80383838;
    private static final int ALERT_BACKGROUND = 0x805F4710;
    private static final int AUTOMATIC_ALERT_BACKGROUND = 0xA0701515;
    private static final int AUTOMATIC_ALERT_HOVER_BACKGROUND = 0xB0882525;
    private static final int MENU_WIDTH = 118;
    private static final int SUBMENU_WIDTH = 90;
    private static final int SCROLLBAR_INSET = 3;
    private static final int STATUS_HORIZONTAL_PADDING = 12;
    private static final int STATUS_VERTICAL_PADDING = 6;
    private static final long SUBMENU_HOVER_DELAY_NANOS = 250_000_000L;
    private static final float PUNISHMENT_SUMMARY_SCALE = 0.8F;
    private static final int MINI_ME_TEXTURE_SIZE = 800;
    private static final float MINI_ME_SPRING_STRENGTH = 115.0F;
    private static final float MINI_ME_SPRING_DAMPING = 7.0F;
    private static final long MINI_ME_SPAM_WINDOW_NANOS = 350_000_000L;
    private static final SoundEvent MINI_ME_SQUISH_SOUND = SoundEvent.of(
            Identifier.of(DMLS.MOD_ID.toLowerCase(), "mini_me.squish"));
    private static final Identifier NOTIFICATION_TEXTURE = Identifier.of(
            DMLS.MOD_ID.toLowerCase(), "textures/gui/icon/notification.png");
    private static final List<MiniMe> MINI_MES = List.of(
            new MiniMe("Dupey", Identifier.of(DMLS.MOD_ID.toLowerCase(), "textures/gui/mini_me/dupey/dupey.png")),
            new MiniMe("Siaffy", Identifier.of(DMLS.MOD_ID.toLowerCase(), "textures/gui/mini_me/siaffy/siaffy.png")),
            new MiniMe("Beany", Identifier.of(DMLS.MOD_ID.toLowerCase(), "textures/gui/mini_me/beany/beany.png")),
            new MiniMe("Morvy", Identifier.of(DMLS.MOD_ID.toLowerCase(), "textures/gui/mini_me/morvy/morvy.png")),
            new MiniMe("Biggy", Identifier.of(DMLS.MOD_ID.toLowerCase(), "textures/gui/mini_me/biggy/biggy.png"))
    );
    private final Screen parent;
    private final PunishmentLogSource punishmentLogSource;
    private final PaneState globalPane = new PaneState();
    private final PaneState channelPane = new PaneState();
    private final Map<String, SkinTextures> punishmentSkinCache = new HashMap<>();
    private final Set<String> punishmentSkinLoading = new HashSet<>();
    private final List<MessageHitbox> messageHitboxes = new ArrayList<>();
    private final List<PunishmentHitbox> punishmentHitboxes = new ArrayList<>();
    private final ChannelMentionTracker channelMentions = ModerationChatService.channelMentions();

    private TextFieldWidget globalInput;
    private TextFieldWidget channelInput;
    private ChatInputSuggestor globalSuggestor;
    private ChatInputSuggestor channelSuggestor;
    private DropdownWidget<ChatChannel> channelDropdown;
    private ButtonWidget globalSend;
    private ButtonWidget channelSend;
    private ButtonWidget settingsTab;
    private ButtonWidget miniMeTab;
    private TextFieldWidget punishmentReason;
    private TextFieldWidget punishmentDuration;
    private ButtonWidget punishmentConfirm;
    private ButtonWidget punishmentCancel;

    private ChatChannel selectedChannel = ChatChannel.STAFF;
    private RightTab selectedRightTab = RightTab.SETTINGS;
    private ModerationPreferences preferences;
    private long lastRevision = -1;
    private ContextMenu contextMenu;
    private PunishmentType modalType;
    private String modalIgn = "";
    private String status = "";
    private int statusTicks;
    private PaneState draggingPane;
    private int logScroll;
    private int logMaxScroll;
    private boolean draggingLogScrollbar;
    private int settingsScroll;
    private int settingsMaxScroll;
    private boolean draggingSettingsScrollbar;
    private boolean adminAccess;
    private int selectedMiniMeIndex;
    private float miniMeSquish;
    private float miniMeSquishVelocity;
    private float miniMeSpamEnergy;
    private long miniMeAnimationNanos = System.nanoTime();
    private long lastMiniMeClickNanos;
    private boolean serverAllowedPreviously;
    private Rect miniMeHitbox;
    private SoundInstance activeMiniMeSquishSound;
    private long lastAutomaticAlertRevision;

    public ModerationScreen(Screen parent) {
        this(parent, PunishmentLogService.shared());
    }

    public ModerationScreen(Screen parent, PunishmentLogSource punishmentLogSource) {
        super(Text.translatable("dmls.moderation.title"));
        this.parent = parent;
        this.punishmentLogSource = punishmentLogSource == null ? PunishmentLogSource.EMPTY : punishmentLogSource;
        if (this.punishmentLogSource == PunishmentLogService.shared()) {
            PunishmentLogService.shared().onScreenOpened();
        }
        this.preferences = DMLSConfig.moderationPreferences();
        this.lastAutomaticAlertRevision = ModerationChatService.automaticAlertRevision();
    }

    @Override
    protected void init() {
        String savedGlobal = globalInput == null ? "" : globalInput.getText();
        String savedChannel = channelInput == null ? "" : channelInput.getText();
        String savedReason = punishmentReason == null ? "" : punishmentReason.getText();
        String savedDuration = punishmentDuration == null ? "" : punishmentDuration.getText();
        Layout layout = layout();
        adminAccess = isAdmin();
        List<ChatChannel> selectableChannels = selectableChannels();
        channelMentions.retainAvailableChannels(selectableChannels);
        if (!selectableChannels.contains(selectedChannel)) selectChannel(ChatChannel.STAFF);

        int sendWidth = Math.min(72, Math.max(50, layout.leftWidth() / 7));
        globalInput = addDrawableChild(new TextFieldWidget(textRenderer, layout.leftX(), layout.globalInputY(),
                layout.leftWidth() - sendWidth - 4, INPUT_HEIGHT, Text.translatable("dmls.moderation.global_input")));
        globalInput.setMaxLength(240);
        globalInput.setText(savedGlobal);
        globalSuggestor = createSuggestor(globalInput);
        globalInput.setChangedListener(this::onGlobalInputChanged);
        onGlobalInputChanged(savedGlobal);
        globalSend = addDrawableChild(ButtonWidget.builder(Text.translatable("dmls.moderation.send"), ignored -> sendGlobal())
                .dimensions(layout.leftX() + layout.leftWidth() - sendWidth, layout.globalInputY(), sendWidth, INPUT_HEIGHT).build());

        int dropdownWidth = Math.min(92, Math.max(66, layout.leftWidth() / 5));
        channelDropdown = addDrawableChild(DropdownWidget.builder(Text.translatable("dmls.moderation.channel"),
                        selectableChannels, selectedChannel, ChatChannel::displayName,
                        (dropdown, value) -> selectChannel(value))
                .dimensions(layout.leftX(), layout.channelInputY(), dropdownWidth, INPUT_HEIGHT)
                .indicator(NOTIFICATION_TEXTURE, channelMentions::isUnread, channelMentions::hasUnread)
                .maxVisibleRows(selectableChannels.size()).showOptionLabel(false).build());
        channelDropdown.setDropdownBounds(MARGIN, height - MARGIN);
        channelInput = addDrawableChild(new TextFieldWidget(textRenderer, layout.leftX() + dropdownWidth + 4,
                layout.channelInputY(), layout.leftWidth() - dropdownWidth - sendWidth - 8, INPUT_HEIGHT,
                Text.translatable("dmls.moderation.channel_input")));
        channelInput.setMaxLength(240);
        channelInput.setText(savedChannel);
        channelSuggestor = createSuggestor(channelInput);
        channelInput.setChangedListener(this::onChannelInputChanged);
        onChannelInputChanged(savedChannel);
        channelSend = addDrawableChild(ButtonWidget.builder(Text.translatable("dmls.moderation.send"), ignored -> sendSelected())
                .dimensions(layout.leftX() + layout.leftWidth() - sendWidth, layout.channelInputY(), sendWidth, INPUT_HEIGHT).build());

        int tabWidth = (layout.rightWidth() - 4) / 2;
        settingsTab = addDrawableChild(ButtonWidget.builder(Text.translatable("dmls.moderation.settings"), ignored -> {
                    selectedRightTab = RightTab.SETTINGS;
                    updateTabButtons();
                }).dimensions(layout.rightX(), layout.tabY(), tabWidth, INPUT_HEIGHT).build());
        miniMeTab = addDrawableChild(ButtonWidget.builder(Text.translatable("dmls.moderation.mini_me"), ignored -> openMiniMeTab())
                .dimensions(layout.rightX() + tabWidth + 4, layout.tabY(), tabWidth, INPUT_HEIGHT).build());

        ModalLayout modal = modalLayout();
        punishmentReason = addDrawableChild(new TextFieldWidget(textRenderer, modal.fieldX(), modal.reasonY(),
                modal.fieldWidth(), INPUT_HEIGHT, Text.translatable("dmls.moderation.reason")));
        punishmentReason.setMaxLength(PunishmentRequest.MAX_REASON_LENGTH);
        punishmentReason.setText(savedReason);
        punishmentDuration = addDrawableChild(new TextFieldWidget(textRenderer, modal.fieldX(), modal.durationY(),
                modal.fieldWidth(), INPUT_HEIGHT, Text.translatable("dmls.moderation.duration")));
        punishmentDuration.setMaxLength(16);
        punishmentDuration.setText(savedDuration);
        punishmentConfirm = addDrawableChild(ButtonWidget.builder(Text.translatable("dmls.moderation.confirm"), ignored -> confirmPunishment())
                .dimensions(modal.fieldX(), modal.buttonsY(), (modal.fieldWidth() - 4) / 2, INPUT_HEIGHT).build());
        punishmentCancel = addDrawableChild(ButtonWidget.builder(ScreenTexts.CANCEL, ignored -> closePunishmentModal())
                .dimensions(modal.fieldX() + (modal.fieldWidth() + 4) / 2, modal.buttonsY(),
                        (modal.fieldWidth() - 4) / 2, INPUT_HEIGHT).build());
        repositionModalWidgets();
        updateTabButtons();
        setModalVisibility(modalType != null);
        updateBaseVisibility();
        refreshServerAvailability();
    }

    private void sendGlobal() {
        if (!DMLSConfig.hasRecognizedStaffRank()) {
            showStatus(Text.translatable("dmls.moderation.send_blocked").getString());
            return;
        }
        String message = globalInput.getText().trim();
        if (message.startsWith("/")) {
            completeSend(globalInput, ClientUtils.dispatchCommand(client, message.substring(1)));
        } else {
            send(ChatChannel.GLOBAL, globalInput);
        }
    }

    private void sendSelected() {
        send(selectedChannel, channelInput);
    }

    private void send(ChatChannel channel, TextFieldWidget field) {
        String message = field.getText();
        completeSend(field, ModerationActions.sendToChannel(client, channel, message));
    }

    private void completeSend(TextFieldWidget field, CommandDispatch result) {
        if (result.accepted()) {
            field.setText("");
            if (result == CommandDispatch.SIMULATED) {
                showStatus(Text.translatable("dmls.moderation.simulated").getString());
            }
        } else {
            showStatus(Text.translatable("dmls.moderation.send_blocked").getString());
        }
    }

    private ChatInputSuggestor createSuggestor(TextFieldWidget input) {
        ChatInputSuggestor suggestor = new ChatInputSuggestor(client, new SuggestionOwner(input), input, textRenderer,
                false, false, 1, 10, true, PANEL_BACKGROUND);
        // Match ChatScreen: Tab belongs to autocomplete and must not move focus to another mod-view control.
        suggestor.setCanLeave(false);
        return suggestor;
    }

    private void onGlobalInputChanged(String value) {
        updateSuggestor(globalInput, globalSuggestor, value, true);
    }

    private void onChannelInputChanged(String value) {
        updateSuggestor(channelInput, channelSuggestor, value, false);
    }

    private void updateSuggestor(TextFieldWidget input, ChatInputSuggestor suggestor,
                                 String value, boolean commandsAllowed) {
        boolean empty = value.isEmpty();
        Text placeholder = commandsAllowed
                ? Text.translatable("dmls.moderation.type_message_or_command")
                : Text.translatable("dmls.moderation.type_channel_message", selectedChannel.displayName());
        input.setSuggestion(empty ? fitPlaceholder(input, placeholder.getString()) : null);
        boolean enabled = !empty && (commandsAllowed || !value.startsWith("/"))
                && canRefreshSuggestions();
        suggestor.setWindowActive(enabled);
        if (enabled) suggestor.refresh();
    }

    private String fitPlaceholder(TextFieldWidget input, String placeholder) {
        int availableWidth = Math.max(0, input.getWidth() - 8);
        if (textRenderer.getWidth(placeholder) <= availableWidth) return placeholder;
        String ellipsis = "…";
        int textWidth = availableWidth - textRenderer.getWidth(ellipsis);
        return textWidth <= 0 ? "" : textRenderer.trimToWidth(placeholder, textWidth) + ellipsis;
    }

    private boolean canRefreshSuggestions() {
        return client != null && client.player != null && client.getNetworkHandler() != null
                && client.getNetworkHandler().isConnectionOpen();
    }

    @Override
    public void tick() {
        refreshServerAvailability();
        if (statusTicks > 0 && --statusTicks == 0) status = "";
        long revision = ModerationChatService.revision();
        if (revision != lastRevision) {
            lastRevision = revision;
            updateChannelMentions();
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        refreshServerAvailability();
        if (!DMLSConfig.hasRecognizedStaffRank()) {
            close();
            return;
        }
        if (adminAccess != isAdmin()) {
            clearChildren();
            init();
        }
        // Minecraft has already applied the in-game screen blur for this frame.
        // Calling Screen.renderBackground here attempts a second blur and crashes.
        context.fill(0, 0, width, height, 0x70000000);
        Layout layout = layout();
        messageHitboxes.clear();
        punishmentHitboxes.clear();
        boolean contextMenuConsumesPointer = isPointerOverContextMenu(mouseX, mouseY);
        int contentMouseX = contextMenuConsumesPointer ? -10_000 : mouseX;
        int contentMouseY = contextMenuConsumesPointer ? -10_000 : mouseY;

        Set<Long> automaticFlags = ModerationChatService.automaticFlaggedSequences();
        renderFeed(context, layout.globalPane(), globalPane,
                ModerationChatService.messages().stream()
                        .filter(message -> preferences.includesInGlobal(message.channel())).toList(),
                automaticFlags, contentMouseX, contentMouseY);
        renderFeed(context, layout.channelPane(), channelPane,
                ModerationChatService.messages().stream()
                        .filter(message -> message.channel() == selectedChannel).toList(),
                automaticFlags, contentMouseX, contentMouseY);
        renderPunishmentLog(context, layout, contentMouseX, contentMouseY);
        renderRightPanel(context, layout, contentMouseX, contentMouseY);

        if (modalType != null) renderModalBackground(context);
        super.render(context, contentMouseX, contentMouseY, delta);

        if (modalType == null) {
            ChatInputSuggestor suggestor = activeSuggestor();
            if (suggestor != null) {
                context.createNewRootLayer();
                suggestor.render(context, contentMouseX, contentMouseY);
            }
            channelDropdown.renderDropdown(context, contentMouseX, contentMouseY, delta);
            renderContextMenu(context, mouseX, mouseY);
        } else {
            renderModalForeground(context);
        }
        if (!status.isEmpty()) {
            int statusWidth = textRenderer.getWidth(status) + STATUS_HORIZONTAL_PADDING * 2;
            int statusHeight = textRenderer.fontHeight + STATUS_VERTICAL_PADDING * 2;
            int x = (width - statusWidth) / 2;
            int y = 4;
            context.fill(x, y, x + statusWidth, y + statusHeight, 0xE0000000);
            context.drawStrokedRectangle(x, y, statusWidth, statusHeight, PANEL_BORDER);
            context.drawCenteredTextWithShadow(textRenderer, Text.literal(status), width / 2,
                    y + STATUS_VERTICAL_PADDING, 0xFFFFFFFF);
        }
    }

    private void renderFeed(DrawContext context, Rect pane, PaneState state,
                            List<ModerationMessage> messages, Set<Long> automaticFlags,
                            int mouseX, int mouseY) {
        drawPanel(context, pane);
        int innerX = pane.x() + 5;
        int innerY = pane.y() + 5;
        int contentRight = scrollbarX(pane) - 2;
        int rowLeft = innerX - 2;
        int innerWidth = Math.max(20, contentRight - innerX);
        int innerHeight = Math.max(1, pane.height() - 10);
        int lineHeight = textRenderer.fontHeight + 2;
        List<MessageBlock> blocks = new ArrayList<>();
        int totalHeight = 0;
        for (ModerationMessage message : messages) {
            Text display = preferences.showTimestamps()
                    ? Text.literal("§8[" + message.time() + "] §r").append(message.text())
                    : message.text();
            List<OrderedText> lines = textRenderer.wrapLines(display, innerWidth);
            int blockHeight = Math.max(lineHeight, lines.size() * lineHeight) + 2;
            blocks.add(new MessageBlock(message, lines, totalHeight, blockHeight));
            totalHeight += blockHeight;
        }
        state.maxScroll = Math.max(0, totalHeight - innerHeight);
        if (state.followBottom && contextMenu == null) state.scroll = state.maxScroll;
        state.scroll = Math.clamp(state.scroll, 0, state.maxScroll);

        context.enableScissor(pane.x() + 1, pane.y() + 1, pane.right() - 1, pane.bottom() - 1);
        if (blocks.isEmpty()) {
            context.drawCenteredTextWithShadow(textRenderer, Text.translatable("dmls.moderation.no_messages"),
                    pane.x() + pane.width() / 2, pane.y() + pane.height() / 2, 0xFFAAAAAA);
        }
        for (MessageBlock block : blocks) {
            int blockY = innerY + block.offset() - state.scroll;
            if (blockY + block.height() < innerY || blockY > innerY + innerHeight) continue;
            ModerationMessage message = block.message();
            boolean player = message.playerMessage();
            boolean hovered = player && mouseX >= rowLeft && mouseX < contentRight
                    && mouseY >= blockY && mouseY < blockY + block.height()
                    && mouseY >= innerY && mouseY < innerY + innerHeight;
            boolean selected = player && contextMenu != null && contextMenu.sourcePane == state
                    && contextMenu.message.sequence() == message.sequence();
            boolean alert = player && preferences.highlightAlerts()
                    && ChatAlertsModule.findWordlistMatch(message.messageBody()).isPresent();
            boolean automaticAlert = player && automaticFlags.contains(message.sequence());
            if (automaticAlert) {
                context.fill(rowLeft, blockY, contentRight, blockY + block.height(),
                        hovered || selected ? AUTOMATIC_ALERT_HOVER_BACKGROUND : AUTOMATIC_ALERT_BACKGROUND);
            } else if (alert || hovered || selected) {
                context.fill(rowLeft, blockY, contentRight, blockY + block.height(),
                        hovered || selected ? HOVER_BACKGROUND : ALERT_BACKGROUND);
            }
            if (player) {
                messageHitboxes.add(new MessageHitbox(message, state, rowLeft, blockY,
                        contentRight, blockY + block.height(), pane));
            }
            int y = blockY + 2;
            for (OrderedText line : block.lines()) {
                context.drawTextWithShadow(textRenderer, line, innerX, y, 0xFFFFFFFF);
                y += lineHeight;
            }
        }
        context.disableScissor();
        renderScrollbar(context, pane, state);
        if (state.maxScroll > 0 && !state.followBottom) {
            int size = 16;
            int x = scrollbarX(pane) - size - 3;
            int y = pane.bottom() - size - 6;
            context.fill(x, y, x + size, y + size, 0xD0303030);
            context.drawStrokedRectangle(x, y, size, size, PANEL_BORDER);
            context.drawCenteredTextWithShadow(textRenderer, Text.literal("↓"), x + size / 2, y + 4, 0xFFFFFFFF);
            state.jumpRect = new Rect(x, y, size, size);
        } else {
            state.jumpRect = null;
        }
    }

    private void renderScrollbar(DrawContext context, Rect pane, PaneState state) {
        if (state.maxScroll <= 0) return;
        int trackX = scrollbarX(pane);
        int trackY = scrollbarTop(pane);
        int trackHeight = scrollbarHeight(pane);
        int contentHeight = trackHeight + state.maxScroll;
        int thumbHeight = Math.clamp(trackHeight * trackHeight / Math.max(1, contentHeight), 18, trackHeight);
        int thumbY = trackY + state.scroll * Math.max(0, trackHeight - thumbHeight) / state.maxScroll;
        DMLSMenuScreen.renderVanillaScrollbar(context, trackX, trackY, trackHeight, thumbY, thumbHeight);
    }

    private void renderPunishmentLog(DrawContext context, Layout layout, int mouseX, int mouseY) {
        Rect pane = layout.logPane();
        drawPanel(context, pane);
        context.drawCenteredTextWithShadow(textRenderer, Text.translatable("dmls.moderation.punishment_log"),
                pane.x() + pane.width() / 2, pane.y() + 6, 0xFFFFFFFF);
        int contentTop = logScrollbarTop(pane);
        int rowHeight = 30;
        List<PunishmentLogEntry> entries = punishmentLogSource.latest().stream()
                .limit(PunishmentLogService.MAX_ENTRIES).toList();
        int viewportHeight = pane.bottom() - contentTop - 3;
        logMaxScroll = Math.max(0, entries.size() * rowHeight - viewportHeight);
        logScroll = Math.clamp(logScroll, 0, logMaxScroll);
        int contentRight = logMaxScroll > 0 ? logScrollbarX(pane) - 2 : pane.right() - 3;
        context.enableScissor(pane.x() + 1, contentTop, pane.right() - 1, pane.bottom() - 1);
        if (entries.isEmpty()) {
            context.drawCenteredTextWithShadow(textRenderer, Text.translatable("dmls.moderation.no_punishments"),
                    pane.x() + pane.width() / 2, contentTop + Math.max(6, viewportHeight / 2), 0xFF888888);
        }
        for (int index = 0; index < entries.size(); index++) {
            PunishmentLogEntry entry = entries.get(index);
            int y = contentTop + index * rowHeight - logScroll;
            if (y + rowHeight <= contentTop || y >= pane.bottom()) continue;
            int highlightTop = y + 1;
            int highlightBottom = y + rowHeight - 1;
            boolean hovered = mouseX >= pane.x() + 2 && mouseX < contentRight
                    && mouseY >= Math.max(highlightTop, contentTop)
                    && mouseY < Math.min(highlightBottom, pane.bottom());
            int highlightAlpha = entry.highlightAlpha(System.currentTimeMillis());
            if (highlightAlpha > 0) {
                context.fill(pane.x() + 2, highlightTop, pane.right() - 2, highlightBottom,
                        (entry.type().accentColor() & 0x00FFFFFF) | highlightAlpha << 24);
            }
            if (hovered) {
                context.fill(pane.x() + 2, highlightTop, pane.right() - 2, highlightBottom, HOVER_BACKGROUND);
                context.setCursor(StandardCursors.POINTING_HAND);
            }
            renderPunishmentAvatar(context, entry, pane.x() + 7, y + 3, 24);
            context.drawTextWithShadow(textRenderer, Text.literal(entry.playerName()), pane.x() + 38, y + 6, 0xFFFFFFFF);
            Text logLine = entry.duration().isEmpty()
                    ? Text.translatable("dmls.moderation.log_line", entry.type().displayName(), entry.staffName())
                    : Text.translatable("dmls.moderation.log_line_timed",
                    entry.type().displayName(), entry.duration(), entry.staffName());
            drawPunishmentSummary(context, logLine.copy().formatted(Formatting.GRAY),
                    pane.x() + 38, contentRight, y + 14, y + 29);
            punishmentHitboxes.add(new PunishmentHitbox(entry, pane.x() + 2, highlightTop,
                    contentRight, highlightBottom, new Rect(pane.x(), contentTop,
                    pane.width(), pane.bottom() - contentTop)));
        }
        context.disableScissor();
        if (logMaxScroll > 0) {
            int trackX = logScrollbarX(pane);
            int trackY = logScrollbarTop(pane);
            int trackHeight = logScrollbarHeight(pane);
            int contentHeight = trackHeight + logMaxScroll;
            int thumbHeight = Math.clamp(trackHeight * trackHeight / Math.max(1, contentHeight), 18, trackHeight);
            int thumbY = trackY + logScroll * Math.max(0, trackHeight - thumbHeight) / logMaxScroll;
            DMLSMenuScreen.renderVanillaScrollbar(context, trackX, trackY, trackHeight, thumbY, thumbHeight);
        }
    }

    private void renderPunishmentAvatar(DrawContext context, PunishmentLogEntry entry, int x, int y, int size) {
        PlayerSkinDrawer.draw(context, punishmentSkin(entry), x, y, size);
    }

    private SkinTextures punishmentSkin(PunishmentLogEntry entry) {
        String key = entry.playerName().toLowerCase(java.util.Locale.ROOT);
        SkinTextures known = punishmentSkinCache.get(key);
        if (known != null) return known;

        SkinTextures fallback = DefaultSkinHelper.getSkinTextures(entry.playerUuid());
        punishmentSkinCache.put(key, fallback);
        if (punishmentSkinLoading.add(key)) {
            CompletableFuture.supplyAsync(() -> client.getApiServices().profileRepository()
                            .findProfileByName(entry.playerName())
                            .map(nameAndId -> client.getApiServices().sessionService()
                                    .fetchProfile(nameAndId.id(), true))
                            .map(profileResult -> profileResult.profile())
                            .orElse(null))
                    .thenCompose(profile -> profile == null
                            ? CompletableFuture.completedFuture(Optional.<SkinTextures>empty())
                            : client.getSkinProvider().fetchSkinTextures(profile))
                    .thenAccept(textures -> client.execute(() -> {
                        textures.ifPresent(skin -> punishmentSkinCache.put(key, skin));
                        punishmentSkinLoading.remove(key);
                    }))
                    .exceptionally(error -> {
                        client.execute(() -> punishmentSkinLoading.remove(key));
                        return null;
                    });
        }
        return fallback;
    }

    private void renderRightPanel(DrawContext context, Layout layout, int mouseX, int mouseY) {
        Rect pane = layout.rightPane();
        drawPanel(context, pane);
        if (selectedRightTab == RightTab.MINI_ME) {
            settingsMaxScroll = 0;
            renderMiniMe(context, pane, mouseX, mouseY);
            return;
        }
        miniMeHitbox = null;
        List<SettingEntry> entries = settingEntries();
        int contentTop = pane.y() + 8;
        int contentBottom = pane.bottom() - 3;
        int viewportHeight = Math.max(1, contentBottom - contentTop);
        int contentHeight = entries.stream().mapToInt(SettingEntry::height).sum();
        settingsMaxScroll = Math.max(0, contentHeight - viewportHeight);
        settingsScroll = Math.clamp(settingsScroll, 0, settingsMaxScroll);
        int contentRight = settingsMaxScroll > 0 ? scrollbarX(pane) - 2 : pane.right() - 3;
        context.enableScissor(pane.x() + 1, pane.y() + 1, contentRight, pane.bottom() - 1);
        int y = contentTop - settingsScroll;
        for (SettingEntry entry : entries) {
            int entryHeight = entry.height();
            if (y + entryHeight <= contentTop) {
                y += entryHeight;
                continue;
            }
            if (y >= contentBottom) break;

            if (entry instanceof SettingSection section) {
                renderSettingSection(context, section.label(), pane.x() + 8, contentRight - 8,
                        y, entryHeight);
                y += entryHeight;
                continue;
            }

            SettingRow row = (SettingRow) entry;
            boolean hovered = mouseX >= pane.x() + 4 && mouseX < contentRight
                    && mouseY >= Math.max(y, contentTop) && mouseY < Math.min(y + entryHeight, contentBottom);
            if (hovered) context.fill(pane.x() + 3, y, contentRight, y + entryHeight, HOVER_BACKGROUND);
            int boxY = y + (ROW_HEIGHT - 10) / 2;
            context.fill(pane.x() + 8, boxY, pane.x() + 18, boxY + 10, 0xFF111111);
            context.drawStrokedRectangle(pane.x() + 8, boxY, 10, 10, PANEL_BORDER);
            if (row.enabled()) {
                context.fill(pane.x() + 10, boxY + 2, pane.x() + 16, boxY + 8, 0xFF55CC55);
            }
            context.drawTextWithShadow(textRenderer, row.label(), pane.x() + 24,
                    y + (ROW_HEIGHT - textRenderer.fontHeight) / 2, 0xFFFFFFFF);
            y += entryHeight;
        }
        context.disableScissor();
        if (settingsMaxScroll > 0) renderSettingsScrollbar(context, pane);
    }

    private void renderMiniMe(DrawContext context, Rect pane, int mouseX, int mouseY) {
        updateMiniMeAnimation();
        MiniMe miniMe = MINI_MES.get(selectedMiniMeIndex);
        int nameY = pane.bottom() - textRenderer.fontHeight - 6;
        int spriteTop = pane.y() + 7;
        int spriteBottom = nameY - 5;
        int spriteSize = Math.max(1, Math.min(pane.width() - 14, spriteBottom - spriteTop));
        int spriteX = pane.x() + (pane.width() - spriteSize) / 2;
        int spriteY = spriteBottom - spriteSize;
        miniMeHitbox = new Rect(spriteX, spriteY, spriteSize, spriteSize);

        if (miniMeHitbox.contains(mouseX, mouseY)) {
            context.setCursor(StandardCursors.POINTING_HAND);
        }

        float minimumDeformation = -0.10F - miniMeSpamEnergy * 0.06F;
        float maximumDeformation = 0.16F + miniMeSpamEnergy * 0.12F;
        float deformation = Math.clamp(miniMeSquish * 0.52F, minimumDeformation, maximumDeformation);
        float scaleX = 1.0F + deformation;
        float scaleY = 1.0F - deformation;
        context.enableScissor(pane.x() + 1, pane.y() + 1, pane.right() - 1, pane.bottom() - 1);
        context.getMatrices().pushMatrix();
        context.getMatrices().translate(spriteX + spriteSize / 2.0F, spriteBottom);
        context.getMatrices().scale(scaleX, scaleY);
        context.drawTexture(RenderPipelines.GUI_TEXTURED, miniMe.texture(),
                -spriteSize / 2, -spriteSize, 0.0F, 0.0F,
                spriteSize, spriteSize, MINI_ME_TEXTURE_SIZE, MINI_ME_TEXTURE_SIZE,
                MINI_ME_TEXTURE_SIZE, MINI_ME_TEXTURE_SIZE);
        context.getMatrices().popMatrix();
        context.drawCenteredTextWithShadow(textRenderer, Text.literal(miniMe.name()),
                pane.x() + pane.width() / 2, nameY, 0xFFFFFFFF);
        context.disableScissor();
    }

    private void updateMiniMeAnimation() {
        long now = System.nanoTime();
        float deltaSeconds = Math.min((now - miniMeAnimationNanos) / 1_000_000_000.0F, 0.05F);
        miniMeAnimationNanos = now;
        miniMeSpamEnergy = Math.max(0.0F, miniMeSpamEnergy - deltaSeconds * 0.65F);
        miniMeSquishVelocity += (-MINI_ME_SPRING_STRENGTH * miniMeSquish
                - MINI_ME_SPRING_DAMPING * miniMeSquishVelocity) * deltaSeconds;
        float maximumSquish = 0.36F + miniMeSpamEnergy * 0.22F;
        miniMeSquish = Math.clamp(miniMeSquish + miniMeSquishVelocity * deltaSeconds, -0.30F, maximumSquish);
        if (Math.abs(miniMeSquish) < 0.001F && Math.abs(miniMeSquishVelocity) < 0.01F) {
            miniMeSquish = 0.0F;
            miniMeSquishVelocity = 0.0F;
        }
    }

    private void squishMiniMe() {
        long now = System.nanoTime();
        if (now - lastMiniMeClickNanos <= MINI_ME_SPAM_WINDOW_NANOS) {
            miniMeSpamEnergy = Math.min(1.0F, miniMeSpamEnergy + 0.22F);
        } else {
            miniMeSpamEnergy = Math.max(0.08F, miniMeSpamEnergy);
        }
        lastMiniMeClickNanos = now;

        float clickCompression = 0.055F + miniMeSpamEnergy * 0.055F;
        float maximumSquish = 0.36F + miniMeSpamEnergy * 0.22F;
        miniMeSquish = Math.min(maximumSquish, Math.max(0.0F, miniMeSquish) + clickCompression);
        miniMeSquishVelocity = Math.min(10.0F,
                Math.max(0.0F, miniMeSquishVelocity) * 0.35F + 4.0F + miniMeSpamEnergy * 3.0F);
        miniMeAnimationNanos = now;
        if (client != null) {
            if (activeMiniMeSquishSound != null) {
                client.getSoundManager().stop(activeMiniMeSquishSound);
            }
            activeMiniMeSquishSound = PositionedSoundInstance.ui(MINI_ME_SQUISH_SOUND, 1.0F, 0.8F);
            client.getSoundManager().play(activeMiniMeSquishSound);
        }
    }

    private void cycleMiniMe() {
        selectedMiniMeIndex = (selectedMiniMeIndex + 1) % MINI_MES.size();
        miniMeSquish = 0.0F;
        miniMeSquishVelocity = 5.0F;
        miniMeSpamEnergy = 0.0F;
        lastMiniMeClickNanos = 0L;
        miniMeAnimationNanos = System.nanoTime();
    }

    private void openMiniMeTab() {
        selectedRightTab = RightTab.MINI_ME;
        selectedMiniMeIndex = ThreadLocalRandom.current().nextInt(MINI_MES.size());
        miniMeSquish = 0.0F;
        miniMeSquishVelocity = 0.0F;
        miniMeSpamEnergy = 0.0F;
        lastMiniMeClickNanos = 0L;
        miniMeAnimationNanos = System.nanoTime();
        if (client != null && activeMiniMeSquishSound != null) {
            client.getSoundManager().stop(activeMiniMeSquishSound);
            activeMiniMeSquishSound = null;
        }
        updateTabButtons();
    }

    private void renderSettingsScrollbar(DrawContext context, Rect pane) {
        int trackX = scrollbarX(pane);
        int trackY = scrollbarTop(pane);
        int trackHeight = scrollbarHeight(pane);
        int contentHeight = trackHeight + settingsMaxScroll;
        int thumbHeight = Math.clamp(trackHeight * trackHeight / Math.max(1, contentHeight), 18, trackHeight);
        int thumbY = trackY + settingsScroll * Math.max(0, trackHeight - thumbHeight) / settingsMaxScroll;
        DMLSMenuScreen.renderVanillaScrollbar(context, trackX, trackY, trackHeight, thumbY, thumbHeight);
    }

    private void renderSettingSection(DrawContext context, Text label, int left, int right, int y, int height) {
        int centerX = left + (right - left) / 2;
        int lineY = y + height / 2;
        int titleHalfWidth = textRenderer.getWidth(label) / 2;
        int titlePadding = 8;
        int leftLineEnd = centerX - titleHalfWidth - titlePadding;
        int rightLineStart = centerX + titleHalfWidth + titlePadding;
        if (leftLineEnd > left) context.fill(left, lineY, leftLineEnd, lineY + 1, PANEL_BORDER);
        if (right > rightLineStart) context.fill(rightLineStart, lineY, right, lineY + 1, PANEL_BORDER);
        context.drawCenteredTextWithShadow(textRenderer, label, centerX,
                lineY - textRenderer.fontHeight / 2, 0xFFFFFFFF);
    }

    private List<SettingEntry> settingEntries() {
        List<SettingEntry> entries = new ArrayList<>();
        entries.add(new SettingSection(Text.translatable("dmls.moderation.settings.section.chat_feed")));
        entries.add(new SettingRow(Text.translatable("dmls.moderation.include_local"), preferences.includeLocal(), SettingKey.LOCAL));
        entries.add(new SettingRow(Text.translatable("dmls.moderation.include_trade"), preferences.includeTrade(), SettingKey.TRADE));
        entries.add(new SettingRow(Text.translatable("dmls.moderation.include_rp"), preferences.includeRp(), SettingKey.RP));
        entries.add(new SettingRow(Text.translatable("dmls.moderation.include_staff"), preferences.includeStaff(), SettingKey.STAFF));
        if (isAdmin()) {
            entries.add(new SettingRow(Text.translatable("dmls.moderation.include_admin"), preferences.includeAdmin(), SettingKey.ADMIN));
        }
        entries.add(new SettingRow(Text.translatable("dmls.moderation.include_server"), preferences.includeServer(), SettingKey.SERVER));

        entries.add(new SettingSection(Text.translatable("dmls.moderation.settings.section.display")));
        entries.add(new SettingRow(Text.translatable("dmls.moderation.timestamps"), preferences.showTimestamps(), SettingKey.TIMESTAMPS));
        entries.add(new SettingRow(Text.translatable("dmls.moderation.highlight_alerts"), preferences.highlightAlerts(), SettingKey.ALERTS));

        List<AutomaticRuleDefinition> automaticRules = ModerationChatService.automaticRules();
        if (!automaticRules.isEmpty()) {
            entries.add(new SettingSection(Text.translatable("dmls.moderation.settings.section.automatic_detection")));
            for (AutomaticRuleDefinition rule : automaticRules) {
                entries.add(new SettingRow(Text.translatable(rule.labelKey()),
                        ModerationChatService.automaticRuleEnabled(rule.id()), SettingKey.AUTOMATIC_RULE, rule.id()));
            }
        }

        return entries;
    }

    private void renderContextMenu(DrawContext context, int mouseX, int mouseY) {
        if (contextMenu == null) return;
        int x = contextMenu.x;
        int y = contextMenu.y;
        int subX = submenuX(contextMenu);
        int subY = submenuY(contextMenu);
        boolean punishHovered = contains(mouseX, mouseY, x, y, MENU_WIDTH, CONTEXT_ROW_HEIGHT);
        boolean submenuHovered = contextMenu.submenuOpen && contains(mouseX, mouseY, subX, subY,
                SUBMENU_WIDTH, CONTEXT_ROW_HEIGHT * PunishmentType.values().length);
        contextMenu.updateHover(punishHovered, submenuHovered);
        drawMenuPanel(context, x, y, MENU_WIDTH, CONTEXT_ROW_HEIGHT * 4);
        drawMenuRow(context, Text.translatable("dmls.moderation.context.punish").append("  ›"), x, y,
                MENU_WIDTH, true, mouseX, mouseY);
        drawMenuRow(context, Text.translatable("dmls.moderation.context.copy_ign"), x, y + CONTEXT_ROW_HEIGHT,
                MENU_WIDTH, true, mouseX, mouseY);
        drawMenuRow(context, Text.translatable("dmls.moderation.context.copy_uuid"), x, y + CONTEXT_ROW_HEIGHT * 2,
                MENU_WIDTH, true, mouseX, mouseY);
        drawMenuRow(context, Text.translatable("dmls.moderation.context.copy_message"), x, y + CONTEXT_ROW_HEIGHT * 3,
                MENU_WIDTH, true, mouseX, mouseY);
        if (isPointerOverContextMenu(mouseX, mouseY)) context.setCursor(StandardCursors.ARROW);
        if (!contextMenu.submenuOpen) return;
        drawMenuPanel(context, subX, subY, SUBMENU_WIDTH, CONTEXT_ROW_HEIGHT * PunishmentType.values().length);
        for (int index = 0; index < PunishmentType.values().length; index++) {
            PunishmentType type = PunishmentType.values()[index];
            boolean enabled = DMLSConfig.staffRank().isAtLeast(type.minimumRank());
            drawMenuRow(context, type.displayName(), subX, subY + index * CONTEXT_ROW_HEIGHT,
                    SUBMENU_WIDTH, enabled, mouseX, mouseY);
        }
    }

    private boolean isPointerOverContextMenu(double mouseX, double mouseY) {
        ContextMenu menu = contextMenu;
        if (menu == null) return false;
        if (contains(mouseX, mouseY, menu.x, menu.y, MENU_WIDTH, CONTEXT_ROW_HEIGHT * 4)) return true;
        return menu.submenuOpen && contains(mouseX, mouseY, submenuX(menu), submenuY(menu),
                SUBMENU_WIDTH, CONTEXT_ROW_HEIGHT * PunishmentType.values().length);
    }

    private void renderModalBackground(DrawContext context) {
        context.fill(0, 0, width, height, 0xA0000000);
        ModalLayout modal = modalLayout();
        drawPanel(context, modal.panel());
        context.drawCenteredTextWithShadow(textRenderer, modalType.displayName(), width / 2,
                modal.panel().y() + 10, 0xFFFFFFFF);
        context.drawTextWithShadow(textRenderer, Text.translatable("dmls.moderation.target", modalIgn),
                modal.fieldX(), modal.panel().y() + 32, 0xFFDDDDDD);
        context.drawTextWithShadow(textRenderer, Text.translatable("dmls.moderation.reason"),
                modal.fieldX(), modal.reasonY() - 12, 0xFFCCCCCC);
        if (modalType.durationRequired()) {
            context.drawTextWithShadow(textRenderer, Text.translatable("dmls.moderation.duration"),
                    modal.fieldX(), modal.durationY() - 12, 0xFFCCCCCC);
        }
    }

    private void renderModalForeground(DrawContext context) {
        PunishmentRequest.Validation validation = PunishmentRequest.validate(modalType, modalIgn,
                punishmentDuration.getText(), punishmentReason.getText());
        punishmentConfirm.active = ServerGuard.check(client).allowed()
                && validation == PunishmentRequest.Validation.VALID;
        String command = commandPreview();
        ModalLayout modal = modalLayout();
        int previewY = modal.previewY();
        int previewBottom = modal.buttonsY() - (DMLSConfig.dryRun() ? 28 : 8);
        context.enableScissor(modal.fieldX(), previewY, modal.fieldX() + modal.fieldWidth(), previewBottom);
        for (OrderedText line : textRenderer.wrapLines(Text.literal("§7/" + command), modal.fieldWidth())) {
            if (previewY + textRenderer.fontHeight > previewBottom) break;
            context.drawTextWithShadow(textRenderer, line, modal.fieldX(), previewY, 0xFFFFFFFF);
            previewY += textRenderer.fontHeight + 2;
        }
        context.disableScissor();
        if (DMLSConfig.dryRun()) {
            context.drawCenteredTextWithShadow(textRenderer, Text.translatable("dmls.moderation.dry_run_preview"),
                    width / 2, modal.buttonsY() - 20, 0xFFFFFF55);
        }
    }

    private void confirmPunishment() {
        PunishmentRequest.Validation validation = PunishmentRequest.validate(modalType, modalIgn,
                punishmentDuration.getText(), punishmentReason.getText());
        if (validation != PunishmentRequest.Validation.VALID) {
            showStatus(validationMessage(validation));
            return;
        }
        PunishmentRequest request = new PunishmentRequest(modalType, modalIgn,
                punishmentDuration.getText(), punishmentReason.getText());
        ModerationActions.Outcome outcome = ModerationActions.punish(client, request);
        switch (outcome) {
            case SENT -> {
                closePunishmentModal();
                showStatus(Text.translatable("dmls.moderation.punishment_sent").getString());
            }
            case SIMULATED -> {
                closePunishmentModal();
                showStatus(Text.translatable("dmls.moderation.simulated").getString());
            }
            case BUSY -> showStatus(Text.translatable("dmls.validation.operation.busy").getString());
            case RANK_BLOCKED -> showStatus(Text.translatable("dmls.validation.required_rank").getString());
            default -> showStatus(Text.translatable("dmls.moderation.send_blocked").getString());
        }
    }

    private String validationMessage(PunishmentRequest.Validation validation) {
        return Text.translatable(switch (validation) {
            case INVALID_DURATION -> "dmls.validation.ban.duration";
            case INVALID_REASON -> "dmls.validation.ban.reason";
            case INVALID_IGN -> "dmls.validation.ban.ign";
            default -> "dmls.moderation.send_blocked";
        }).getString();
    }

    private String commandPreview() {
        String reason = punishmentReason == null ? "" : punishmentReason.getText().trim();
        String duration = punishmentDuration == null ? "" : punishmentDuration.getText().trim();
        return modalType.durationRequired()
                ? "%s %s %s %s".formatted(modalType.command(), modalIgn, duration, reason)
                : "%s %s %s".formatted(modalType.command(), modalIgn, reason);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        refreshServerAvailability();
        if (modalType != null) return super.mouseClicked(click, doubled);
        ChatInputSuggestor suggestor = activeSuggestor();
        if (suggestor != null && suggestor.mouseClicked(click)) return true;
        if (channelDropdown.isDropdownOpen() && channelDropdown.mouseClicked(click, doubled)) {
            setFocused(channelDropdown);
            return true;
        }
        if (contextMenu != null && handleContextClick(click)) return true;
        if (contextMenu != null) contextMenu = null;

        Layout layout = layout();
        if (selectedRightTab == RightTab.MINI_ME && miniMeHitbox != null
                && miniMeHitbox.contains(click.x(), click.y())) {
            if (click.button() == 0) {
                squishMiniMe();
                return true;
            }
            if (click.button() == 1) {
                cycleMiniMe();
                return true;
            }
        }
        if (selectedRightTab == RightTab.SETTINGS && layout.rightPane().contains(click.x(), click.y())) {
            Rect pane = layout.rightPane();
            if (settingsMaxScroll > 0 && click.button() == 0 && overScrollbar(pane, click.x(), click.y())) {
                draggingSettingsScrollbar = true;
                updateSettingsScroll(pane, click.y());
                return true;
            }
            int contentRight = settingsMaxScroll > 0 ? scrollbarX(pane) - 2 : pane.right() - 3;
            if (click.x() < contentRight && click.y() >= pane.y() + 8 && click.y() < pane.bottom() - 3
                    && click.button() == 0) {
                int y = pane.y() + 8 - settingsScroll;
                for (SettingEntry entry : settingEntries()) {
                    if (click.y() >= y && click.y() < y + entry.height()) {
                        if (entry instanceof SettingRow row) {
                            toggleSetting(row);
                            return true;
                        }
                        break;
                    }
                    y += entry.height();
                }
            }
        }
        if (globalPane.jumpRect != null && globalPane.jumpRect.contains(click.x(), click.y())) {
            globalPane.followBottom = true;
            globalPane.scroll = globalPane.maxScroll;
            return true;
        }
        if (channelPane.jumpRect != null && channelPane.jumpRect.contains(click.x(), click.y())) {
            channelPane.followBottom = true;
            channelPane.scroll = channelPane.maxScroll;
            return true;
        }
        if (startScrollbarDrag(layout.globalPane(), globalPane, click)
                || startScrollbarDrag(layout.channelPane(), channelPane, click)) return true;
        if (logMaxScroll > 0 && click.button() == 0
                && overLogScrollbar(layout.logPane(), click.x(), click.y())) {
            draggingLogScrollbar = true;
            updateLogScroll(layout.logPane(), click.y());
            return true;
        }

        if (click.button() == 0) {
            for (PunishmentHitbox hitbox : punishmentHitboxes) {
                if (hitbox.contains(click.x(), click.y())) {
                    client.setScreen(new PunishmentDetailsScreen(this, hitbox.entry()));
                    return true;
                }
            }
            for (MessageHitbox hitbox : messageHitboxes) {
                if (hitbox.contains(click.x(), click.y())) {
                    openContextMenu(hitbox.message(), hitbox.state(), (int) click.x(), (int) click.y());
                    return true;
                }
            }
        }
        return super.mouseClicked(click, doubled);
    }

    private boolean handleContextClick(Click click) {
        ContextMenu menu = contextMenu;
        int row = contextRowAt(click.x(), click.y(), menu.x, menu.y, MENU_WIDTH, 4);
        if (row == 0) {
            menu.submenuOpen = true;
            return true;
        }
        if (row == 1) {
            resolveIgn(menu.message, ign -> ign.ifPresentOrElse(value -> {
                client.keyboard.setClipboard(value);
                showStatus(Text.translatable("dmls.moderation.copied_ign").getString());
            }, () -> showStatus(Text.translatable("dmls.moderation.identity_failed").getString())));
            contextMenu = null;
            return true;
        }
        if (row == 2) {
            showStatus(Text.translatable("dmls.moderation.resolving_uuid").getString());
            ModerationChatService.resolveUuid(client, menu.message, uuid -> uuid.ifPresentOrElse(value -> {
                client.keyboard.setClipboard(value.toString());
                showStatus(Text.translatable("dmls.moderation.copied_uuid").getString());
            }, () -> showStatus(Text.translatable("dmls.moderation.uuid_failed").getString())));
            contextMenu = null;
            return true;
        }
        if (row == 3) {
            client.keyboard.setClipboard(menu.message.messageBody());
            showStatus(Text.translatable("dmls.moderation.copied_message").getString());
            contextMenu = null;
            return true;
        }
        if (menu.submenuOpen) {
            int subRow = contextRowAt(click.x(), click.y(), submenuX(menu), submenuY(menu),
                    SUBMENU_WIDTH, PunishmentType.values().length);
            if (subRow >= 0) {
                PunishmentType type = PunishmentType.values()[subRow];
                if (!DMLSConfig.staffRank().isAtLeast(type.minimumRank())) return true;
                contextMenu = null;
                resolveIgn(menu.message, ign -> ign.ifPresentOrElse(value -> openPunishmentModal(type, value),
                        () -> showStatus(Text.translatable("dmls.moderation.identity_failed").getString())));
                return true;
            }
        }
        return false;
    }

    private void resolveIgn(ModerationMessage message, java.util.function.Consumer<Optional<String>> callback) {
        Optional<String> known = ModerationChatService.knownIgn(message);
        if (known.isEmpty()) showStatus(DMLSConfig.dryRun()
                ? Text.translatable("dmls.moderation.identity_dry_run").getString()
                : Text.translatable("dmls.moderation.resolving_identity").getString());
        ModerationChatService.resolveIgn(client, message, callback);
    }

    private void openPunishmentModal(PunishmentType type, String ign) {
        modalType = type;
        modalIgn = ign;
        repositionModalWidgets();
        punishmentReason.setText("");
        punishmentDuration.setText("");
        setModalVisibility(true);
        updateBaseVisibility();
        setFocused(type.durationRequired() ? punishmentDuration : punishmentReason);
    }

    private void closePunishmentModal() {
        modalType = null;
        modalIgn = "";
        setModalVisibility(false);
        updateBaseVisibility();
        setFocused(null);
    }

    private void setModalVisibility(boolean visible) {
        if (punishmentReason == null) return;
        punishmentReason.visible = visible;
        punishmentDuration.visible = visible && modalType != null && modalType.durationRequired();
        punishmentConfirm.visible = visible;
        punishmentCancel.visible = visible;
    }

    private void updateBaseVisibility() {
        boolean visible = modalType == null;
        globalInput.visible = visible;
        globalSend.visible = visible;
        channelDropdown.visible = visible;
        channelInput.visible = visible;
        channelSend.visible = visible;
        settingsTab.visible = visible;
        miniMeTab.visible = visible;
    }

    private void refreshServerAvailability() {
        if (globalInput == null || channelInput == null || channelDropdown == null
                || globalSend == null || channelSend == null || punishmentConfirm == null) {
            return;
        }
        boolean serverAllowed = ServerGuard.check(client).allowed();
        globalInput.active = serverAllowed;
        globalInput.setEditable(serverAllowed);
        channelInput.active = serverAllowed;
        channelInput.setEditable(serverAllowed);
        channelDropdown.active = serverAllowed;
        globalSend.active = serverAllowed;
        channelSend.active = serverAllowed;
        globalSend.setTooltip(serverAllowed ? null : STONEWORKS_CONNECTION_TOOLTIP);
        channelSend.setTooltip(serverAllowed ? null : STONEWORKS_CONNECTION_TOOLTIP);
        punishmentConfirm.setTooltip(serverAllowed ? null : STONEWORKS_CONNECTION_TOOLTIP);
        if (!serverAllowed) {
            punishmentConfirm.active = false;
            channelDropdown.closeDropdown();
            if (globalSuggestor != null) globalSuggestor.setWindowActive(false);
            if (channelSuggestor != null) channelSuggestor.setWindowActive(false);
        } else if (!serverAllowedPreviously) {
            onGlobalInputChanged(globalInput.getText());
            onChannelInputChanged(channelInput.getText());
        }
        serverAllowedPreviously = serverAllowed;
    }

    private void toggleSetting(SettingRow row) {
        if (row.key == SettingKey.AUTOMATIC_RULE) {
            if (!ModerationChatService.setAutomaticRuleEnabled(row.ruleId, !row.enabled)) {
                showStatus(Text.translatable("dmls.validation.config.save_failed").getString());
            }
            return;
        }
        ModerationPreferences updated = switch (row.key) {
            case LOCAL -> preferences.withChannel(ChatChannel.LOCAL, !preferences.includeLocal());
            case TRADE -> preferences.withChannel(ChatChannel.TRADE, !preferences.includeTrade());
            case RP -> preferences.withChannel(ChatChannel.RP, !preferences.includeRp());
            case STAFF -> preferences.withChannel(ChatChannel.STAFF, !preferences.includeStaff());
            case ADMIN -> isAdmin()
                    ? preferences.withChannel(ChatChannel.ADMIN, !preferences.includeAdmin()) : preferences;
            case SERVER -> preferences.withChannel(ChatChannel.SERVER, !preferences.includeServer());
            case TIMESTAMPS -> preferences.withTimestamps(!preferences.showTimestamps());
            case ALERTS -> preferences.withHighlightAlerts(!preferences.highlightAlerts());
            case AUTOMATIC_RULE -> throw new IllegalStateException("Automatic rules are handled separately");
        };
        if (DMLSConfig.setModerationPreferences(updated)) {
            preferences = updated;
        } else {
            showStatus(Text.translatable("dmls.validation.config.save_failed").getString());
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (modalType != null) return true;
        if (contextMenu != null) return true;
        ChatInputSuggestor suggestor = activeSuggestor();
        if (suggestor != null && suggestor.mouseScrolled(verticalAmount)) return true;
        if (channelDropdown.isDropdownOpen()
                && channelDropdown.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)) return true;
        Layout layout = layout();
        if (layout.globalPane().contains(mouseX, mouseY)) return scrollPane(globalPane, verticalAmount);
        if (layout.channelPane().contains(mouseX, mouseY)) return scrollPane(channelPane, verticalAmount);
        if (layout.logPane().contains(mouseX, mouseY)) {
            logScroll = Math.clamp(logScroll - (int) (verticalAmount * 24), 0, logMaxScroll);
            return true;
        }
        if (selectedRightTab == RightTab.SETTINGS && layout.rightPane().contains(mouseX, mouseY)) {
            settingsScroll = Math.clamp(settingsScroll - (int) (verticalAmount * ROW_HEIGHT), 0, settingsMaxScroll);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    private boolean scrollPane(PaneState pane, double amount) {
        pane.scroll = Math.clamp(pane.scroll - (int) (amount * 24), 0, pane.maxScroll);
        pane.followBottom = pane.scroll >= pane.maxScroll;
        return true;
    }

    private boolean startScrollbarDrag(Rect rect, PaneState pane, Click click) {
        if (pane.maxScroll <= 0 || click.button() != 0 || !overScrollbar(rect, click.x(), click.y())) return false;
        draggingPane = pane;
        updateDraggedPane(rect, pane, click.y());
        return true;
    }

    @Override
    public boolean mouseDragged(Click click, double deltaX, double deltaY) {
        if (draggingSettingsScrollbar) {
            updateSettingsScroll(layout().rightPane(), click.y());
            return true;
        }
        if (draggingLogScrollbar) {
            updateLogScroll(layout().logPane(), click.y());
            return true;
        }
        if (draggingPane != null) {
            Layout layout = layout();
            Rect rect = draggingPane == globalPane ? layout.globalPane() : layout.channelPane();
            updateDraggedPane(rect, draggingPane, click.y());
            return true;
        }
        if (channelDropdown.isDraggingScrollbar()) return channelDropdown.mouseDragged(click, deltaX, deltaY);
        return super.mouseDragged(click, deltaX, deltaY);
    }

    private void updateDraggedPane(Rect rect, PaneState pane, double mouseY) {
        double ratio = Math.clamp((mouseY - rect.y()) / Math.max(1.0, rect.height()), 0.0, 1.0);
        pane.scroll = (int) Math.round(ratio * pane.maxScroll);
        pane.followBottom = pane.scroll >= pane.maxScroll;
    }

    private void updateLogScroll(Rect rect, double mouseY) {
        double ratio = Math.clamp((mouseY - logScrollbarTop(rect)) / Math.max(1.0, logScrollbarHeight(rect)),
                0.0, 1.0);
        logScroll = (int) Math.round(ratio * logMaxScroll);
    }

    private void updateSettingsScroll(Rect rect, double mouseY) {
        double ratio = Math.clamp((mouseY - scrollbarTop(rect)) / Math.max(1.0, scrollbarHeight(rect)), 0.0, 1.0);
        settingsScroll = (int) Math.round(ratio * settingsMaxScroll);
    }

    @Override
    public boolean mouseReleased(Click click) {
        draggingPane = null;
        draggingLogScrollbar = false;
        draggingSettingsScrollbar = false;
        if (channelDropdown.isDraggingScrollbar()) return channelDropdown.mouseReleased(click);
        return super.mouseReleased(click);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        refreshServerAvailability();
        if (channelInput != null && channelInput.isFocused() && input.isTab()
                && channelInput.getText().startsWith("/")) return true;
        ChatInputSuggestor suggestor = activeSuggestor();
        if (suggestor != null && suggestor.keyPressed(input)) return true;
        if (input.isEscape()) {
            if (modalType != null) {
                closePunishmentModal();
                return true;
            }
            if (contextMenu != null) {
                contextMenu = null;
                return true;
            }
            if (channelDropdown.isDropdownOpen()) {
                channelDropdown.closeDropdown();
                return true;
            }
        }
        if (input.getKeycode() == InputUtil.GLFW_KEY_ENTER || input.getKeycode() == InputUtil.GLFW_KEY_KP_ENTER) {
            if (modalType != null && punishmentConfirm.active) {
                confirmPunishment();
                return true;
            }
            if (globalInput.isFocused() && globalSend.active) {
                sendGlobal();
                return true;
            }
            if (channelInput.isFocused() && channelSend.active) {
                sendSelected();
                return true;
            }
        }
        return super.keyPressed(input);
    }

    private ChatInputSuggestor activeSuggestor() {
        if (modalType != null || contextMenu != null || channelDropdown == null
                || channelDropdown.isDropdownOpen()) return null;
        if (globalInput != null && globalInput.isFocused()) return globalSuggestor;
        if (channelInput != null && channelInput.isFocused()) return channelSuggestor;
        return null;
    }

    private void selectChannel(ChatChannel channel) {
        selectedChannel = channel;
        channelMentions.markRead(channel);
        if (channelInput != null && channelSuggestor != null) {
            onChannelInputChanged(channelInput.getText());
        }
    }

    private void updateChannelMentions() {
        if (client == null || client.player == null) return;
        channelMentions.update(ModerationChatService.messages(), client.player.getName().getString(),
                selectedChannel, selectableChannels());
    }

    private List<ChatChannel> selectableChannels() {
        return ChatChannel.selectableFor(isAdmin());
    }

    private boolean isAdmin() {
        return DMLSConfig.staffRank().isAtLeast(StaffRank.ADMIN);
    }

    private void openContextMenu(ModerationMessage message, PaneState sourcePane, int mouseX, int mouseY) {
        int totalWidth = MENU_WIDTH + SUBMENU_WIDTH;
        int x = Math.clamp(mouseX, 2, Math.max(2, width - totalWidth - 2));
        int y = Math.clamp(mouseY, 2, Math.max(2, height - CONTEXT_ROW_HEIGHT * 4 - 2));
        contextMenu = new ContextMenu(message, sourcePane, x, y);
    }

    private int submenuX(ContextMenu menu) {
        return menu.x + MENU_WIDTH;
    }

    private int submenuY(ContextMenu menu) {
        return Math.clamp(menu.y, 2,
                Math.max(2, height - CONTEXT_ROW_HEIGHT * PunishmentType.values().length - 2));
    }

    private void drawMenuPanel(DrawContext context, int x, int y, int width, int height) {
        context.createNewRootLayer();
        context.fill(x, y, x + width, y + height, 0xF0101010);
        context.drawStrokedRectangle(x, y, width, height, PANEL_BORDER);
    }

    private void drawMenuRow(DrawContext context, Text text, int x, int y, int width, boolean enabled,
                             int mouseX, int mouseY) {
        if (enabled && contains(mouseX, mouseY, x, y, width, CONTEXT_ROW_HEIGHT)) {
            context.fill(x + 1, y + 1, x + width - 1, y + CONTEXT_ROW_HEIGHT - 1, HOVER_BACKGROUND);
        }
        context.drawTextWithShadow(textRenderer, text, x + 7,
                y + 7, enabled ? 0xFFFFFFFF : 0xFF777777);
    }

    private static int contextRowAt(double x, double y, int boxX, int boxY, int boxWidth, int rows) {
        if (!contains(x, y, boxX, boxY, boxWidth, rows * CONTEXT_ROW_HEIGHT)) return -1;
        return (int) ((y - boxY) / CONTEXT_ROW_HEIGHT);
    }

    private static boolean contains(double x, double y, int boxX, int boxY, int boxWidth, int boxHeight) {
        return x >= boxX && x < boxX + boxWidth && y >= boxY && y < boxY + boxHeight;
    }

    private static int scrollbarX(Rect pane) {
        return pane.right() - SCROLLBAR_INSET - DMLSMenuScreen.SCROLLBAR_WIDTH;
    }

    private static int logScrollbarX(Rect pane) {
        return pane.right() - DMLSMenuScreen.SCROLLBAR_WIDTH - 1;
    }

    private static int logScrollbarTop(Rect pane) {
        return pane.y() + LOG_CONTENT_TOP_OFFSET;
    }

    private static int logScrollbarHeight(Rect pane) {
        return Math.max(1, pane.bottom() - SCROLLBAR_INSET - logScrollbarTop(pane));
    }

    private static int scrollbarTop(Rect pane) {
        return pane.y() + SCROLLBAR_INSET;
    }

    private static int scrollbarHeight(Rect pane) {
        return Math.max(1, pane.height() - SCROLLBAR_INSET * 2);
    }

    private static boolean overScrollbar(Rect pane, double mouseX, double mouseY) {
        int x = scrollbarX(pane);
        return contains(mouseX, mouseY, x, scrollbarTop(pane),
                DMLSMenuScreen.SCROLLBAR_WIDTH, scrollbarHeight(pane));
    }

    private static boolean overLogScrollbar(Rect pane, double mouseX, double mouseY) {
        return contains(mouseX, mouseY, logScrollbarX(pane), logScrollbarTop(pane),
                DMLSMenuScreen.SCROLLBAR_WIDTH, logScrollbarHeight(pane));
    }

    private void showStatus(String value) {
        status = value == null ? "" : value;
        statusTicks = 100;
    }

    private void updateTabButtons() {
        if (settingsTab != null) settingsTab.active = selectedRightTab != RightTab.SETTINGS;
        if (miniMeTab != null) miniMeTab.active = selectedRightTab != RightTab.MINI_ME;
    }

    private void drawPanel(DrawContext context, Rect rect) {
        context.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), PANEL_BACKGROUND);
        context.drawStrokedRectangle(rect.x(), rect.y(), rect.width(), rect.height(), PANEL_BORDER);
    }

    private Layout layout() {
        int availableWidth = Math.max(220, width - MARGIN * 2 - GAP);
        int rightWidth = Math.clamp((int) (availableWidth * 0.30), 120, 270);
        int leftWidth = availableWidth - rightWidth;
        if (leftWidth < 170) {
            leftWidth = 170;
            rightWidth = Math.max(90, availableWidth - leftWidth);
        }
        int leftX = MARGIN;
        int rightX = leftX + leftWidth + GAP;
        if (rightX + rightWidth > width - MARGIN) rightWidth = Math.max(90, width - MARGIN - rightX);

        int availableHeight = Math.max(180, height - MARGIN * 2);
        int feedHeight = Math.max(60, availableHeight - INPUT_HEIGHT * 2 - GAP * 3);
        int globalHeight = Math.max(50, (int) (feedHeight * 0.56));
        int channelHeight = Math.max(50, feedHeight - globalHeight);
        int globalY = MARGIN;
        int globalInputY = globalY + globalHeight + GAP;
        int channelY = globalInputY + INPUT_HEIGHT + GAP;
        int channelInputY = channelY + channelHeight + GAP;

        int logHeight = globalHeight;
        int tabY = globalInputY;
        int rightPaneY = channelY;
        int rightPaneHeight = Math.max(40, height - MARGIN - rightPaneY);
        return new Layout(leftX, rightX, leftWidth, rightWidth,
                new Rect(leftX, globalY, leftWidth, globalHeight), globalInputY,
                new Rect(leftX, channelY, leftWidth, channelHeight), channelInputY,
                new Rect(rightX, MARGIN, rightWidth, logHeight), tabY,
                new Rect(rightX, rightPaneY, rightWidth, rightPaneHeight));
    }

    private ModalLayout modalLayout() {
        int panelWidth = Math.min(360, width - 40);
        int desiredHeight = modalType != null && modalType.durationRequired() ? 250 : 210;
        int panelHeight = Math.min(desiredHeight, height - 24);
        int x = (width - panelWidth) / 2;
        int y = (height - panelHeight) / 2;
        int fieldX = x + 18;
        int fieldWidth = panelWidth - 36;
        int reasonY = y + 64;
        int durationY = y + 106;
        int previewY = modalType != null && modalType.durationRequired() ? y + 134 : y + 94;
        int buttonsY = y + panelHeight - 30;
        return new ModalLayout(new Rect(x, y, panelWidth, panelHeight), fieldX, fieldWidth,
                reasonY, durationY, previewY, buttonsY);
    }

    private void repositionModalWidgets() {
        if (punishmentReason == null) return;
        ModalLayout modal = modalLayout();
        punishmentReason.setDimensionsAndPosition(modal.fieldWidth(), INPUT_HEIGHT, modal.fieldX(), modal.reasonY());
        punishmentDuration.setDimensionsAndPosition(modal.fieldWidth(), INPUT_HEIGHT, modal.fieldX(), modal.durationY());
        int buttonWidth = (modal.fieldWidth() - 4) / 2;
        punishmentConfirm.setDimensionsAndPosition(buttonWidth, INPUT_HEIGHT, modal.fieldX(), modal.buttonsY());
        punishmentCancel.setDimensionsAndPosition(buttonWidth, INPUT_HEIGHT,
                modal.fieldX() + buttonWidth + 4, modal.buttonsY());
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void close() {
        client.setScreen(parent);
    }

    /** Called only for a threshold crossing received while this screen is current. */
    void onAutomaticRuleAlert(long alertRevision) {
        if (alertRevision <= lastAutomaticAlertRevision || client == null || client.currentScreen != this) return;
        lastAutomaticAlertRevision = alertRevision;
        client.getSoundManager().play(PositionedSoundInstance.ui(SoundEvents.BLOCK_NOTE_BLOCK_CHIME, 1.0F));
    }

    private enum RightTab { SETTINGS, MINI_ME }

    private static final class PaneState {
        private int scroll;
        private int maxScroll;
        private boolean followBottom = true;
        private Rect jumpRect;
    }

    private static final class ContextMenu {
        private final ModerationMessage message;
        private final PaneState sourcePane;
        private final int x;
        private final int y;
        private boolean submenuOpen;
        private long punishHoverStartedAt = -1L;

        private ContextMenu(ModerationMessage message, PaneState sourcePane, int x, int y) {
            this.message = message;
            this.sourcePane = sourcePane;
            this.x = x;
            this.y = y;
        }

        private void updateHover(boolean punishHovered, boolean submenuHovered) {
            if (submenuOpen) {
                if (!punishHovered && !submenuHovered) {
                    submenuOpen = false;
                    punishHoverStartedAt = -1L;
                }
                return;
            }
            if (!punishHovered) {
                punishHoverStartedAt = -1L;
                return;
            }
            long now = System.nanoTime();
            if (punishHoverStartedAt < 0L) {
                punishHoverStartedAt = now;
            } else if (now - punishHoverStartedAt >= SUBMENU_HOVER_DELAY_NANOS) {
                submenuOpen = true;
            }
        }
    }

    private interface SettingEntry {
        Text label();
        int height();
    }

    private record SettingSection(Text label) implements SettingEntry {
        @Override
        public int height() {
            return SETTINGS_SECTION_HEIGHT;
        }
    }

    private static final class SettingRow implements SettingEntry {
        private final Text label;
        private final boolean enabled;
        private final SettingKey key;
        private final String ruleId;

        private SettingRow(Text label, boolean enabled, SettingKey key) {
            this(label, enabled, key, "");
        }

        private SettingRow(Text label, boolean enabled, SettingKey key, String ruleId) {
            this.label = label;
            this.enabled = enabled;
            this.key = key;
            this.ruleId = ruleId;
        }

        public Text label() { return label; }
        boolean enabled() { return enabled; }

        @Override
        public int height() {
            return ROW_HEIGHT;
        }
    }

    private enum SettingKey {
        LOCAL, TRADE, RP, STAFF, ADMIN, SERVER, TIMESTAMPS, ALERTS, AUTOMATIC_RULE
    }

    private record Rect(int x, int y, int width, int height) {
        int right() { return x + width; }
        int bottom() { return y + height; }
        boolean contains(double px, double py) { return px >= x && px < right() && py >= y && py < bottom(); }
    }

    private record Layout(int leftX, int rightX, int leftWidth, int rightWidth,
                          Rect globalPane, int globalInputY, Rect channelPane, int channelInputY,
                          Rect logPane, int tabY, Rect rightPane) {
    }

    private record ModalLayout(Rect panel, int fieldX, int fieldWidth, int reasonY, int durationY,
                               int previewY, int buttonsY) {
    }

    private record MessageBlock(ModerationMessage message, List<OrderedText> lines, int offset, int height) {
    }

    private record MessageHitbox(ModerationMessage message, PaneState state,
                                 int left, int top, int right, int bottom, Rect clip) {
        boolean contains(double x, double y) {
            return x >= left && x < right && y >= Math.max(top, clip.y())
                    && y < Math.min(bottom, clip.bottom());
        }
    }

    private record MiniMe(String name, Identifier texture) {
    }

    /** Makes vanilla's bottom-anchored suggestion window line up with an arbitrary input field. */
    private final class SuggestionOwner extends Screen {
        private SuggestionOwner(TextFieldWidget input) {
            super(Text.empty());
            width = ModerationScreen.this.width;
            height = input.getY() + 12;
        }

        @Override
        public Element getFocused() {
            return ModerationScreen.this.getFocused();
        }
    }

    private void drawPunishmentSummary(DrawContext context, Text text, int left, int right, int top, int bottom) {
        int scaledLeft = Math.round(left / PUNISHMENT_SUMMARY_SCALE);
        int scaledRight = Math.round(right / PUNISHMENT_SUMMARY_SCALE);
        int scaledTop = Math.round(top / PUNISHMENT_SUMMARY_SCALE);
        int scaledBottom = Math.round(bottom / PUNISHMENT_SUMMARY_SCALE);
        int centeredX = scaledLeft + textRenderer.getWidth(text) / 2;

        context.getMatrices().pushMatrix();
        context.getMatrices().scale(PUNISHMENT_SUMMARY_SCALE, PUNISHMENT_SUMMARY_SCALE);
        context.getTextConsumer().marqueedText(text, centeredX,
                scaledLeft, scaledRight, scaledTop, scaledBottom);
        context.getMatrices().popMatrix();
    }

    private record PunishmentHitbox(PunishmentLogEntry entry,
                                    int left, int top, int right, int bottom, Rect clip) {
        boolean contains(double x, double y) {
            return x >= left && x < right && y >= Math.max(top, clip.y())
                    && y < Math.min(bottom, clip.bottom());
        }
    }
}
