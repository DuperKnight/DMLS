package com.duperknight.client.gui;

import com.duperknight.client.accountlink.DiscordLinkService;
import com.duperknight.client.accountlink.DiscordLinkAvailability;
import com.duperknight.client.accountlink.DiscordLinkTokenStore;
import com.duperknight.client.accountlink.DiscordAccountProfile;
import com.duperknight.client.accountlink.DiscordAccountProfileStore;
import com.duperknight.client.accountlink.DiscordAvatarCache;
import com.duperknight.client.gui.widgets.DropdownWidget;
import com.duperknight.client.gui.widgets.LinkCodeWidget;
import com.duperknight.client.modules.DepartmentRank;
import com.duperknight.client.modules.StaffDepartment;
import com.duperknight.client.modules.StaffRank;
import com.duperknight.client.utils.DMLSConfig;
import com.duperknight.client.utils.HeaderBehavior;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/** Persisted mod-wide settings that do not belong to an individual module. */
public final class DMLSOptionsScreen extends DMLSMenuScreen {
    private static final long STATUS_DURATION_MILLIS = 5_000L;
    private static final int STATUS_COLOR = 0xFFDDDDDD;
    private static final int ERROR_COLOR = 0xFFFF7777;
    private static final int MINIMUM_CONTENT_HEIGHT = STANDARD_BUTTON_HEIGHT * 4 + scaled(165);
    private int rankX;
    private int rankOffset;
    private int rankWidth;
    private int departmentsX;
    private int departmentsOffset;
    private int departmentsWidth;
    private int dividerOffset;
    private int departmentDropdownOffset;
    private int settingsOffset;
    private int integrationsDividerOffset;
    private int discordLinkOffset;
    private int linkCodeOffset;
    private int contentBottomPadding;
    private int baseContentHeight;
    private Button discordLinkButton;
    private LinkCodeWidget linkCodeWidget;
    private String linkCode = "";
    private String linkClientToken = "";
    private boolean linkCodeRevealed;
    private Component linkStatus;
    private long linkStatusUntilMillis;
    private int linkStatusColor = STATUS_COLOR;
    private boolean pendingStatusShown;
    private boolean storedTokenLoaded;
    private boolean linked;
    private int requestGeneration;
    private int statusGeneration;

    public DMLSOptionsScreen(Screen parent) {
        super(Component.translatable("dmls.title.options"), parent);
    }

    @Override
    protected void init() {
        rankWidth = Math.min(scaled(240), width - scaled(32));
        rankX = width / 2 - rankWidth / 2;
        int contentTop = headerHeight() + scaled(29);
        int contentBottom = height - FOOTER_TOP_OFFSET - scaled(8);
        int settledContentTop = settledHeaderHeight() + scaled(29);
        int topPadding = Math.max(0, (contentBottom - settledContentTop - MINIMUM_CONTENT_HEIGHT) / 2);
        contentBottomPadding = topPadding;
        baseContentHeight = MINIMUM_CONTENT_HEIGHT + topPadding * 2;
        rankOffset = topPadding;
        boolean showAllowedServers = DMLSConfig.staffRank().isAtLeast(StaffRank.SENIOR_MODERATOR);

        departmentsWidth = Math.min(scaled(480), width - scaled(32));
        departmentsX = width / 2 - departmentsWidth / 2;
        settingsOffset = rankOffset + STANDARD_BUTTON_HEIGHT + scaled(12);
        dividerOffset = settingsOffset + STANDARD_BUTTON_HEIGHT + scaled(18);
        departmentsOffset = dividerOffset + scaled(14);
        departmentDropdownOffset = departmentsOffset + scaled(15);
        integrationsDividerOffset = departmentDropdownOffset + STANDARD_BUTTON_HEIGHT + scaled(18);
        discordLinkOffset = integrationsDividerOffset + scaled(14);
        linkCodeOffset = discordLinkOffset + STANDARD_BUTTON_HEIGHT + scaled(8);
        configureScrollableContent(contentTop, contentBottom, baseContentHeight);

        int gap = scaled(8);
        int departmentWidth = (departmentsWidth - gap * 2) / 3;

        int index = 0;
        for (StaffDepartment department : StaffDepartment.values()) {
            int x = departmentsX + index * (departmentWidth + gap);
            addScrollableDropdownChild(DropdownWidget.builder(
                            department.displayName(),
                            DepartmentRank.optionsFor(department),
                            DMLSConfig.departmentRank(department),
                            DepartmentRank::displayName,
                            (dropdown, value) -> DMLSConfig.setDepartmentRank(department, value))
                    .dimensions(x, contentY(departmentDropdownOffset), departmentWidth, STANDARD_BUTTON_HEIGHT)
                    .maxVisibleRows(4)
                    .showOptionLabel(false)
                    .build(), departmentDropdownOffset);
            index++;
        }

        int settingGap = scaled(8);
        int behaviorX = rankX;
        int behaviorWidth = rankWidth;
        if (showAllowedServers) {
            int settingWidth = (departmentsWidth - settingGap) / 2;
            addScrollableChild(Button.builder(Component.translatable("dmls.option.allowed_servers"),
                            button -> minecraft.gui.setScreen(new AllowedServersScreen(this)))
                    .bounds(departmentsX, contentY(settingsOffset), settingWidth, STANDARD_BUTTON_HEIGHT).build(),
                    settingsOffset);
            behaviorX = departmentsX + settingWidth + settingGap;
            behaviorWidth = settingWidth;
        }
        boolean collapseForced = isHeaderCollapseForced();
        DropdownWidget<HeaderBehavior> behaviorDropdown = DropdownWidget.builder(
                        Component.translatable("dmls.option.header_behavior"),
                        HeaderBehavior.options(),
                        collapseForced ? HeaderBehavior.ALWAYS_SMALL : DMLSConfig.headerBehavior(),
                        HeaderBehavior::displayName,
                        (dropdown, value) -> {
                            if (DMLSConfig.setHeaderBehavior(value)) {
                                applyHeaderPreferenceImmediately();
                                rebuildWidgets();
                            } else {
                                dropdown.setValue(DMLSConfig.headerBehavior());
                            }
                        })
                .dimensions(behaviorX, contentY(settingsOffset), behaviorWidth, STANDARD_BUTTON_HEIGHT)
                .maxVisibleRows(3)
                .showOptionLabel(true)
                .build();
        behaviorDropdown.active = !collapseForced;
        addScrollableDropdownChild(behaviorDropdown, settingsOffset);
        discordLinkButton = addScrollableChild(Button.builder(
                        discordLinkButtonText(),
                        button -> requestDiscordLink())
                .bounds(rankX, contentY(discordLinkOffset), rankWidth, STANDARD_BUTTON_HEIGHT).build(),
                discordLinkOffset);
        discordLinkButton.active = !linked;
        linkCodeWidget = addScrollableChild(new LinkCodeWidget(
                rankX, contentY(linkCodeOffset), rankWidth, STANDARD_BUTTON_HEIGHT, linkCode, linkCodeRevealed,
                () -> linkCodeRevealed = true,
                () -> {
                    setLinkStatus(Component.translatable("dmls.option.discord_link.copied"), STATUS_COLOR);
                }), linkCodeOffset);
        setScrollableChildEnabled(linkCodeWidget, !linked && !linkCode.isEmpty());
        addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, button -> onClose())
                .bounds(width / 2 - scaled(75), footerButtonY(), scaled(150), STANDARD_BUTTON_HEIGHT).build());
        addRenderableWidget(Button.builder(Component.translatable("dmls.feedback.open"),
                        button -> minecraft.gui.setScreen(new FeedbackScreen(this)))
                .bounds(width - scaled(132), footerButtonY(), scaled(116), STANDARD_BUTTON_HEIGHT).build());

        if (!storedTokenLoaded) {
            storedTokenLoaded = true;
            loadStoredLink();
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        renderMenuBackground(context);
        context.centeredText(font, title, width / 2, headerHeight() + scaled(16), 0xFFFFFFFF);

        if (linkStatus != null && System.currentTimeMillis() >= linkStatusUntilMillis) {
            clearLinkStatus();
        }
        List<FormattedCharSequence> statusLines = linkStatus == null
                ? List.of() : font.split(linkStatus, departmentsWidth);
        boolean showLinkCode = !linked && !linkCode.isEmpty();
        int statusOffset = linkCodeOffset + (showLinkCode
                ? STANDARD_BUTTON_HEIGHT + scaled(7) : scaled(2));
        int requiredContentHeight = statusLines.isEmpty() ? baseContentHeight : Math.max(baseContentHeight,
                statusOffset + statusLines.size() * scaled(11) + scaled(8) + contentBottomPadding);
        updateScrollableContentHeight(requiredContentHeight);

        beginContentScissor(context);
        int rankY = contentY(rankOffset);
        renderPanel(context, rankX, rankY, rankWidth, STANDARD_BUTTON_HEIGHT);
        context.centeredText(font, DMLSConfig.staffRank().displayName(),
                width / 2, rankY + (STANDARD_BUTTON_HEIGHT - font.lineHeight) / 2 + scaled(1), 0xFFFFFFFF);

        renderSectionDivider(context, contentY(dividerOffset), Component.translatable("dmls.option.departments"));

        int gap = scaled(8);
        int departmentWidth = (departmentsWidth - gap * 2) / 3;
        int index = 0;
        for (StaffDepartment department : StaffDepartment.values()) {
            int centerX = departmentsX + index * (departmentWidth + gap) + departmentWidth / 2;
            context.centeredText(font, department.displayName(), centerX,
                    contentY(departmentsOffset), 0xFFDDDDDD);
            index++;
        }
        renderSectionDivider(context, contentY(integrationsDividerOffset), Component.translatable("dmls.option.integrations"));
        if (!statusLines.isEmpty()) {
            int statusY = contentY(statusOffset);
            for (FormattedCharSequence line : statusLines) {
                if (isContentVisible(statusY, font.lineHeight)) {
                    context.centeredText(font, line, width / 2, statusY, linkStatusColor);
                }
                statusY += scaled(11);
            }
        }
        endContentScissor(context);
        if (linkCodeWidget != null) {
            setScrollableChildEnabled(linkCodeWidget, !linked && !linkCode.isEmpty());
        }
        super.extractRenderState(context, mouseX, mouseY, delta);
    }

    private void renderSectionDivider(GuiGraphicsExtractor context, int y, Component label) {
        int titleHalfWidth = font.width(label) / 2;
        int titlePadding = scaled(8);
        context.fill(departmentsX, y,
                width / 2 - titleHalfWidth - titlePadding, y + 1, PANEL_BORDER_COLOR);
        context.fill(width / 2 + titleHalfWidth + titlePadding, y,
                departmentsX + departmentsWidth, y + 1, PANEL_BORDER_COLOR);
        context.centeredText(font, label,
                width / 2, y - font.lineHeight / 2, 0xFFFFFFFF);
    }

    @Override
    protected int contentScrollbarX() {
        return departmentsX + departmentsWidth + scaled(7);
    }

    private void requestDiscordLink() {
        if (minecraft == null || minecraft.getUser().getProfileId() == null) {
            setLinkStatus(Component.translatable("dmls.option.discord_link.error.identity"), ERROR_COLOR);
            return;
        }

        statusGeneration++;
        pendingStatusShown = false;
        int generation = ++requestGeneration;
        discordLinkButton.active = false;
        discordLinkButton.setMessage(Component.translatable("dmls.option.discord_link.requesting"));
        setLinkStatus(Component.translatable("dmls.option.discord_link.requesting_hint"), STATUS_COLOR);

        var minecraftUuid = minecraft.getUser().getProfileId();
        DiscordLinkService.request(minecraftUuid, minecraft.getUser().getName())
                .thenApply(result -> new StoredLinkResult(result,
                        !result.succeeded() || DiscordLinkTokenStore.save(minecraftUuid, result.clientToken())))
                .thenAccept(result -> minecraft.execute(() -> applyLinkResult(generation, result)));
    }

    private void applyLinkResult(int generation, StoredLinkResult storedResult) {
        if (generation != requestGeneration) return;
        discordLinkButton.active = true;
        DiscordLinkService.Result result = storedResult.result();

        if (result.succeeded()) {
            if (!storedResult.stored()) {
                linkCode = "";
                linkClientToken = "";
                linkCodeRevealed = false;
                setScrollableChildEnabled(linkCodeWidget, false);
                discordLinkButton.setMessage(Component.translatable("dmls.option.discord_link"));
                setLinkStatus(Component.translatable("dmls.option.discord_link.error.storage"), ERROR_COLOR);
                return;
            }
            linkCode = result.code();
            linkClientToken = result.clientToken();
            linked = false;
            clearCachedProfile();
            refreshDiscordAccount();
            linkCodeRevealed = false;
            linkCodeWidget.setCode(linkCode);
            setScrollableChildEnabled(linkCodeWidget, true);
            discordLinkButton.setMessage(Component.translatable("dmls.option.discord_link.new_code"));
            setLinkStatus(Component.translatable("dmls.option.discord_link.instructions"), STATUS_COLOR);
            pendingStatusShown = true;
            beginStatusCheck(++statusGeneration);
            return;
        }

        discordLinkButton.setMessage(discordLinkButtonText());
        Component error = switch (result.status()) {
            case RATE_LIMITED -> Component.translatable("dmls.option.discord_link.error.rate_limited");
            case TIMEOUT -> Component.translatable("dmls.option.discord_link.error.timeout");
            case NETWORK_ERROR -> Component.translatable("dmls.option.discord_link.error.network");
            case SERVICE_ERROR -> result.message().isBlank()
                    ? Component.translatable("dmls.option.discord_link.error.service")
                    : Component.translatable("dmls.option.discord_link.error.service_message", safeMessage(result.message()));
            default -> Component.translatable("dmls.option.discord_link.error.response");
        };
        setLinkStatus(error, ERROR_COLOR);
    }

    private void loadStoredLink() {
        if (minecraft == null || minecraft.getUser().getProfileId() == null) return;
        DiscordLinkTokenStore.load(minecraft.getUser().getProfileId()).ifPresent(token -> {
            linkClientToken = token;
            discordLinkButton.active = false;
            discordLinkButton.setMessage(Component.translatable("dmls.option.discord_link.checking"));
            clearLinkStatus();
            pendingStatusShown = false;
            beginStatusCheck(++statusGeneration);
        });
    }

    private void beginStatusCheck(int generation) {
        if (minecraft == null || minecraft.gui.screen() != this || generation != statusGeneration
                || linkClientToken.isEmpty() || minecraft.getUser().getProfileId() == null) {
            return;
        }

        var minecraftUuid = minecraft.getUser().getProfileId();
        DiscordLinkService.checkStatus(minecraftUuid, linkClientToken)
                .thenAccept(result -> minecraft.execute(() -> applyStatusResult(generation, result)));
    }

    private void applyStatusResult(int generation, DiscordLinkService.LinkStatusResult result) {
        if (minecraft == null || minecraft.gui.screen() != this || generation != statusGeneration) return;

        switch (result.status()) {
            case LINKED -> showLinked(result.profile());
            case PENDING -> {
                linked = false;
                discordLinkButton.active = true;
                discordLinkButton.setMessage(Component.translatable("dmls.option.discord_link.new_code"));
                if (!pendingStatusShown) {
                    clearCachedProfile();
                    refreshDiscordAccount();
                    setLinkStatus(Component.translatable(linkCode.isEmpty()
                            ? "dmls.option.discord_link.pending_without_code"
                            : "dmls.option.discord_link.instructions"), STATUS_COLOR);
                    pendingStatusShown = true;
                }
                scheduleStatusCheck(generation, result.pollAfterSeconds());
            }
            case INVALID_TOKEN -> clearInvalidToken("dmls.option.discord_link.error.invalid_token");
            case EXPIRED -> clearInvalidToken("dmls.option.discord_link.error.expired");
            case AUTHORIZATION_STALE -> {
                linked = false;
                setScrollableChildEnabled(linkCodeWidget, false);
                discordLinkButton.active = false;
                discordLinkButton.setMessage(Component.translatable("dmls.option.discord_link.stale"));
                setLinkStatus(Component.translatable("dmls.option.discord_link.error.stale"), ERROR_COLOR);
                scheduleStatusCheck(generation, 30);
            }
            case RATE_LIMITED -> retryStatusCheck(generation,
                    "dmls.option.discord_link.error.status_rate_limited", 60);
            case TIMEOUT -> retryStatusCheck(generation,
                    "dmls.option.discord_link.error.status_timeout", 15);
            case NETWORK_ERROR -> retryStatusCheck(generation,
                    "dmls.option.discord_link.error.status_network", 15);
            case SERVICE_ERROR, MALFORMED_RESPONSE -> retryStatusCheck(generation,
                    "dmls.option.discord_link.error.status_service", 15);
        }
    }

    private void showLinked(DiscordAccountProfile profile) {
        linked = true;
        if (minecraft != null) DiscordLinkAvailability.markLinked(minecraft.getUser().getProfileId());
        pendingStatusShown = false;
        linkCode = "";
        linkCodeRevealed = false;
        setScrollableChildEnabled(linkCodeWidget, false);
        discordLinkButton.active = false;
        discordLinkButton.setMessage(Component.translatable("dmls.option.discord_link.linked"));
        clearLinkStatus();
        if (profile != null) {
            DiscordAccountProfileStore.save(profile);
            DiscordAvatarCache.ensureCached(profile);
        }
        refreshDiscordAccount();
    }

    private void clearInvalidToken(String messageKey) {
        if (minecraft != null && minecraft.getUser().getProfileId() != null) {
            DiscordLinkTokenStore.delete(minecraft.getUser().getProfileId());
            clearCachedProfile();
        }
        statusGeneration++;
        linked = false;
        pendingStatusShown = false;
        linkCode = "";
        linkClientToken = "";
        linkCodeRevealed = false;
        setScrollableChildEnabled(linkCodeWidget, false);
        discordLinkButton.active = true;
        discordLinkButton.setMessage(Component.translatable("dmls.option.discord_link"));
        setLinkStatus(Component.translatable(messageKey), ERROR_COLOR);
        refreshDiscordAccount();
    }

    private void retryStatusCheck(int generation, String messageKey, int delaySeconds) {
        discordLinkButton.active = false;
        discordLinkButton.setMessage(Component.translatable("dmls.option.discord_link.checking"));
        setLinkStatus(Component.translatable(messageKey), ERROR_COLOR);
        scheduleStatusCheck(generation, delaySeconds);
    }

    private void scheduleStatusCheck(int generation, int delaySeconds) {
        var currentClient = minecraft;
        if (currentClient == null) return;
        CompletableFuture.delayedExecutor(Math.max(1, delaySeconds), TimeUnit.SECONDS).execute(() ->
                currentClient.execute(() -> beginStatusCheck(generation)));
    }

    private Component discordLinkButtonText() {
        if (linked) return Component.translatable("dmls.option.discord_link.linked");
        return Component.translatable(linkCode.isEmpty()
                ? "dmls.option.discord_link" : "dmls.option.discord_link.new_code");
    }

    private void setLinkStatus(Component status, int color) {
        linkStatus = status;
        linkStatusColor = color;
        linkStatusUntilMillis = System.currentTimeMillis() + STATUS_DURATION_MILLIS;
    }

    private void clearLinkStatus() {
        linkStatus = null;
        linkStatusUntilMillis = 0L;
    }

    private void clearCachedProfile() {
        if (minecraft == null || minecraft.getUser().getProfileId() == null) return;
        var minecraftUuid = minecraft.getUser().getProfileId();
        DiscordAccountProfileStore.load(minecraftUuid).ifPresent(DiscordAvatarCache::deleteCached);
        DiscordAccountProfileStore.delete(minecraftUuid);
    }

    private static String safeMessage(String message) {
        String cleaned = message.replaceAll("[\\p{Cntrl}]", " ").trim();
        return cleaned.length() <= 160 ? cleaned : cleaned.substring(0, 157) + "...";
    }

    private record StoredLinkResult(DiscordLinkService.Result result, boolean stored) {
    }
}
