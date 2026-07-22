package com.duperknight.client.gui;

import com.duperknight.client.accountlink.DiscordLinkService;
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
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/** Persisted mod-wide settings that do not belong to an individual module. */
public final class DMLSOptionsScreen extends DMLSMenuScreen {
    private static final long STATUS_DURATION_MILLIS = 5_000L;
    private static final int STATUS_COLOR = 0xFFDDDDDD;
    private static final int ERROR_COLOR = 0xFFFF7777;
    private int rankX;
    private int rankY;
    private int rankWidth;
    private int departmentsX;
    private int departmentsY;
    private int departmentsWidth;
    private int dividerY;
    private int departmentDropdownY;
    private int allowedServersY;
    private int integrationsDividerY;
    private int discordLinkY;
    private int linkCodeY;
    private ButtonWidget discordLinkButton;
    private LinkCodeWidget linkCodeWidget;
    private String linkCode = "";
    private String linkClientToken = "";
    private boolean linkCodeRevealed;
    private Text linkStatus;
    private long linkStatusUntilMillis;
    private int linkStatusColor = STATUS_COLOR;
    private boolean pendingStatusShown;
    private boolean storedTokenLoaded;
    private boolean linked;
    private int requestGeneration;
    private int statusGeneration;

    public DMLSOptionsScreen(Screen parent) {
        super(Text.translatable("dmls.title.options"), parent);
    }

    @Override
    protected void init() {
        rankWidth = Math.min(scaled(240), width - scaled(32));
        rankX = width / 2 - rankWidth / 2;
        int footerTop = height - FOOTER_TOP_OFFSET;
        int contentTop = HEADER_HEIGHT + scaled(25);
        boolean showAllowedServers = DMLSConfig.staffRank().isAtLeast(StaffRank.SENIOR_MODERATOR);
        int contentHeight = STANDARD_BUTTON_HEIGHT * (showAllowedServers ? 5 : 4)
                + scaled(showAllowedServers ? 125 : 95);
        rankY = contentTop + Math.max(0, (footerTop - contentTop - contentHeight) / 2);

        departmentsWidth = Math.min(scaled(480), width - scaled(32));
        departmentsX = width / 2 - departmentsWidth / 2;
        allowedServersY = rankY + STANDARD_BUTTON_HEIGHT + scaled(12);
        dividerY = showAllowedServers
                ? allowedServersY + STANDARD_BUTTON_HEIGHT + scaled(18)
                : rankY + STANDARD_BUTTON_HEIGHT + scaled(18);
        departmentsY = dividerY + scaled(14);
        departmentDropdownY = departmentsY + scaled(15);
        integrationsDividerY = departmentDropdownY + STANDARD_BUTTON_HEIGHT + scaled(18);
        discordLinkY = integrationsDividerY + scaled(14);
        linkCodeY = discordLinkY + STANDARD_BUTTON_HEIGHT + scaled(8);
        int gap = scaled(8);
        int departmentWidth = (departmentsWidth - gap * 2) / 3;

        int index = 0;
        for (StaffDepartment department : StaffDepartment.values()) {
            int x = departmentsX + index * (departmentWidth + gap);
            addDropdownChild(DropdownWidget.builder(
                            department.displayName(),
                            DepartmentRank.optionsFor(department),
                            DMLSConfig.departmentRank(department),
                            DepartmentRank::displayName,
                            (dropdown, value) -> DMLSConfig.setDepartmentRank(department, value))
                    .dimensions(x, departmentDropdownY, departmentWidth, STANDARD_BUTTON_HEIGHT)
                    .maxVisibleRows(4)
                    .showOptionLabel(false)
                    .build());
            index++;
        }

        if (showAllowedServers) {
            addDrawableChild(ButtonWidget.builder(Text.translatable("dmls.option.allowed_servers"),
                            button -> client.setScreen(new AllowedServersScreen(this)))
                    .dimensions(rankX, allowedServersY, rankWidth, STANDARD_BUTTON_HEIGHT).build());
        }
        discordLinkButton = addDrawableChild(ButtonWidget.builder(
                        discordLinkButtonText(),
                        button -> requestDiscordLink())
                .dimensions(rankX, discordLinkY, rankWidth, STANDARD_BUTTON_HEIGHT).build());
        discordLinkButton.active = !linked;
        linkCodeWidget = addDrawableChild(new LinkCodeWidget(
                rankX, linkCodeY, rankWidth, STANDARD_BUTTON_HEIGHT, linkCode, linkCodeRevealed,
                () -> linkCodeRevealed = true,
                () -> {
                    setLinkStatus(Text.translatable("dmls.option.discord_link.copied"), STATUS_COLOR);
                }));
        linkCodeWidget.visible = !linked && !linkCode.isEmpty();
        addDrawableChild(ButtonWidget.builder(ScreenTexts.BACK, button -> close())
                .dimensions(width / 2 - scaled(75), footerButtonY(), scaled(150), STANDARD_BUTTON_HEIGHT).build());

        if (!storedTokenLoaded) {
            storedTokenLoaded = true;
            loadStoredLink();
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderMenuBackground(context);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, HEADER_HEIGHT + scaled(16), 0xFFFFFFFF);

        renderPanel(context, rankX, rankY, rankWidth, STANDARD_BUTTON_HEIGHT);
        context.drawCenteredTextWithShadow(textRenderer, DMLSConfig.staffRank().displayName(),
                width / 2, rankY + (STANDARD_BUTTON_HEIGHT - textRenderer.fontHeight) / 2 + scaled(1), 0xFFFFFFFF);

        renderSectionDivider(context, dividerY, Text.translatable("dmls.option.departments"));

        int gap = scaled(8);
        int departmentWidth = (departmentsWidth - gap * 2) / 3;
        int index = 0;
        for (StaffDepartment department : StaffDepartment.values()) {
            int centerX = departmentsX + index * (departmentWidth + gap) + departmentWidth / 2;
            context.drawCenteredTextWithShadow(textRenderer, department.displayName(), centerX, departmentsY, 0xFFDDDDDD);
            index++;
        }
        renderSectionDivider(context, integrationsDividerY, Text.translatable("dmls.option.integrations"));
        if (linkStatus != null && System.currentTimeMillis() >= linkStatusUntilMillis) {
            clearLinkStatus();
        }
        if (linkStatus != null) {
            List<OrderedText> statusLines = textRenderer.wrapLines(linkStatus, departmentsWidth);
            int statusY = linkCodeWidget != null && linkCodeWidget.visible
                    ? linkCodeY + STANDARD_BUTTON_HEIGHT + scaled(7)
                    : linkCodeY + scaled(2);
            for (OrderedText line : statusLines) {
                context.drawCenteredTextWithShadow(textRenderer, line, width / 2, statusY, linkStatusColor);
                statusY += scaled(11);
            }
        }
        super.render(context, mouseX, mouseY, delta);
    }

    private void renderSectionDivider(DrawContext context, int y, Text label) {
        int titleHalfWidth = textRenderer.getWidth(label) / 2;
        int titlePadding = scaled(8);
        context.fill(departmentsX, y,
                width / 2 - titleHalfWidth - titlePadding, y + 1, PANEL_BORDER_COLOR);
        context.fill(width / 2 + titleHalfWidth + titlePadding, y,
                departmentsX + departmentsWidth, y + 1, PANEL_BORDER_COLOR);
        context.drawCenteredTextWithShadow(textRenderer, label,
                width / 2, y - textRenderer.fontHeight / 2, 0xFFFFFFFF);
    }

    private void requestDiscordLink() {
        if (client == null || client.getSession().getUuidOrNull() == null) {
            setLinkStatus(Text.translatable("dmls.option.discord_link.error.identity"), ERROR_COLOR);
            return;
        }

        statusGeneration++;
        pendingStatusShown = false;
        int generation = ++requestGeneration;
        discordLinkButton.active = false;
        discordLinkButton.setMessage(Text.translatable("dmls.option.discord_link.requesting"));
        setLinkStatus(Text.translatable("dmls.option.discord_link.requesting_hint"), STATUS_COLOR);

        var minecraftUuid = client.getSession().getUuidOrNull();
        DiscordLinkService.request(minecraftUuid, client.getSession().getUsername())
                .thenApply(result -> new StoredLinkResult(result,
                        !result.succeeded() || DiscordLinkTokenStore.save(minecraftUuid, result.clientToken())))
                .thenAccept(result -> client.execute(() -> applyLinkResult(generation, result)));
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
                linkCodeWidget.visible = false;
                discordLinkButton.setMessage(Text.translatable("dmls.option.discord_link"));
                setLinkStatus(Text.translatable("dmls.option.discord_link.error.storage"), ERROR_COLOR);
                return;
            }
            linkCode = result.code();
            linkClientToken = result.clientToken();
            linked = false;
            clearCachedProfile();
            linkCodeRevealed = false;
            linkCodeWidget.setCode(linkCode);
            linkCodeWidget.visible = true;
            discordLinkButton.setMessage(Text.translatable("dmls.option.discord_link.new_code"));
            setLinkStatus(Text.translatable("dmls.option.discord_link.instructions"), STATUS_COLOR);
            pendingStatusShown = true;
            beginStatusCheck(++statusGeneration);
            return;
        }

        discordLinkButton.setMessage(discordLinkButtonText());
        Text error = switch (result.status()) {
            case RATE_LIMITED -> Text.translatable("dmls.option.discord_link.error.rate_limited");
            case TIMEOUT -> Text.translatable("dmls.option.discord_link.error.timeout");
            case NETWORK_ERROR -> Text.translatable("dmls.option.discord_link.error.network");
            case SERVICE_ERROR -> result.message().isBlank()
                    ? Text.translatable("dmls.option.discord_link.error.service")
                    : Text.translatable("dmls.option.discord_link.error.service_message", safeMessage(result.message()));
            default -> Text.translatable("dmls.option.discord_link.error.response");
        };
        setLinkStatus(error, ERROR_COLOR);
    }

    private void loadStoredLink() {
        if (client == null || client.getSession().getUuidOrNull() == null) return;
        DiscordLinkTokenStore.load(client.getSession().getUuidOrNull()).ifPresent(token -> {
            linkClientToken = token;
            discordLinkButton.active = false;
            discordLinkButton.setMessage(Text.translatable("dmls.option.discord_link.checking"));
            clearLinkStatus();
            pendingStatusShown = false;
            beginStatusCheck(++statusGeneration);
        });
    }

    private void beginStatusCheck(int generation) {
        if (client == null || client.currentScreen != this || generation != statusGeneration
                || linkClientToken.isEmpty() || client.getSession().getUuidOrNull() == null) {
            return;
        }

        var minecraftUuid = client.getSession().getUuidOrNull();
        DiscordLinkService.checkStatus(minecraftUuid, linkClientToken)
                .thenAccept(result -> client.execute(() -> applyStatusResult(generation, result)));
    }

    private void applyStatusResult(int generation, DiscordLinkService.LinkStatusResult result) {
        if (client == null || client.currentScreen != this || generation != statusGeneration) return;

        switch (result.status()) {
            case LINKED -> showLinked(result.profile());
            case PENDING -> {
                linked = false;
                discordLinkButton.active = true;
                discordLinkButton.setMessage(Text.translatable("dmls.option.discord_link.new_code"));
                if (!pendingStatusShown) {
                    clearCachedProfile();
                    setLinkStatus(Text.translatable(linkCode.isEmpty()
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
                linkCodeWidget.visible = false;
                discordLinkButton.active = false;
                discordLinkButton.setMessage(Text.translatable("dmls.option.discord_link.stale"));
                setLinkStatus(Text.translatable("dmls.option.discord_link.error.stale"), ERROR_COLOR);
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
        pendingStatusShown = false;
        linkCode = "";
        linkCodeRevealed = false;
        linkCodeWidget.visible = false;
        discordLinkButton.active = false;
        discordLinkButton.setMessage(Text.translatable("dmls.option.discord_link.linked"));
        clearLinkStatus();
        if (profile != null) {
            DiscordAccountProfileStore.save(profile);
            DiscordAvatarCache.ensureCached(profile);
        }
    }

    private void clearInvalidToken(String messageKey) {
        if (client != null && client.getSession().getUuidOrNull() != null) {
            DiscordLinkTokenStore.delete(client.getSession().getUuidOrNull());
            clearCachedProfile();
        }
        statusGeneration++;
        linked = false;
        pendingStatusShown = false;
        linkCode = "";
        linkClientToken = "";
        linkCodeRevealed = false;
        linkCodeWidget.visible = false;
        discordLinkButton.active = true;
        discordLinkButton.setMessage(Text.translatable("dmls.option.discord_link"));
        setLinkStatus(Text.translatable(messageKey), ERROR_COLOR);
    }

    private void retryStatusCheck(int generation, String messageKey, int delaySeconds) {
        discordLinkButton.active = false;
        discordLinkButton.setMessage(Text.translatable("dmls.option.discord_link.checking"));
        setLinkStatus(Text.translatable(messageKey), ERROR_COLOR);
        scheduleStatusCheck(generation, delaySeconds);
    }

    private void scheduleStatusCheck(int generation, int delaySeconds) {
        var currentClient = client;
        if (currentClient == null) return;
        CompletableFuture.delayedExecutor(Math.max(1, delaySeconds), TimeUnit.SECONDS).execute(() ->
                currentClient.execute(() -> beginStatusCheck(generation)));
    }

    private Text discordLinkButtonText() {
        if (linked) return Text.translatable("dmls.option.discord_link.linked");
        return Text.translatable(linkCode.isEmpty()
                ? "dmls.option.discord_link" : "dmls.option.discord_link.new_code");
    }

    private void setLinkStatus(Text status, int color) {
        linkStatus = status;
        linkStatusColor = color;
        linkStatusUntilMillis = System.currentTimeMillis() + STATUS_DURATION_MILLIS;
    }

    private void clearLinkStatus() {
        linkStatus = null;
        linkStatusUntilMillis = 0L;
    }

    private void clearCachedProfile() {
        if (client == null || client.getSession().getUuidOrNull() == null) return;
        var minecraftUuid = client.getSession().getUuidOrNull();
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
