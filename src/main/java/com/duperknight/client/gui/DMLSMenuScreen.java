package com.duperknight.client.gui;

import com.duperknight.DMLS;
import com.duperknight.client.accountlink.DiscordAccountProfileStore;
import com.duperknight.client.accountlink.DiscordAvatarCache;
import com.duperknight.client.accountlink.DiscordLinkService;
import com.duperknight.client.accountlink.DiscordLinkAvailability;
import com.duperknight.client.accountlink.DiscordLinkTokenStore;
import com.duperknight.client.gui.widgets.DiscordAccountWidget;
import com.duperknight.client.gui.widgets.DropdownWidget;
import com.duperknight.client.modules.DMLSModule;
import com.duperknight.client.utils.DMLSConfig;
import com.duperknight.client.utils.HeaderBehavior;
import com.duperknight.client.utils.ServerGuard;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/** Shared in-game menu chrome used by every DMLS screen. */
public abstract class DMLSMenuScreen extends Screen {
    private static final Tooltip STONEWORKS_CONNECTION_TOOLTIP = Tooltip.of(
            Text.translatable("dmls.tooltip.connect_stoneworks"));
    private static final Set<java.util.UUID> ACCOUNT_LINK_CHECKS = ConcurrentHashMap.newKeySet();
    private static final Set<java.util.UUID> ACCOUNT_LINK_CHECKS_IN_PROGRESS = ConcurrentHashMap.newKeySet();
    protected static final float UI_SCALE = 0.85F;
    private static final Identifier LOGO = Identifier.of(DMLS.MOD_ID.toLowerCase(), "logo.png");
    private static final Identifier HEADER_SEPARATOR = Identifier.ofVanilla("textures/gui/inworld_header_separator.png");
    private static final Identifier FOOTER_SEPARATOR = Identifier.ofVanilla("textures/gui/inworld_footer_separator.png");
    protected static final Identifier SCROLLER = Identifier.ofVanilla("widget/scroller");
    protected static final Identifier SCROLLER_BACKGROUND = Identifier.ofVanilla("widget/scroller_background");
    public static final int SCROLLBAR_WIDTH = 6;
    public static final int PANEL_BACKGROUND_COLOR = 0xC0101010;
    public static final int PANEL_BORDER_COLOR = 0xFF9A9A9A;
    private static final int LOGO_TEXTURE_WIDTH = 2040;
    private static final int LOGO_TEXTURE_HEIGHT = 400;
    protected static final int HEADER_HEIGHT = scaled(80);
    protected static final int FOOTER_TOP_OFFSET = scaled(55);
    private static final int COMPACT_HEADER_HEIGHT = FOOTER_TOP_OFFSET;
    private static final long HEADER_ANIMATION_NANOS = 140_000_000L;
    private static final int HEADER_COMPACT_SCROLL_THRESHOLD = scaled(28);
    private static final int HEADER_EXPAND_SCROLL_THRESHOLD = scaled(4);
    private static final int MIN_EXPANDED_HEADER_WIDTH = scaled(520);
    private static final int MIN_EXPANDED_HEADER_HEIGHT = scaled(430);
    protected static final int STANDARD_BUTTON_HEIGHT = 20;
    private static final int PAIRED_BUTTON_MARGIN = scaled(16);
    private static final int MAX_PAIRED_BUTTON_WIDTH = scaled(250);

    protected final Screen parent;
    private final List<ScrollableWidget> scrollableWidgets = new ArrayList<>();
    private final Set<ClickableWidget> disabledScrollableWidgets =
            Collections.newSetFromMap(new IdentityHashMap<>());
    private final List<DropdownWidget<?>> dropdownWidgets = new ArrayList<>();
    private final List<CommandControl> commandControls = new ArrayList<>();
    private int contentViewportTop;
    private int contentViewportBottom;
    private int contentViewportHeaderOffset;
    private int configuredContentHeight;
    private boolean contentViewportTracksHeader;
    private int contentScrollOffset;
    private int maxContentScroll;
    private boolean draggingContentScrollbar;
    private boolean compactHeaderForScroll;
    private int downwardHeaderScrollDistance;
    private float headerCompactProgress;
    private long lastHeaderAnimationNanos = System.nanoTime();
    private boolean discordAccountInitialized;
    private DiscordAccountWidget accountWidget;
    private Identifier accountAvatarTexture;

    protected DMLSMenuScreen(Text title, Screen parent) {
        super(title);
        this.parent = parent;
        headerCompactProgress = DMLSConfig.headerBehavior() == HeaderBehavior.ALWAYS_SMALL ? 1.0F : 0.0F;
    }

    protected void renderMenuBackground(DrawContext context) {
        // Screen.renderBackground has already applied the vanilla blurred in-world background.
        initializeDiscordAccount();
        updateHeaderAnimation();
        refreshHeaderTrackedViewport();
        int headerHeight = headerHeight();
        if (accountWidget != null) {
            accountWidget.setY(Math.max(0, (headerHeight - DiscordAccountWidget.HEIGHT) / 2));
        }
        context.fill(0, 0, width, headerHeight, 0x20000000);

        float compactProgress = easedHeaderCompactProgress();
        int expandedLogoWidth = Math.clamp(width - scaled(32), scaled(160), scaled(205));
        int compactLogoWidth = Math.min(expandedLogoWidth, scaled(160));
        int logoWidth = interpolate(expandedLogoWidth, compactLogoWidth, compactProgress);
        int logoHeight = Math.max(1, logoWidth * LOGO_TEXTURE_HEIGHT / LOGO_TEXTURE_WIDTH);
        int expandedLogoX = (width - expandedLogoWidth) / 2;
        int compactLogoX = Math.max(scaled(4), width - compactLogoWidth - scaled(16));
        int logoX = interpolate(expandedLogoX, compactLogoX, compactProgress);
        int expandedLogoY = scaled(22);
        int compactLogoY = Math.max(0, (COMPACT_HEADER_HEIGHT - logoHeight) / 2);
        int logoY = interpolate(expandedLogoY, compactLogoY, compactProgress);
        context.drawTexture(RenderPipelines.GUI_TEXTURED, LOGO, logoX, logoY, 0.0F, 0.0F,
                logoWidth, logoHeight, LOGO_TEXTURE_WIDTH, LOGO_TEXTURE_HEIGHT,
                LOGO_TEXTURE_WIDTH, LOGO_TEXTURE_HEIGHT);

        int footerTop = height - FOOTER_TOP_OFFSET;
        context.fill(0, headerHeight, width, footerTop, 0xA6000000);
        context.fill(0, footerTop, width, height, 0x20000000);

        // Use the same two-layer translucent separators as vanilla in-world lists.
        context.drawTexture(RenderPipelines.GUI_TEXTURED, HEADER_SEPARATOR, 0, headerHeight - 2,
                0.0F, 0.0F, width, 2, 32, 2);
        context.drawTexture(RenderPipelines.GUI_TEXTURED, FOOTER_SEPARATOR, 0, footerTop,
                0.0F, 0.0F, width, 2, 32, 2);
    }

    /** Full-height variant for dense readers that do not need the shared logo header. */
    protected void renderMenuBackgroundWithoutHeader(DrawContext context) {
        initializeDiscordAccount();
        if (accountWidget != null) accountWidget.setY(scaled(2));
        int footerTop = height - FOOTER_TOP_OFFSET;
        context.fill(0, 0, width, footerTop, 0xA6000000);
        context.fill(0, footerTop, width, height, 0x20000000);
        context.drawTexture(RenderPipelines.GUI_TEXTURED, FOOTER_SEPARATOR, 0, footerTop,
                0.0F, 0.0F, width, 2, 32, 2);
    }

    protected void renderPanel(DrawContext context, int x, int y, int panelWidth, int panelHeight) {
        context.fill(x, y, x + panelWidth, y + panelHeight, PANEL_BACKGROUND_COLOR);
        context.drawStrokedRectangle(x, y, panelWidth, panelHeight, PANEL_BORDER_COLOR);
    }

    /** Renders the standard title and description block for a module screen. */
    protected void renderModuleHeader(DrawContext context, DMLSModule module) {
        int descriptionY = headerHeight() + scaled(34);
        int descriptionWidth = Math.min(scaled(360), width - scaled(32));
        List<OrderedText> wrappedLines = wrappedDescription(module, descriptionWidth);
        int lineSpacing = scaled(12);
        int descriptionHeight = descriptionHeight(wrappedLines.size());
        context.drawCenteredTextWithShadow(textRenderer, module.displayName(), width / 2, headerHeight() + scaled(16), 0xFFFFFFFF);
        renderPanel(context, width / 2 - descriptionWidth / 2, descriptionY, descriptionWidth, descriptionHeight);

        int lineY = descriptionY + (descriptionHeight - wrappedLines.size() * scaled(10)) / 2;
        for (OrderedText line : wrappedLines) {
            context.drawCenteredTextWithShadow(textRenderer, line, width / 2, lineY, 0xFFDDDDDD);
            lineY += lineSpacing;
        }
    }

    /** Sets up a naturally spaced module form that can scroll without moving its footer buttons. */
    protected void configureScrollableContent(DMLSModule module, int contentHeight) {
        int descriptionWidth = Math.min(scaled(360), width - scaled(32));
        int descriptionBottom = headerHeight() + scaled(34)
                + descriptionHeight(wrappedDescription(module, descriptionWidth).size());
        configureScrollableContent(descriptionBottom + scaled(10), contentHeight);
    }

    /** Configures scrolling for non-module settings screens. */
    protected void configureScrollableContent(int viewportTop, int contentHeight) {
        configureScrollableContent(viewportTop, height - FOOTER_TOP_OFFSET - scaled(8), contentHeight);
    }

    /** Configures scrolling with a screen-specific bottom edge. */
    protected void configureScrollableContent(int viewportTop, int viewportBottom, int contentHeight) {
        scrollableWidgets.clear();
        contentViewportTop = viewportTop;
        contentViewportBottom = Math.max(contentViewportTop + 1, viewportBottom);
        contentViewportHeaderOffset = viewportTop - headerHeight();
        contentViewportTracksHeader = true;
        updateScrollableContentHeight(contentHeight);
    }

    /** Updates a form's scroll range when custom-drawn content changes height. */
    protected void updateScrollableContentHeight(int contentHeight) {
        configuredContentHeight = contentHeight;
        maxContentScroll = Math.max(0, contentHeight - (contentViewportBottom - contentViewportTop));
        contentScrollOffset = Math.clamp(contentScrollOffset, 0, maxContentScroll);
        updateScrollableWidgets();
    }

    protected int contentY(int offset) {
        return contentViewportTop + offset - contentScrollOffset;
    }

    protected <T extends ClickableWidget> T addScrollableChild(T widget, int offset) {
        scrollableWidgets.add(new ScrollableWidget(widget, offset));
        T child = addDrawableChild(widget);
        updateScrollableWidgets();
        return child;
    }

    /** Marks a control as available only while connected to a configured allowed server. */
    protected <T extends ClickableWidget> T registerCommandControl(T widget) {
        return registerCommandControl(widget, () -> true);
    }

    /** Adds a local eligibility condition on top of the configured allowed-server requirement. */
    protected <T extends ClickableWidget> T registerCommandControl(T widget, BooleanSupplier locallyEnabled) {
        commandControls.add(new CommandControl(widget, locallyEnabled));
        refreshCommandControls();
        return widget;
    }

    /** Shared connection predicate for non-button controls, such as moderation chat composers. */
    protected boolean isCommandServerAllowed() {
        return ServerGuard.check(client).allowed();
    }

    private void refreshCommandControls() {
        boolean serverAllowed = isCommandServerAllowed();
        for (CommandControl control : commandControls) {
            control.widget().active = serverAllowed && control.locallyEnabled().getAsBoolean();
            control.widget().setTooltip(serverAllowed ? null : STONEWORKS_CONNECTION_TOOLTIP);
        }
    }

    /** Keeps a conditionally available scrolling control hidden without losing its scroll registration. */
    protected void setScrollableChildEnabled(ClickableWidget widget, boolean enabled) {
        if (enabled) {
            disabledScrollableWidgets.remove(widget);
        } else {
            disabledScrollableWidgets.add(widget);
        }
        updateScrollableWidgets();
    }

    /** Registers a dropdown and ensures its expanded list renders and receives input above other controls. */
    protected <T> DropdownWidget<T> addDropdownChild(DropdownWidget<T> dropdown) {
        dropdown.setDropdownBounds(scaled(4), height - scaled(4));
        dropdownWidgets.add(dropdown);
        return addDrawableChild(dropdown);
    }

    /** Scrollable-content counterpart to {@link #addDropdownChild(DropdownWidget)}. */
    protected <T> DropdownWidget<T> addScrollableDropdownChild(DropdownWidget<T> dropdown, int offset) {
        dropdown.setDropdownBounds(contentViewportTop, contentViewportBottom);
        dropdownWidgets.add(dropdown);
        return addScrollableChild(dropdown, offset);
    }

    protected boolean isContentVisible(int y, int elementHeight) {
        return y < contentViewportBottom && y + elementHeight > contentViewportTop;
    }

    /** Clips custom-drawn scrolling content while still allowing partially visible elements to render. */
    protected void beginContentScissor(DrawContext context) {
        context.enableScissor(0, contentViewportTop, width, contentViewportBottom);
    }

    protected void endContentScissor(DrawContext context) {
        context.disableScissor();
    }

    /** Returns the first fixed-height content row that can intersect the viewport. */
    protected int firstVisibleContentIndex(int rowHeight) {
        return Math.max(0, contentScrollOffset / Math.max(1, rowHeight));
    }

    /** Returns the exclusive end index for fixed-height rows that can intersect the viewport. */
    protected int visibleContentEndIndex(int rowHeight, int rowCount) {
        int safeRowHeight = Math.max(1, rowHeight);
        int viewportHeight = contentViewportBottom - contentViewportTop;
        return Math.min(rowCount, (contentScrollOffset + viewportHeight + safeRowHeight - 1) / safeRowHeight + 1);
    }

    protected void resetContentScroll() {
        contentScrollOffset = 0;
        updateScrollableWidgets();
    }

    protected void scrollContentToBottom() {
        contentScrollOffset = maxContentScroll;
        updateScrollableWidgets();
    }

    protected boolean isContentScrolledToTop() {
        return contentScrollOffset <= 0;
    }

    protected boolean isContentScrolledToBottom() {
        return contentScrollOffset >= maxContentScroll;
    }

    protected int contentScrollOffset() {
        return contentScrollOffset;
    }

    protected int maxContentScroll() {
        return maxContentScroll;
    }

    /** Current animated header height, from the normal header down to the footer-sized compact header. */
    protected int headerHeight() {
        return interpolate(HEADER_HEIGHT, COMPACT_HEADER_HEIGHT, easedHeaderCompactProgress());
    }

    /** Small windows temporarily force the compact header without changing the persisted preference. */
    protected boolean isHeaderCollapseForced() {
        return width < MIN_EXPANDED_HEADER_WIDTH || height < MIN_EXPANDED_HEADER_HEIGHT;
    }

    /** Header height after the current forced/preference state has finished transitioning. */
    protected int settledHeaderHeight() {
        return shouldCollapseHeader() ? COMPACT_HEADER_HEIGHT : HEADER_HEIGHT;
    }

    /** Applies scrollbar movement with hysteresis so small direction changes do not keep toggling the header. */
    protected void updateHeaderForScrollChange(int previousOffset, int newOffset) {
        if (newOffset > previousOffset) {
            downwardHeaderScrollDistance += newOffset - previousOffset;
            if (downwardHeaderScrollDistance >= HEADER_COMPACT_SCROLL_THRESHOLD) {
                compactHeaderForScroll = true;
            }
        } else if (newOffset < previousOffset) {
            downwardHeaderScrollDistance = 0;
            if (newOffset <= HEADER_EXPAND_SCROLL_THRESHOLD) {
                compactHeaderForScroll = false;
            }
        }
    }

    /** Snaps to a newly selected preference before rebuilding a settings screen's fixed controls. */
    protected void applyHeaderPreferenceImmediately() {
        compactHeaderForScroll = false;
        downwardHeaderScrollDistance = 0;
        headerCompactProgress = shouldCollapseHeader() ? 1.0F : 0.0F;
        lastHeaderAnimationNanos = System.nanoTime();
        refreshHeaderTrackedViewport();
    }

    protected void setContentScrollOffset(int offset) {
        contentScrollOffset = Math.clamp(offset, 0, maxContentScroll);
        updateScrollableWidgets();
    }

    protected boolean isDraggingContentScrollbar() {
        return draggingContentScrollbar;
    }

    protected void stopDraggingContentScrollbar() {
        draggingContentScrollbar = false;
    }

    private List<OrderedText> wrappedDescription(DMLSModule module, int descriptionWidth) {
        return module.description().stream()
                .flatMap(line -> textRenderer.wrapLines(line, descriptionWidth - scaled(16)).stream())
                .toList();
    }

    private int descriptionHeight(int lineCount) {
        return Math.max(scaled(48), lineCount * scaled(12) + scaled(20));
    }

    private void updateScrollableWidgets() {
        for (ScrollableWidget entry : scrollableWidgets) {
            ClickableWidget widget = entry.widget();
            widget.setY(contentY(entry.offset()));
            if (widget instanceof DropdownWidget<?> dropdown) {
                dropdown.setDropdownBounds(contentViewportTop, contentViewportBottom);
            }
            widget.visible = !disabledScrollableWidgets.contains(widget)
                    && isContentVisible(widget.getY(), widget.getHeight());
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        refreshCommandControls();
        // Screen renders all drawable children together. Hide scrolling children for that pass, then
        // render them separately through the viewport scissor so partial widgets are cut smoothly.
        for (ScrollableWidget entry : scrollableWidgets) {
            entry.widget().visible = false;
        }
        super.render(context, mouseX, mouseY, delta);
        int clippedMouseX = mouseY >= contentViewportTop && mouseY < contentViewportBottom
                ? mouseX : Integer.MIN_VALUE;
        int clippedMouseY = mouseY >= contentViewportTop && mouseY < contentViewportBottom
                ? mouseY : Integer.MIN_VALUE;
        context.enableScissor(0, contentViewportTop, width, contentViewportBottom);
        for (ScrollableWidget entry : scrollableWidgets) {
            ClickableWidget widget = entry.widget();
            widget.visible = !disabledScrollableWidgets.contains(widget)
                    && isContentVisible(widget.getY(), widget.getHeight());
            if (widget.visible) widget.render(context, clippedMouseX, clippedMouseY, delta);
        }
        context.disableScissor();
        if (maxContentScroll > 0) {
            int trackX = contentScrollbarX();
            int trackTop = contentScrollbarTop();
            int trackHeight = Math.max(1, contentScrollbarBottom() - trackTop);
            int viewportHeight = contentViewportBottom - contentViewportTop;
            int thumbHeight = scrollbarThumbHeight(trackHeight, viewportHeight, viewportHeight + maxContentScroll);
            int thumbY = trackTop + contentScrollOffset * (trackHeight - thumbHeight) / maxContentScroll;
            renderVanillaScrollbar(context, trackX, trackTop, trackHeight, thumbY, thumbHeight);
        }
        for (DropdownWidget<?> dropdown : dropdownWidgets) {
            dropdown.renderDropdown(context, mouseX, mouseY, delta);
        }
        if (accountWidget != null) {
            accountWidget.renderShelf(context, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        for (DropdownWidget<?> dropdown : dropdownWidgets) {
            if (dropdown.isDropdownOpen()
                    && dropdown.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)) {
                return true;
            }
        }
        if (maxContentScroll > 0 && mouseY >= contentViewportTop && mouseY < contentViewportBottom) {
            int previousOffset = contentScrollOffset;
            contentScrollOffset = Math.clamp(contentScrollOffset - (int) (verticalAmount * scaled(24)), 0, maxContentScroll);
            updateHeaderForScrollChange(previousOffset, contentScrollOffset);
            updateScrollableWidgets();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        refreshCommandControls();
        if (handleDiscordAccountClick(click, doubled)) {
            return true;
        }
        for (DropdownWidget<?> dropdown : dropdownWidgets) {
            if (dropdown.isDropdownOpen() && dropdown.mouseClicked(click, doubled)) {
                setFocused(dropdown);
                return true;
            }
        }
        if (maxContentScroll > 0 && click.button() == 0
                && click.x() >= contentScrollbarX() && click.x() <= contentScrollbarX() + SCROLLBAR_WIDTH
                && click.y() >= contentScrollbarTop() && click.y() < contentScrollbarBottom()) {
            draggingContentScrollbar = true;
            int previousOffset = contentScrollOffset;
            updateContentScrollFromMouse(click.y());
            updateHeaderForScrollChange(previousOffset, contentScrollOffset);
            return true;
        }
        if (click.y() >= contentViewportTop && click.y() < contentViewportBottom) {
            return super.mouseClicked(click, doubled);
        }
        for (ScrollableWidget entry : scrollableWidgets) {
            entry.widget().visible = false;
        }
        boolean handled = super.mouseClicked(click, doubled);
        updateScrollableWidgets();
        return handled;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        refreshCommandControls();
        return super.keyPressed(input);
    }

    @Override
    public boolean mouseDragged(Click click, double deltaX, double deltaY) {
        for (DropdownWidget<?> dropdown : dropdownWidgets) {
            if (dropdown.mouseDragged(click, deltaX, deltaY)) {
                return true;
            }
        }
        if (draggingContentScrollbar) {
            int previousOffset = contentScrollOffset;
            updateContentScrollFromMouse(click.y());
            updateHeaderForScrollChange(previousOffset, contentScrollOffset);
            return true;
        }
        return super.mouseDragged(click, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(Click click) {
        for (DropdownWidget<?> dropdown : dropdownWidgets) {
            if (dropdown.isDraggingScrollbar() && dropdown.mouseReleased(click)) {
                return true;
            }
        }
        draggingContentScrollbar = false;
        return super.mouseReleased(click);
    }

    @Override
    protected void clearChildren() {
        super.clearChildren();
        scrollableWidgets.clear();
        disabledScrollableWidgets.clear();
        dropdownWidgets.clear();
        commandControls.clear();
        contentViewportTracksHeader = false;
        discordAccountInitialized = false;
        accountWidget = null;
        releaseAccountTexture();
    }

    protected int contentScrollbarX() {
        int contentWidth = Math.min(scaled(360), width - scaled(48));
        return width / 2 + contentWidth / 2 + scaled(7);
    }

    protected int contentScrollbarTop() {
        return contentViewportTop;
    }

    protected int contentScrollbarBottom() {
        return contentViewportBottom;
    }

    /** Gives an open account shelf priority over screen-specific controls. */
    protected boolean handleDiscordAccountClick(Click click, boolean doubled) {
        if (accountWidget == null || !accountWidget.isShelfOpen()) return false;
        if (!accountWidget.mouseClicked(click, doubled)) return false;
        setFocused(accountWidget);
        return true;
    }

    /** Reloads the shared account control after the options screen links or invalidates an account. */
    protected void refreshDiscordAccount() {
        clearDiscordAccountDisplay();
        discordAccountInitialized = false;
        initializeDiscordAccount();
    }

    private void initializeDiscordAccount() {
        if (discordAccountInitialized) return;
        discordAccountInitialized = true;
        if (client == null || client.getSession().getUuidOrNull() == null) return;
        java.util.UUID minecraftUuid = client.getSession().getUuidOrNull();
        String token = DiscordLinkTokenStore.load(minecraftUuid).orElse("");
        if (token.isEmpty()) return;

        showCachedDiscordAccount(minecraftUuid);
        if (ACCOUNT_LINK_CHECKS.contains(minecraftUuid)) return;
        if (ACCOUNT_LINK_CHECKS_IN_PROGRESS.add(minecraftUuid)) {
            checkDiscordAccount(minecraftUuid, token);
        }
    }

    private void checkDiscordAccount(java.util.UUID minecraftUuid, String token) {
        DiscordLinkService.checkStatus(minecraftUuid, token).thenAccept(result -> client.execute(() -> {
            if (result.status() == DiscordLinkService.LinkStatus.LINKED) {
                ACCOUNT_LINK_CHECKS_IN_PROGRESS.remove(minecraftUuid);
                ACCOUNT_LINK_CHECKS.add(minecraftUuid);
                DiscordLinkAvailability.markLinked(minecraftUuid);
                if (result.profile() != null) {
                    DiscordAccountProfileStore.save(result.profile());
                    DiscordAvatarCache.ensureCached(result.profile());
                }
                if (client.currentScreen instanceof DMLSMenuScreen screen) {
                    screen.showCachedDiscordAccount(minecraftUuid);
                }
                return;
            }
            if (result.status() == DiscordLinkService.LinkStatus.INVALID_TOKEN
                    || result.status() == DiscordLinkService.LinkStatus.EXPIRED) {
                clearStoredDiscordAccount(minecraftUuid);
                if (client.currentScreen instanceof DMLSMenuScreen screen) {
                    screen.clearDiscordAccountDisplay();
                }
                return;
            }
            ACCOUNT_LINK_CHECKS_IN_PROGRESS.remove(minecraftUuid);
            if (result.status() != DiscordLinkService.LinkStatus.RATE_LIMITED
                    && result.status() != DiscordLinkService.LinkStatus.TIMEOUT
                    && result.status() != DiscordLinkService.LinkStatus.NETWORK_ERROR
                    && result.status() != DiscordLinkService.LinkStatus.SERVICE_ERROR
                    && result.status() != DiscordLinkService.LinkStatus.MALFORMED_RESPONSE) {
                ACCOUNT_LINK_CHECKS.add(minecraftUuid);
            }
        }));
    }

    private void showCachedDiscordAccount(java.util.UUID minecraftUuid) {
        if (accountWidget != null || client == null) return;
        DiscordAccountProfileStore.load(minecraftUuid).ifPresent(profile -> {
            accountAvatarTexture = DiscordAvatarCache.loadTexture(client, profile);
            int minimumWidth = scaled(70);
            int maximumWidth = Math.max(minimumWidth,
                    Math.min(scaled(210), width / 2 - scaled(110)));
            int desiredWidth = 32 + textRenderer.getWidth(profile.discordUsername());
            int widgetWidth = Math.clamp(desiredWidth, minimumWidth, maximumWidth);
            accountWidget = addDrawableChild(new DiscordAccountWidget(
                    scaled(8), Math.max(0, (headerHeight() - DiscordAccountWidget.HEIGHT) / 2),
                    widgetWidth, profile.discordUsername(), accountAvatarTexture,
                    this::unlinkDiscordAccount));
            if (accountAvatarTexture == null) {
                DiscordAccountWidget targetWidget = accountWidget;
                DiscordAvatarCache.ensureCached(profile).thenAccept(path -> {
                    if (path == null) return;
                    client.execute(() -> {
                        if (client.currentScreen != this || accountWidget != targetWidget) return;
                        accountAvatarTexture = DiscordAvatarCache.loadTexture(client, profile);
                        accountWidget.setAvatarTexture(accountAvatarTexture);
                    });
                });
            }
        });
    }

    private void unlinkDiscordAccount() {
        if (client == null || accountWidget == null || client.getSession().getUuidOrNull() == null) return;
        java.util.UUID minecraftUuid = client.getSession().getUuidOrNull();
        String token = DiscordLinkTokenStore.load(minecraftUuid).orElse("");
        if (token.isEmpty()) {
            clearLocalDiscordAccount(minecraftUuid);
            return;
        }

        DiscordAccountWidget targetWidget = accountWidget;
        targetWidget.setTooltip(null);
        targetWidget.setUnlinking(true);
        attemptDiscordUnlink(minecraftUuid, token, targetWidget, 0);
    }

    private void attemptDiscordUnlink(java.util.UUID minecraftUuid, String token,
                                      DiscordAccountWidget targetWidget, int attempt) {
        DiscordLinkService.unlink(token).thenAccept(result -> client.execute(() -> {
            if (accountWidget != targetWidget) return;
            if (result.unlinkedLocally()) {
                clearLocalDiscordAccount(minecraftUuid);
                return;
            }
            boolean retriable = result.status() != DiscordLinkService.UnlinkStatus.RATE_LIMITED;
            if (retriable && attempt < 4) {
                int retryDelaySeconds = 2 << attempt;
                CompletableFuture.delayedExecutor(retryDelaySeconds, TimeUnit.SECONDS).execute(() ->
                        client.execute(() -> {
                            if (accountWidget == targetWidget) {
                                attemptDiscordUnlink(minecraftUuid, token, targetWidget, attempt + 1);
                            }
                        }));
                return;
            }
            targetWidget.setUnlinking(false);
            targetWidget.setTooltip(Tooltip.of(Text.translatable(
                    result.status() == DiscordLinkService.UnlinkStatus.RATE_LIMITED
                            ? "dmls.account.unlink.error.rate_limited"
                            : "dmls.account.unlink.error.service")));
        }));
    }

    private void clearLocalDiscordAccount(java.util.UUID minecraftUuid) {
        clearStoredDiscordAccount(minecraftUuid);
        clearDiscordAccountDisplay();
    }

    private static void clearStoredDiscordAccount(java.util.UUID minecraftUuid) {
        ACCOUNT_LINK_CHECKS.remove(minecraftUuid);
        ACCOUNT_LINK_CHECKS_IN_PROGRESS.remove(minecraftUuid);
        DiscordAccountProfileStore.load(minecraftUuid).ifPresent(DiscordAvatarCache::deleteCached);
        DiscordLinkTokenStore.delete(minecraftUuid);
        DiscordAccountProfileStore.delete(minecraftUuid);
    }

    private void clearDiscordAccountDisplay() {
        releaseAccountTexture();
        if (accountWidget != null) {
            accountWidget.visible = false;
            remove(accountWidget);
        }
        accountWidget = null;
    }

    private void releaseAccountTexture() {
        if (client != null && accountAvatarTexture != null) {
            client.getTextureManager().destroyTexture(accountAvatarTexture);
        }
        accountAvatarTexture = null;
    }

    private void updateContentScrollFromMouse(double mouseY) {
        int trackTop = contentScrollbarTop();
        int trackHeight = Math.max(1, contentScrollbarBottom() - trackTop);
        int viewportHeight = contentViewportBottom - contentViewportTop;
        int thumbHeight = scrollbarThumbHeight(trackHeight, viewportHeight, viewportHeight + maxContentScroll);
        int track = Math.max(1, trackHeight - thumbHeight);
        double relative = Math.clamp((int) (mouseY - trackTop - thumbHeight / 2.0), 0, track);
        contentScrollOffset = Math.clamp((int) Math.round(relative * maxContentScroll / track), 0, maxContentScroll);
        updateScrollableWidgets();
    }

    private void updateHeaderAnimation() {
        long now = System.nanoTime();
        long elapsed = Math.max(0L, now - lastHeaderAnimationNanos);
        lastHeaderAnimationNanos = now;
        if (isHeaderCollapseForced()) {
            headerCompactProgress = 1.0F;
            return;
        }
        float target = shouldCollapseHeader() ? 1.0F : 0.0F;
        float step = Math.min(1.0F, elapsed / (float) HEADER_ANIMATION_NANOS);
        if (headerCompactProgress < target) {
            headerCompactProgress = Math.min(target, headerCompactProgress + step);
        } else if (headerCompactProgress > target) {
            headerCompactProgress = Math.max(target, headerCompactProgress - step);
        }
    }

    private void refreshHeaderTrackedViewport() {
        if (!contentViewportTracksHeader) return;
        int animatedTop = headerHeight() + contentViewportHeaderOffset;
        if (animatedTop == contentViewportTop) return;
        contentViewportTop = animatedTop;
        updateScrollableContentHeight(configuredContentHeight);
    }

    private float easedHeaderCompactProgress() {
        float progress = Math.clamp(headerCompactProgress, 0.0F, 1.0F);
        return progress * progress * (3.0F - 2.0F * progress);
    }

    private static int interpolate(int from, int to, float progress) {
        return Math.round(from + (to - from) * progress);
    }

    private boolean shouldCollapseHeader() {
        if (isHeaderCollapseForced()) return true;
        return switch (DMLSConfig.headerBehavior()) {
            case ALWAYS_BIG -> false;
            case ALWAYS_SMALL -> true;
            case ON_SCROLL -> compactHeaderForScroll;
        };
    }

    protected static int scrollbarThumbHeight(int viewportHeight, int contentHeight) {
        return Math.clamp(viewportHeight * viewportHeight / Math.max(1, contentHeight),
                32, Math.max(32, viewportHeight - 8));
    }

    protected static int scrollbarThumbHeight(int trackHeight, int viewportHeight, int contentHeight) {
        return Math.clamp(trackHeight * viewportHeight / Math.max(1, contentHeight),
                32, Math.max(32, trackHeight - 8));
    }

    public static void renderVanillaScrollbar(DrawContext context, int x, int y, int height,
                                               int thumbY, int thumbHeight) {
        context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, SCROLLER_BACKGROUND,
                x, y, SCROLLBAR_WIDTH, height);
        context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, SCROLLER,
                x, thumbY, SCROLLBAR_WIDTH, thumbHeight);
    }

    protected int pairedButtonWidth() {
        return Math.min(MAX_PAIRED_BUTTON_WIDTH, (width - PAIRED_BUTTON_MARGIN * 3) / 2);
    }

    protected int leftPairedButtonX() {
        return width / 2 - pairedButtonWidth() - PAIRED_BUTTON_MARGIN / 2;
    }

    protected int rightPairedButtonX() {
        return width / 2 + PAIRED_BUTTON_MARGIN / 2;
    }

    protected int footerButtonY() {
        return height - 31;
    }

    protected static int scaled(int value) {
        return Math.round(value * UI_SCALE);
    }

    /** Closes the whole DMLS menu stack and returns directly to gameplay. */
    protected void closeToGame() {
        client.setScreen(null);
    }

    private record ScrollableWidget(ClickableWidget widget, int offset) {
    }

    private record CommandControl(ClickableWidget widget, BooleanSupplier locallyEnabled) {
    }

    @Override
    public void removed() {
        releaseAccountTexture();
        super.removed();
    }

    @Override
    public void close() {
        client.setScreen(parent);
    }
}
