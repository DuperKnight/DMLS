package com.duperknight.client.gui.modules;

import com.duperknight.client.gui.DMLSMenuScreen;
import com.duperknight.client.modules.ChatReplayModule;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Asynchronously searches and displays bounded chunks from persistent Minecraft-session chat logs. */
public final class ChatReplayScreen extends DMLSMenuScreen {
    private static final int REFRESH_INTERVAL_TICKS = 40;
    private static final int FILTER_DEBOUNCE_TICKS = 8;
    private static final int SESSION_BUTTON_GAP = scaled(4);
    private static final int SESSION_LIST_WIDTH = scaled(140);
    private static final int PANE_GAP = scaled(14);

    private TextFieldWidget filterField;
    private ButtonWidget jumpToLatestButton;
    private ButtonWidget exportButton;
    private String filter;
    private List<OrderedText> lines = List.of();
    private final List<SessionButton> sessionButtons = new ArrayList<>();
    private String selectedSessionId;
    private ChatReplayModule.Chunk chunk = new ChatReplayModule.Chunk(List.of(), 0, false, false);
    private long lastRevision = -1;
    private long loadGeneration;
    private int lineHeight;
    private int contentX;
    private int contentWidth;
    private int scrollbarX;
    private int ticksSinceRefresh;
    private int filterDebounce;
    private int lastCurrentMessageCount;
    private int sessionScrollOffset;
    private int maxSessionScroll;
    private boolean loading;
    private boolean loadFailed;
    private boolean exporting;
    private boolean draggingSessionScrollbar;
    private Text exportStatus;

    public ChatReplayScreen(Screen parent, ChatReplayModule module, String initialFilter) {
        super(Text.translatable("dmls.module.chat_replay.name"), parent);
        this.filter = initialFilter;
    }

    @Override
    protected void init() {
        boolean reinitializing = selectedSessionId != null;
        boolean wasAtBottom = isContentScrolledToBottom();
        double previousScrollRatio = maxContentScroll() == 0
                ? 0.0
                : (double) contentScrollOffset() / maxContentScroll();

        // Screen.init is called again after window or GUI-scale changes. The
        // vanilla child list is rebuilt automatically; mirror that here so
        // stale buttons do not inflate the session content height.
        sessionButtons.clear();
        draggingSessionScrollbar = false;
        maxSessionScroll = 0;

        FooterLayout footer = footerLayout();
        filterField = addDrawableChild(new TextFieldWidget(textRenderer, footer.filterX(), footerButtonY(),
                footer.filterWidth(), STANDARD_BUTTON_HEIGHT, Text.translatable("dmls.field.filter")));
        filterField.setMaxLength(64);
        filterField.setText(filter);
        filterField.setSuggestion(filter.isEmpty() ? Text.translatable("dmls.placeholder.filter").getString() : null);
        filterField.setChangedListener(value -> {
            filter = value;
            filterField.setSuggestion(value.isEmpty() ? Text.translatable("dmls.placeholder.filter").getString() : null);
            filterDebounce = FILTER_DEBOUNCE_TICKS;
        });

        exportButton = addDrawableChild(ButtonWidget.builder(Text.translatable("dmls.button.export"), ignored -> exportCurrent())
                .dimensions(footer.exportX(), footerButtonY(), footer.buttonWidth(), STANDARD_BUTTON_HEIGHT).build());
        addDrawableChild(ButtonWidget.builder(ScreenTexts.BACK, button -> close())
                .dimensions(footer.backX(), footerButtonY(), footer.buttonWidth(), STANDARD_BUTTON_HEIGHT).build());

        ReplayLayout layout = replayLayout();
        jumpToLatestButton = addDrawableChild(ButtonWidget.builder(Text.literal("↓"), ignored -> jumpToLatest())
                .dimensions(layout.navigationX(), height - FOOTER_TOP_OFFSET - STANDARD_BUTTON_HEIGHT - scaled(5),
                        STANDARD_BUTTON_HEIGHT, STANDARD_BUTTON_HEIGHT)
                .build());

        List<ChatReplayModule.Session> sessions = ChatReplayModule.sessions();
        boolean selectedSessionStillExists = selectedSessionId != null
                && sessions.stream().anyMatch(session -> session.id().equals(selectedSessionId));
        if (!selectedSessionStillExists && !sessions.isEmpty()) {
            selectedSessionId = sessions.getFirst().id();
        }
        for (int index = 0; index < sessions.size(); index++) {
            ChatReplayModule.Session session = sessions.get(index);
            ButtonWidget button = addDrawableChild(ButtonWidget.builder(Text.literal(session.startedAt()),
                            ignored -> selectSession(session.id()))
                    .dimensions(layout.sessionX(), layout.viewportTop() + index * (STANDARD_BUTTON_HEIGHT + SESSION_BUTTON_GAP),
                            layout.sessionWidth(), STANDARD_BUTTON_HEIGHT)
                    .build());
            sessionButtons.add(new SessionButton(session.id(), button));
        }
        updateSessionScrollBounds();
        updateSessionButtonPositions();
        updateSessionButtons();
        if (reinitializing && selectedSessionStillExists) {
            rebuildChunkLines();
            if (wasAtBottom) {
                scrollContentToBottom();
            } else {
                setContentScrollOffset((int) Math.round(previousScrollRatio * maxContentScroll()));
            }
            updateNavigationButtons();
        } else {
            requestChunk(-1, true);
        }
    }

    private void selectSession(String sessionId) {
        selectedSessionId = sessionId;
        updateSessionButtons();
        requestChunk(-1, true);
    }

    private void updateSessionButtons() {
        sessionButtons.forEach(entry -> entry.button().active = !entry.sessionId().equals(selectedSessionId));
    }

    private void jumpToLatest() {
        if (!chunk.hasNext()) {
            scrollContentToBottom();
        } else {
            requestChunk(-1, true);
        }
        updateNavigationButtons();
    }

    private void exportCurrent() {
        if (selectedSessionId == null || exporting) return;
        exporting = true;
        exportStatus = Text.translatable("dmls.screen.chat_replay.exporting");
        updateNavigationButtons();
        ChatReplayModule.export(selectedSessionId, filter).whenComplete((path, error) -> client.execute(() -> {
            exporting = false;
            exportStatus = error == null
                    ? Text.translatable("dmls.screen.chat_replay.exported", path.getFileName().toString())
                    : Text.translatable("dmls.screen.chat_replay.export_failed");
            updateNavigationButtons();
        }));
    }

    private void requestChunk(int offset, boolean scrollToBottom) {
        if (selectedSessionId == null) {
            applyChunk(new ChatReplayModule.Chunk(List.of(), 0, false, false), false);
            return;
        }

        beginChunkLoad(ChatReplayModule.loadChunk(selectedSessionId, filter, offset), scrollToBottom);
    }

    private void requestPreviousChunk() {
        if (selectedSessionId == null) return;
        beginChunkLoad(ChatReplayModule.loadChunkEndingAt(selectedSessionId, filter, chunk.offset()), true);
    }

    private void beginChunkLoad(java.util.concurrent.CompletableFuture<ChatReplayModule.Chunk> future,
                                boolean scrollToBottom) {
        long generation = ++loadGeneration;
        loading = true;
        loadFailed = false;
        updateNavigationButtons();
        future.whenComplete((loadedChunk, error) ->
                client.execute(() -> {
                    if (generation != loadGeneration) return;
                    loading = false;
                    loadFailed = error != null;
                    if (error == null) {
                        applyChunk(loadedChunk, scrollToBottom);
                    } else {
                        applyChunk(new ChatReplayModule.Chunk(List.of(), 0, false, false), false);
                        loadFailed = true;
                    }
                }));
    }

    private void applyChunk(ChatReplayModule.Chunk loadedChunk, boolean scrollToBottom) {
        chunk = loadedChunk;
        lastRevision = ChatReplayModule.revision();
        if (selectedSessionId != null && selectedSessionId.equals(ChatReplayModule.currentSessionId())) {
            lastCurrentMessageCount = ChatReplayModule.currentMessageCount();
        }
        ticksSinceRefresh = 0;
        rebuildChunkLines();
        if (scrollToBottom) {
            scrollContentToBottom();
        } else {
            resetContentScroll();
        }
        updateNavigationButtons();
    }

    private void rebuildChunkLines() {
        ReplayLayout layout = replayLayout();
        contentX = layout.contentX();
        contentWidth = layout.contentWidth();
        scrollbarX = layout.scrollbarX();
        lineHeight = Math.max(textRenderer.fontHeight + 1, scaled(10));

        List<OrderedText> built = new ArrayList<>();
        for (ChatReplayModule.Entry entry : chunk.entries()) {
            Text line = Text.literal("§8[" + entry.time() + "] §r").append(entry.text());
            built.addAll(textRenderer.wrapLines(line, contentWidth - scaled(8)));
        }
        lines = List.copyOf(built);
        int contentHeight = lines.isEmpty() ? 0 : lines.size() * lineHeight + scaled(8);
        configureScrollableContent(layout.viewportTop(), contentHeight);
    }

    private void updateNavigationButtons() {
        if (jumpToLatestButton == null) return;
        jumpToLatestButton.active = !loading && (chunk.hasNext()
                || (!lines.isEmpty() && !isContentScrolledToBottom()));
        if (exportButton != null) {
            exportButton.active = !exporting && selectedSessionId != null
                    && ChatReplayModule.isSessionStored(selectedSessionId);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        ReplayLayout layout = replayLayout();
        if (isOverSessionViewport(layout, mouseX, mouseY) && maxSessionScroll > 0) {
            sessionScrollOffset = Math.clamp(sessionScrollOffset - (int) (verticalAmount * scaled(24)),
                    0, maxSessionScroll);
            updateSessionButtonPositions();
            return true;
        }
        if (mouseX < layout.contentX() || mouseX > layout.scrollbarX() + SCROLLBAR_WIDTH
                || mouseY < layout.viewportTop() || mouseY >= height - FOOTER_TOP_OFFSET - scaled(8)) {
            return false;
        }
        if (loading) {
            return true;
        }

        boolean handled = super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        if (verticalAmount < 0 && isContentScrolledToBottom() && chunk.hasNext()) {
            // Keep the boundary message as a visual anchor while swapping chunks.
            int forwardStep = Math.max(1, chunk.entries().size() - 1);
            requestChunk(chunk.offset() + forwardStep, false);
            return true;
        }
        if (verticalAmount > 0 && isContentScrolledToTop() && chunk.hasPrevious()) {
            requestPreviousChunk();
            return true;
        }
        return handled;
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        ReplayLayout layout = replayLayout();
        if (click.button() == 0 && maxSessionScroll > 0
                && click.x() >= layout.sessionScrollbarX()
                && click.x() <= layout.sessionScrollbarX() + SCROLLBAR_WIDTH
                && click.y() >= layout.viewportTop() && click.y() < sessionViewportBottom()) {
            draggingSessionScrollbar = true;
            updateSessionScrollFromMouse(click.y());
            return true;
        }
        boolean sessionViewportClick = click.x() >= layout.sessionX()
                && click.x() < layout.sessionX() + layout.sessionWidth()
                && click.y() >= layout.viewportTop() && click.y() < sessionViewportBottom();
        if (!sessionViewportClick) {
            sessionButtons.forEach(entry -> entry.button().visible = false);
        }
        boolean handled = super.mouseClicked(click, doubled);
        updateSessionButtonPositions();
        if (handled && isOverChatScrollbar(replayLayout(), click.x(), click.y())
                && loadAdjacentChunkAtScrollbarEdge()) {
            stopDraggingContentScrollbar();
        }
        return handled;
    }

    @Override
    public boolean mouseDragged(Click click, double deltaX, double deltaY) {
        if (draggingSessionScrollbar) {
            updateSessionScrollFromMouse(click.y());
            return true;
        }
        boolean draggingChatScrollbar = isDraggingContentScrollbar();
        boolean handled = super.mouseDragged(click, deltaX, deltaY);
        if (draggingChatScrollbar && loadAdjacentChunkAtScrollbarEdge()) {
            stopDraggingContentScrollbar();
            return true;
        }
        return handled;
    }

    @Override
    public boolean mouseReleased(Click click) {
        draggingSessionScrollbar = false;
        return super.mouseReleased(click);
    }

    @Override
    public void tick() {
        if (filterDebounce > 0 && --filterDebounce == 0) {
            requestChunk(-1, true);
        }

        ticksSinceRefresh++;
        if (!loading && ticksSinceRefresh >= REFRESH_INTERVAL_TICKS
                && selectedSessionId != null
                && selectedSessionId.equals(ChatReplayModule.currentSessionId())
                && !chunk.hasNext()
                && isContentScrolledToBottom()
                && ChatReplayModule.revision() != lastRevision) {
            refreshCurrentSessionTail();
        }
    }

    private void refreshCurrentSessionTail() {
        ChatReplayModule.CurrentUpdates updates = ChatReplayModule.currentUpdatesAfter(
                selectedSessionId, lastCurrentMessageCount);
        if (!updates.complete()) {
            requestChunk(-1, true);
            return;
        }

        String needle = filter.trim().toLowerCase(Locale.ROOT);
        List<ChatReplayModule.Entry> merged = new ArrayList<>(chunk.entries());
        int offset = chunk.offset();
        for (ChatReplayModule.Entry entry : updates.entries()) {
            if (!needle.isEmpty() && !entry.cleanText().toLowerCase(Locale.ROOT).contains(needle)) continue;
            merged.add(entry);
            if (merged.size() > ChatReplayModule.DISPLAY_CHUNK_SIZE) {
                merged.removeFirst();
                offset++;
            }
        }

        lastCurrentMessageCount = updates.messageCount();
        lastRevision = ChatReplayModule.revision();
        ticksSinceRefresh = 0;
        if (!merged.equals(chunk.entries())) {
            applyChunk(new ChatReplayModule.Chunk(List.copyOf(merged), offset, offset > 0, false), true);
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderMenuBackground(context);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2,
                HEADER_HEIGHT + scaled(16), 0xFFFFFFFF);

        beginContentScissor(context);
        if (loading && lines.isEmpty()) {
            drawStatus(context, Text.translatable("dmls.screen.chat_replay.loading"));
        } else if (loadFailed) {
            drawStatus(context, Text.translatable("dmls.screen.chat_replay.load_failed"));
        } else if (lines.isEmpty()) {
            drawStatus(context, Text.translatable("dmls.screen.chat_replay.empty"));
        } else {
            int firstVisibleLine = firstVisibleContentIndex(lineHeight);
            int endVisibleLine = visibleContentEndIndex(lineHeight, lines.size());
            for (int index = firstVisibleLine; index < endVisibleLine; index++) {
                int y = contentY(index * lineHeight);
                if (isContentVisible(y, textRenderer.fontHeight)) {
                    context.drawTextWithShadow(textRenderer, lines.get(index), contentX, y, 0xFFFFFFFF);
                }
            }
        }
        endContentScissor(context);
        updateNavigationButtons();
        if (exportStatus != null) {
            context.drawCenteredTextWithShadow(textRenderer, exportStatus, width / 2,
                    height - FOOTER_TOP_OFFSET + scaled(4), exporting ? 0xFFAAAAAA : 0xFFFFFFFF);
        }
        sessionButtons.forEach(entry -> entry.button().visible = false);
        super.render(context, mouseX, mouseY, delta);
        renderSessionButtons(context, mouseX, mouseY, delta);
        renderSessionScrollbar(context);
    }

    private void drawStatus(DrawContext context, Text message) {
        int y = contentY(scaled(14));
        if (isContentVisible(y, textRenderer.fontHeight)) {
            context.drawCenteredTextWithShadow(textRenderer, message, width / 2, y, 0xFFAAAAAA);
        }
    }

    @Override
    protected int contentScrollbarX() {
        return scrollbarX;
    }

    private ReplayLayout replayLayout() {
        int totalWidth = Math.min(scaled(650), width - scaled(32));
        int sessionWidth = Math.min(SESSION_LIST_WIDTH, totalWidth / 3);
        int sessionX = (width - totalWidth) / 2;
        int contentX = sessionX + sessionWidth + PANE_GAP;
        int contentWidth = Math.max(scaled(100), totalWidth - sessionWidth - PANE_GAP - scaled(38));
        int viewportTop = HEADER_HEIGHT + scaled(34);
        int scrollbarX = contentX + contentWidth + scaled(4);
        return new ReplayLayout(sessionX, sessionWidth, sessionX + sessionWidth + scaled(3),
                contentX, contentWidth, scrollbarX,
                scrollbarX + scaled(10), viewportTop);
    }

    private void updateSessionScrollBounds() {
        int contentHeight = sessionButtons.isEmpty() ? 0
                : sessionButtons.size() * STANDARD_BUTTON_HEIGHT
                + (sessionButtons.size() - 1) * SESSION_BUTTON_GAP;
        maxSessionScroll = Math.max(0, contentHeight - (sessionViewportBottom() - replayLayout().viewportTop()));
        sessionScrollOffset = Math.clamp(sessionScrollOffset, 0, maxSessionScroll);
    }

    private void updateSessionButtonPositions() {
        int viewportTop = replayLayout().viewportTop();
        int viewportBottom = sessionViewportBottom();
        for (int index = 0; index < sessionButtons.size(); index++) {
            ButtonWidget button = sessionButtons.get(index).button();
            int y = viewportTop + index * (STANDARD_BUTTON_HEIGHT + SESSION_BUTTON_GAP) - sessionScrollOffset;
            button.setY(y);
            button.visible = y < viewportBottom && y + STANDARD_BUTTON_HEIGHT > viewportTop;
        }
    }

    private void renderSessionButtons(DrawContext context, int mouseX, int mouseY, float delta) {
        ReplayLayout layout = replayLayout();
        int viewportBottom = sessionViewportBottom();
        boolean mouseInside = mouseX >= layout.sessionX()
                && mouseX < layout.sessionX() + layout.sessionWidth()
                && mouseY >= layout.viewportTop() && mouseY < viewportBottom;
        int clippedMouseX = mouseInside ? mouseX : Integer.MIN_VALUE;
        int clippedMouseY = mouseInside ? mouseY : Integer.MIN_VALUE;
        context.enableScissor(layout.sessionX(), layout.viewportTop(),
                layout.sessionX() + layout.sessionWidth(), viewportBottom);
        updateSessionButtonPositions();
        for (SessionButton entry : sessionButtons) {
            if (entry.button().visible) {
                entry.button().render(context, clippedMouseX, clippedMouseY, delta);
            }
        }
        context.disableScissor();
    }

    private void renderSessionScrollbar(DrawContext context) {
        if (maxSessionScroll <= 0) return;
        ReplayLayout layout = replayLayout();
        int viewportHeight = sessionViewportBottom() - layout.viewportTop();
        int thumbHeight = scrollbarThumbHeight(viewportHeight, viewportHeight + maxSessionScroll);
        int thumbY = layout.viewportTop()
                + sessionScrollOffset * (viewportHeight - thumbHeight) / maxSessionScroll;
        renderVanillaScrollbar(context, layout.sessionScrollbarX(), layout.viewportTop(), viewportHeight,
                thumbY, thumbHeight);
    }

    private void updateSessionScrollFromMouse(double mouseY) {
        ReplayLayout layout = replayLayout();
        int viewportHeight = sessionViewportBottom() - layout.viewportTop();
        int thumbHeight = scrollbarThumbHeight(viewportHeight, viewportHeight + maxSessionScroll);
        int trackHeight = Math.max(1, viewportHeight - thumbHeight);
        double relative = Math.clamp((int) (mouseY - layout.viewportTop() - thumbHeight / 2.0), 0, trackHeight);
        sessionScrollOffset = Math.clamp((int) Math.round(relative * maxSessionScroll / trackHeight),
                0, maxSessionScroll);
        updateSessionButtonPositions();
    }

    private boolean isOverSessionViewport(ReplayLayout layout, double mouseX, double mouseY) {
        return mouseX >= layout.sessionX()
                && mouseX <= layout.sessionScrollbarX() + SCROLLBAR_WIDTH
                && mouseY >= layout.viewportTop() && mouseY < sessionViewportBottom();
    }

    private boolean isOverChatScrollbar(ReplayLayout layout, double mouseX, double mouseY) {
        return mouseX >= layout.scrollbarX() && mouseX <= layout.scrollbarX() + SCROLLBAR_WIDTH
                && mouseY >= layout.viewportTop() && mouseY < sessionViewportBottom();
    }

    private boolean loadAdjacentChunkAtScrollbarEdge() {
        if (loading) return false;
        if (isContentScrolledToBottom() && chunk.hasNext()) {
            int forwardStep = Math.max(1, chunk.entries().size() - 1);
            requestChunk(chunk.offset() + forwardStep, false);
            return true;
        }
        if (isContentScrolledToTop() && chunk.hasPrevious()) {
            requestPreviousChunk();
            return true;
        }
        return false;
    }

    private int sessionViewportBottom() {
        return height - FOOTER_TOP_OFFSET - scaled(8);
    }

    private FooterLayout footerLayout() {
        int totalWidth = Math.min(scaled(620), width - scaled(32));
        int gap = scaled(8);
        int filterWidth = (totalWidth - gap * 2) / 2;
        int buttonWidth = (totalWidth - gap * 2 - filterWidth) / 2;
        int filterX = (width - totalWidth) / 2;
        int exportX = filterX + filterWidth + gap;
        return new FooterLayout(filterX, filterWidth, exportX, exportX + buttonWidth + gap, buttonWidth);
    }

    private record ReplayLayout(int sessionX, int sessionWidth, int sessionScrollbarX,
                                int contentX, int contentWidth,
                                int scrollbarX, int navigationX, int viewportTop) {
    }

    private record FooterLayout(int filterX, int filterWidth, int exportX, int backX, int buttonWidth) {
    }

    private record SessionButton(String sessionId, ButtonWidget button) {
    }
}
