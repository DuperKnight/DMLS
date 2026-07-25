package com.duperknight.client.gui.modules;

import com.duperknight.client.gui.DMLSMenuScreen;
import com.duperknight.client.modules.ReimbursementModule;
import com.duperknight.client.modules.StaffRank;
import com.duperknight.client.reimbursement.ContainerSelectionController;
import com.duperknight.client.reimbursement.ContainerTarget;
import com.duperknight.client.reimbursement.Destination;
import com.duperknight.client.reimbursement.ItemEntry;
import com.duperknight.client.reimbursement.MoneyEntry;
import com.duperknight.client.reimbursement.ReimbursementDraft;
import com.duperknight.client.reimbursement.ReimbursementEntry;
import com.duperknight.client.reimbursement.ReimbursementEstimate;
import com.duperknight.client.reimbursement.ReimbursementCommandPlanner;
import com.duperknight.client.reimbursement.ReimbursementPlan;
import com.duperknight.client.reimbursement.ReimbursementResult;
import com.duperknight.client.session.OutboundSpamSafety;
import com.duperknight.client.utils.ClientUtils;
import com.duperknight.client.utils.DMLSConfig;
import com.duperknight.client.utils.PrefixTextFormatter;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tab.GridScreenTab;
import net.minecraft.client.gui.tab.Tab;
import net.minecraft.client.gui.tab.TabManager;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.EditBoxWidget;
import net.minecraft.client.gui.widget.TabButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Two-tab editor and locked terminal view for Reimbursement Helper. */
public final class ReimbursementScreen extends DMLSMenuScreen {
    private static final int SLOT_SIZE = 18;
    private static final int GRID_COLUMNS = 9;
    private static final int GRID_ROWS_VISIBLE = 6;
    private static final int GRID_HEIGHT = SLOT_SIZE * GRID_ROWS_VISIBLE;
    private static final int PANE_GAP = 5;
    private static final int ENTRY_ROW_HEIGHT = 36;
    private static final int SUGGESTION_ROW_HEIGHT = 12;
    private static final int SUGGESTION_VISIBLE_ROWS = 10;
    private static final int SUGGESTION_BACKGROUND = 0xD0000000;
    private static final int SUGGESTION_TEXT = 0xFFAAAAAA;
    private static final int SUGGESTION_SELECTED_TEXT = 0xFFFFFF00;
    private static final Identifier LIST_BACKGROUND =
            Identifier.ofVanilla("textures/gui/inworld_menu_list_background.png");
    private static final Identifier LIST_HEADER_SEPARATOR =
            Identifier.ofVanilla("textures/gui/inworld_header_separator.png");
    private static final Identifier LIST_FOOTER_SEPARATOR =
            Identifier.ofVanilla("textures/gui/inworld_footer_separator.png");
    private static final Identifier REMOVE_TEXTURE = Identifier.ofVanilla("pending_invite/reject");
    private static final Identifier REMOVE_HIGHLIGHTED_TEXTURE =
            Identifier.ofVanilla("pending_invite/reject_highlighted");

    private enum ViewTab { REIMBURSEMENT, CONFIG }
    private enum Pane { LEFT, CENTER, RIGHT, CONFIG }

    private final ReimbursementModule module;
    private final ReimbursementDraft draft;
    private final ReimbursementResult result;
    private final List<PaneChild> paneChildren = new ArrayList<>();
    private final List<ButtonWidget> destinationButtons = new ArrayList<>();

    private ViewTab activeTab = ViewTab.REIMBURSEMENT;
    private int selectedEntry;
    private int leftScroll;
    private int centerScroll;
    private int centerUiScroll;
    private int rightScroll;
    private int configScroll;
    private int resultScroll;
    private int knownContainerCount;
    private String validation = "";
    private boolean copied;

    private TabManager tabManager;
    private Tab reimbursementTab;
    private Tab configTab;
    private TextFieldWidget itemSearch;
    private TextFieldWidget moneyAmount;
    private TextFieldWidget itemName;
    private EditBoxWidget loreField;
    private TextFieldWidget itemAmount;
    private TextFieldWidget enchantSearch;
    private TextFieldWidget playerIgn;
    private TextFieldWidget configIgn;
    private TextFieldWidget discordUsername;
    private TextFieldWidget discordId;
    private EditBoxWidget reasonField;
    private TextFieldWidget ticketField;
    private ButtonWidget startButton;
    private ButtonWidget doneButton;
    private ButtonWidget destinationFollowup;
    private int suggestionIndex;
    private int suggestionWindowOffset;
    private int enchantmentSuggestionIndex;
    private int enchantmentSuggestionWindowOffset;
    private List<String> suggestions = List.of();
    private List<ItemChoice> filteredItems = List.of();
    private List<EnchantmentChoice> filteredEnchantments = List.of();
    private List<EnchantmentChoice> availableEnchantments = List.of();
    private final Map<Identifier, TextFieldWidget> enchantmentLevelFields = new LinkedHashMap<>();
    private int initializedDestinationBase;

    public ReimbursementScreen(
            Screen parent,
            ReimbursementModule module,
            ReimbursementDraft draft,
            ReimbursementResult result
    ) {
        super(Text.translatable("dmls.module.reimbursement.name"), parent);
        this.module = module;
        this.draft = draft;
        this.result = result;
    }

    @Override
    protected boolean isHeaderCollapseForced() {
        return true;
    }

    @Override
    protected boolean renderSharedHeaderSeparator() {
        return result != null;
    }

    @Override
    protected void init() {
        paneChildren.clear();
        destinationButtons.clear();
        enchantmentLevelFields.clear();
        destinationFollowup = null;
        knownContainerCount = selectedContainerCount();
        int top = contentTop();
        configureScrollableContent(top, footerTop() - 8, Math.max(1, footerTop() - top - 8));
        if (result != null) {
            initResult();
            return;
        }
        initTabs();
        if (activeTab == ViewTab.REIMBURSEMENT) initReimbursement();
        else initConfig();
        layoutPaneChildren();
    }

    private void initTabs() {
        tabManager = new TabManager(this::addDrawableChild, this::remove);
        reimbursementTab = new GridScreenTab(Text.translatable("dmls.reimbursement.tab.entries"));
        configTab = new GridScreenTab(Text.translatable("dmls.reimbursement.tab.config"));
        int totalWidth = tabTotalWidth();
        int tabWidth = totalWidth / 2;
        int tabHeight = 24;
        int tabX = (width - totalWidth) / 2;
        int tabY = settledHeaderHeight() - tabHeight;
        TabButtonWidget reimbursementButton =
                new TabButtonWidget(tabManager, reimbursementTab, tabWidth, tabHeight) {
                    @Override
                    public void onClick(Click click, boolean doubled) {
                        tabManager.setCurrentTab(reimbursementTab, true);
                    }
                };
        reimbursementButton.setDimensionsAndPosition(tabWidth, tabHeight, tabX, tabY);
        addDrawableChild(reimbursementButton);
        TabButtonWidget configButton = new TabButtonWidget(tabManager, configTab, tabWidth, tabHeight) {
            @Override
            public void onClick(Click click, boolean doubled) {
                tabManager.setCurrentTab(configTab, true);
            }
        };
        configButton.setDimensionsAndPosition(tabWidth, tabHeight, tabX + tabWidth, tabY);
        addDrawableChild(configButton);
        tabManager.setCurrentTab(activeTab == ViewTab.REIMBURSEMENT ? reimbursementTab : configTab, false);
    }

    private void initReimbursement() {
        Layout layout = layout();
        addDrawableChild(ButtonWidget.builder(Text.literal("Back"), ignored -> client.setScreen(parent))
                .dimensions(leftPairedButtonX(), footerButtonY(), pairedButtonWidth(), 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Next"), ignored -> next())
                .dimensions(rightPairedButtonX(), footerButtonY(), pairedButtonWidth(), 20).build());
        selectedEntry = Math.clamp(selectedEntry, 0, Math.max(0, draft.entries().size() - 1));
        if (draft.entries().isEmpty()) return;
        ReimbursementEntry entry = draft.entries().get(selectedEntry);
        int half = (layout.centerWidth() - 10) / 2;
        ButtonWidget itemToggle = ButtonWidget.builder(Text.literal("Item"), ignored -> changeType(true))
                .dimensions(layout.centerX() + 3, layout.top() + 3, half, 20).build();
        itemToggle.active = !(entry instanceof ItemEntry);
        addPaneChild(itemToggle, Pane.CENTER, 3);
        ButtonWidget moneyToggle = ButtonWidget.builder(Text.literal("Money"), ignored -> changeType(false))
                .dimensions(layout.centerX() + 7 + half, layout.top() + 3, half, 20).build();
        moneyToggle.active = !(entry instanceof MoneyEntry);
        addPaneChild(moneyToggle, Pane.CENTER, 3);

        if (entry instanceof MoneyEntry money) initMoney(layout, money);
        else initItem(layout, (ItemEntry) entry);
    }

    private void initMoney(Layout layout, MoneyEntry money) {
        initializedDestinationBase = 38;
        moneyAmount = textField(layout.centerX() + 4, layout.top() + 42,
                layout.centerWidth() - 12, money.amount().signum() == 0 ? "" : money.amount().toPlainString(),
                "0.00", 12, value -> updateMoneyAmount(value));
        addPaneChild(moneyAmount, Pane.CENTER, 42);
        initDestinations(layout, money, false, 38);
    }

    private void initItem(Layout layout, ItemEntry entry) {
        loadEnchantments();
        refreshEnchantments("");
        ItemRightLayout rightLayout = itemRightLayout(layout);
        initializedDestinationBase = rightLayout.destinationBase();
        itemSearch = textField(layout.centerX() + 4, layout.top() + 42,
                layout.centerWidth() - 12, "", "Search vanilla items", 80, value -> {
                    refreshItems(value);
                    centerScroll = 0;
                });
        addPaneChild(itemSearch, Pane.CENTER, 42);
        refreshItems("");

        int rightWidth = rightContentWidth(layout);
        itemName = textField(layout.rightX() + 4, layout.top() + 23, rightWidth,
                entry.customName(), "Optional custom item name", 240, value ->
                        updateItem(currentItem().withCustomName(value)));
        addPaneChild(itemName, Pane.RIGHT, 23);

        loreField = EditBoxWidget.builder().x(layout.rightX() + 4)
                .y(layout.top() + rightLayout.loreField())
                .placeholder(Text.literal("Shift+Enter for another lore line")
                        .formatted(Formatting.DARK_GRAY))
                .build(textRenderer, rightWidth, 58, Text.literal("Lore"));
        loreField.setMaxLength(2048);
        loreField.setText(String.join("\n", entry.lore()));
        loreField.setChangeListener(value ->
                updateItem(currentItem().withLore(List.of(value.split("\\n", -1)))));
        addPaneChild(loreField, Pane.RIGHT, rightLayout.loreField());

        itemAmount = textField(layout.rightX() + 4, layout.top() + rightLayout.amountField(), rightWidth,
                Integer.toString(entry.amount()), "1–9999", 4, value -> {
                    try {
                        updateItem(currentItem().withAmount(Integer.parseInt(value)));
                    } catch (NumberFormatException ignored) {
                    }
                });
        itemAmount.setTextPredicate(value -> value.isEmpty() || value.chars().allMatch(Character::isDigit));
        addPaneChild(itemAmount, Pane.RIGHT, rightLayout.amountField());

        enchantSearch = textField(layout.rightX() + 4, layout.top() + rightLayout.enchantSearch(), rightWidth,
                "", "Add an enchantment", 80, this::refreshEnchantments);
        addPaneChild(enchantSearch, Pane.RIGHT, rightLayout.enchantSearch());
        int row = 0;
        for (Map.Entry<Identifier, Integer> enchantment : entry.enchantments().entrySet()) {
            int offset = rightLayout.enchantmentRows() + row * 28 + 2;
            TextFieldWidget level = textField(layout.rightX() + 4, layout.top() + offset,
                    38, Integer.toString(enchantment.getValue()), "1", 3,
                    value -> updateEnchantmentLevel(enchantment.getKey(), value));
            level.setTextPredicate(value -> value.isEmpty()
                    || value.chars().allMatch(Character::isDigit));
            enchantmentLevelFields.put(enchantment.getKey(), level);
            addPaneChild(level, Pane.RIGHT, offset);
            row++;
        }
        initDestinations(layout, entry, true, rightLayout.destinationBase());
    }

    private void initDestinations(
            Layout layout,
            ReimbursementEntry entry,
            boolean containersAllowed,
            int base
    ) {
        int options = containersAllowed ? 3 : 2;
        int contentWidth = rightContentWidth(layout);
        int buttonWidth = Math.max(1, (contentWidth - (options - 1) * 4) / options);
        addDestinationButton(layout, "Me", Destination.ME, base, 0, buttonWidth);
        addDestinationButton(layout, "Player", Destination.PLAYER, base,
                buttonWidth + 4, buttonWidth);
        if (containersAllowed) {
            addDestinationButton(layout, "Container", Destination.CONTAINER, base,
                    (buttonWidth + 4) * 2, buttonWidth);
        }
        if (entry.destination() == Destination.PLAYER) {
            playerIgn = textField(layout.rightX() + 4, layout.top() + base + 28,
                    contentWidth, entry.playerIgn(), "Player IGN", 16,
                    value -> updateEntry(currentEntry().withPlayerIgn(value)));
            addPaneChild(playerIgn, Pane.RIGHT, base + 28);
        }
        if (containersAllowed && entry.destination() == Destination.CONTAINER) {
            ItemEntry item = (ItemEntry) entry;
            destinationFollowup = registerCommandControl(ButtonWidget.builder(
                            Text.literal("Select containers (" + item.containers().size() + ")"),
                            ignored -> selectContainers())
                    .dimensions(layout.rightX() + 4, layout.top() + base + 28,
                            contentWidth, 20).build());
            addPaneChild(destinationFollowup, Pane.RIGHT, base + 28);
        }
    }

    private void addDestinationButton(
            Layout layout,
            String label,
            Destination destination,
            int base,
            int offset,
            int buttonWidth
    ) {
        ButtonWidget button = destinationButton(layout, label, destination, base, offset, buttonWidth);
        destinationButtons.add(button);
        addPaneChild(button, Pane.RIGHT, base);
    }

    private ButtonWidget destinationButton(
            Layout layout,
            String label,
            Destination destination,
            int base,
            int offset,
            int buttonWidth
    ) {
        boolean selected = currentEntry().destination() == destination;
        ButtonWidget button = ButtonWidget.builder(Text.literal(label), ignored -> {
                    updateEntry(currentEntry().withDestination(destination));
                    clearAndInit();
                })
                .dimensions(layout.rightX() + 4 + offset, layout.top() + base, buttonWidth, 20).build();
        button.active = !selected;
        return button;
    }

    private void initConfig() {
        Layout layout = layout();
        int formWidth = Math.min(460, width - 60);
        int x = (width - formWidth) / 2;
        configIgn = configField(x, 22, formWidth, draft.ign(), "Minecraft IGN(s)", 160, draft::setIgn);
        int discordWidth = (formWidth - 18) / 2;
        discordUsername = configField(x, 70, discordWidth, draft.discordUsername(),
                "Discord username", 64, draft::setDiscordUsername);
        discordId = configField(x + discordWidth + 18, 70, discordWidth, draft.discordId(),
                "Optional Discord ID", 20, draft::setDiscordId);
        discordId.setTextPredicate(value -> value.isEmpty() || value.chars().allMatch(Character::isDigit));

        reasonField = EditBoxWidget.builder().x(x).y(layout.top() + 126)
                .placeholder(Text.literal("Reason — Shift+Enter for another line"))
                .build(textRenderer, formWidth, 80, Text.literal("Reason"));
        reasonField.setMaxLength(2048);
        reasonField.setText(draft.reason());
        reasonField.setChangeListener(draft::setReason);
        addPaneChild(reasonField, Pane.CONFIG, 126);
        ticketField = configField(x, 232, formWidth, draft.ticket(), "Ticket", 96, draft::setTicket);

        addDrawableChild(ButtonWidget.builder(Text.literal("Back"), ignored -> switchTab(ViewTab.REIMBURSEMENT))
                .dimensions(leftPairedButtonX(), footerButtonY(), pairedButtonWidth(), 20).build());
        startButton = registerCommandControl(addDrawableChild(ButtonWidget.builder(
                        Text.literal("Start Reimbursement"), ignored -> start())
                .dimensions(rightPairedButtonX(), footerButtonY(), pairedButtonWidth(), 20).build()),
                () -> ReimbursementPlan.prepare(draft).valid());
    }

    private TextFieldWidget configField(
            int x,
            int offset,
            int fieldWidth,
            String value,
            String placeholder,
            int max,
            java.util.function.Consumer<String> listener
    ) {
        Layout layout = layout();
        TextFieldWidget field = textField(x, layout.top() + offset, fieldWidth, value,
                placeholder, max, listener);
        addPaneChild(field, Pane.CONFIG, offset);
        return field;
    }

    private void initResult() {
        int buttonWidth = Math.min(220, (width - 36) / 2);
        if (result.requiresLogCopy()) {
            addDrawableChild(ButtonWidget.builder(Text.literal(copied ? "Copied!" : "Copy Reimbursement Log"),
                            ignored -> copyResult())
                    .dimensions(width / 2 - buttonWidth - 4, footerButtonY(), buttonWidth, 20).build());
            doneButton = addDrawableChild(ButtonWidget.builder(Text.literal("Done"), ignored -> done())
                    .dimensions(width / 2 + 4, footerButtonY(), buttonWidth, 20).build());
            doneButton.active = copied;
        } else {
            doneButton = addDrawableChild(ButtonWidget.builder(Text.literal("Done"), ignored -> done())
                    .dimensions((width - buttonWidth) / 2, footerButtonY(), buttonWidth, 20).build());
        }
    }

    private TextFieldWidget textField(
            int x,
            int y,
            int fieldWidth,
            String value,
            String placeholder,
            int max,
            java.util.function.Consumer<String> listener
    ) {
        TextFieldWidget field = addDrawableChild(new TextFieldWidget(
                textRenderer, x, y, fieldWidth, 20, Text.empty()));
        field.setMaxLength(max);
        field.setText(value == null ? "" : value);
        field.setPlaceholder(Text.literal(textRenderer.trimToWidth(
                placeholder, Math.max(0, fieldWidth - 8))));
        field.setChangedListener(listener);
        return field;
    }

    private void addPaneChild(ClickableWidget widget, Pane pane, int offset) {
        if (!children().contains(widget)) addDrawableChild(widget);
        paneChildren.add(new PaneChild(widget, pane, offset));
    }

    private void layoutPaneChildren() {
        if (result != null) return;
        Layout layout = layout();
        ItemRightLayout rightLayout = !draft.entries().isEmpty() && currentEntry() instanceof ItemEntry
                ? itemRightLayout(layout) : null;
        if (rightLayout != null) {
            rightScroll = Math.clamp(rightScroll, 0,
                    Math.max(0, rightLayout.contentHeight() - layout.height()));
            int contentWidth = rightLayout.contentWidth();
            itemName.setWidth(contentWidth);
            loreField.setWidth(contentWidth);
            itemAmount.setWidth(contentWidth);
            enchantSearch.setWidth(contentWidth);
            if (playerIgn != null) playerIgn.setWidth(contentWidth);
            if (destinationFollowup != null) destinationFollowup.setWidth(contentWidth);
            for (TextFieldWidget level : enchantmentLevelFields.values()) {
                level.setX(layout.rightX() + 4 + contentWidth - 64);
                level.setWidth(38);
            }
            int buttonWidth = Math.max(1,
                    (contentWidth - Math.max(0, destinationButtons.size() - 1) * 4)
                            / Math.max(1, destinationButtons.size()));
            for (int index = 0; index < destinationButtons.size(); index++) {
                ButtonWidget button = destinationButtons.get(index);
                button.setX(layout.rightX() + 4 + index * (buttonWidth + 4));
                button.setWidth(buttonWidth);
            }
        }
        for (PaneChild child : paneChildren) {
            int scroll = switch (child.pane()) {
                case LEFT -> leftScroll;
                case CENTER -> centerUiScroll;
                case RIGHT -> rightScroll;
                case CONFIG -> configScroll;
            };
            int offset = child.offset();
            if (rightLayout != null && child.pane() == Pane.RIGHT) {
                if (child.widget() == loreField) offset = rightLayout.loreField();
                else if (child.widget() == itemAmount) offset = rightLayout.amountField();
                else if (child.widget() == enchantSearch) offset = rightLayout.enchantSearch();
                else if (enchantmentLevelFields.containsValue(child.widget())) {
                    int row = new ArrayList<>(enchantmentLevelFields.values()).indexOf(child.widget());
                    offset = rightLayout.enchantmentRows() + row * 28 + 2;
                }
                else if (offset >= initializedDestinationBase) {
                    offset += rightLayout.destinationBase() - initializedDestinationBase;
                }
            }
            child.widget().setY(layout.top() + offset - scroll);
            child.widget().visible = child.widget().getBottom() > layout.top()
                    && child.widget().getY() < layout.bottom();
        }
    }

    @Override
    public void tick() {
        if (result == null && knownContainerCount != selectedContainerCount()) {
            clearAndInit();
            return;
        }
        if (result == null && tabManager != null) {
            ViewTab selected = tabManager.getCurrentTab() == configTab
                    ? ViewTab.CONFIG : ViewTab.REIMBURSEMENT;
            if (selected != activeTab) switchTab(selected);
        }
        refreshSuggestions();
        layoutPaneChildren();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderMenuBackground(context);
        if (result != null) renderResult(context);
        else {
            renderTabsAndPanels(context);
            if (activeTab == ViewTab.REIMBURSEMENT) renderReimbursement(context, mouseX, mouseY);
            else renderConfig(context);
        }
        paneChildren.forEach(child -> child.widget().visible = false);
        super.render(context, mouseX, mouseY, delta);
        if (result == null) renderPaneWidgets(context, mouseX, mouseY, delta);
        if (result == null) renderSuggestions(context, mouseX, mouseY);
    }

    private void renderPaneWidgets(DrawContext context, int mouseX, int mouseY, float delta) {
        Layout layout = layout();
        for (Pane pane : Pane.values()) {
            int left = switch (pane) {
                case LEFT -> layout.leftX();
                case CENTER -> layout.centerX();
                case RIGHT -> layout.rightX();
                case CONFIG -> 0;
            };
            int right = switch (pane) {
                case LEFT -> layout.leftX() + layout.leftWidth();
                case CENTER -> layout.centerX() + layout.centerWidth();
                case RIGHT -> layout.rightX() + layout.rightWidth();
                case CONFIG -> width;
            };
            context.enableScissor(left, layout.top(), right, layout.bottom());
            int clippedMouseX = mouseX >= left && mouseX < right
                    && mouseY >= layout.top() && mouseY < layout.bottom()
                    ? mouseX : Integer.MIN_VALUE;
            int clippedMouseY = clippedMouseX != Integer.MIN_VALUE ? mouseY : Integer.MIN_VALUE;
            for (PaneChild child : paneChildren) {
                if (child.pane() != pane) continue;
                ClickableWidget widget = child.widget();
                widget.visible = widget.getBottom() > layout.top()
                        && widget.getY() < layout.bottom();
                if (widget.visible) widget.render(context, clippedMouseX, clippedMouseY, delta);
            }
            context.disableScissor();
        }
    }

    private void renderTabsAndPanels(DrawContext context) {
        Layout layout = layout();
        int tabTop = settledHeaderHeight() - 24;
        int titleY = Math.max(2, tabTop - textRenderer.fontHeight - 3);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, titleY, 0xFFFFFFFF);
        renderHeaderSeparatorOutsideTabs(context);
        if (activeTab == ViewTab.REIMBURSEMENT) {
            renderTexturedPanel(context, layout.leftX(), layout.top(), layout.leftWidth(),
                    layout.height(), leftScroll);
            renderTexturedPanel(context, layout.rightX(), layout.top(), layout.rightWidth(),
                    layout.height(), rightScroll);
            renderPaneScrollbar(context, layout.leftX() + layout.leftWidth() - 7,
                    layout.top() + 2, layout.height() - 4, leftScroll, leftContentHeight(), layout.height());
            renderPaneScrollbar(context, layout.rightX() + layout.rightWidth() - 7,
                    layout.top() + 2, layout.height() - 4, rightScroll, rightContentHeight(), layout.height());
        } else {
            int width = Math.min(500, this.width - 40);
            renderPanel(context, (this.width - width) / 2, layout.top(), width, layout.height());
        }
    }

    private void renderReimbursement(DrawContext context, int mouseX, int mouseY) {
        renderEntryList(context, mouseX, mouseY);
        if (draft.entries().isEmpty()) return;
        ReimbursementEntry entry = currentEntry();
        Layout layout = layout();
        context.enableScissor(layout.centerX(), layout.top(),
                layout.centerX() + layout.centerWidth(), layout.bottom());
        if (entry instanceof ItemEntry item) {
            context.drawTextWithShadow(textRenderer, Text.literal("Item"),
                    layout.centerX() + 4, layout.top() + 27 - centerUiScroll, 0xFFCCCCCC);
            renderItemGrid(context, layout, mouseX, mouseY);
        } else {
            context.drawTextWithShadow(textRenderer, Text.literal("Amount"),
                    layout.centerX() + 4, layout.top() + 27 - centerUiScroll, 0xFFCCCCCC);
        }
        context.disableScissor();
        if (entry instanceof ItemEntry item) renderItemDetails(context, layout, item, mouseX, mouseY);
        renderDestinationTitle(context, layout, entry);
        if (!validation.isEmpty()) {
            context.drawCenteredTextWithShadow(textRenderer, Text.literal(validation),
                    width / 2, layout.bottom() - 12, 0xFFFF5555);
        }
        addFooterForEntriesIfMissing();
    }

    private void addFooterForEntriesIfMissing() {
        // Footer controls are rebuilt only once per init; this method intentionally does no work.
    }

    private void renderItemGrid(DrawContext context, Layout layout, int mouseX, int mouseY) {
        int x = layout.centerX() + Math.max(4, (layout.centerWidth() - GRID_COLUMNS * SLOT_SIZE) / 2);
        int y = layout.top() + 68 - centerUiScroll;
        int boxX = x - 4;
        int boxY = y - 4;
        int boxWidth = GRID_COLUMNS * SLOT_SIZE + 14;
        int boxHeight = GRID_HEIGHT + 8;
        renderTexturedPanel(context, boxX, boxY, boxWidth, boxHeight, centerScroll);
        context.enableScissor(x, y, x + GRID_COLUMNS * SLOT_SIZE, y + GRID_HEIGHT);
        int firstRow = centerScroll / SLOT_SIZE;
        int first = firstRow * GRID_COLUMNS;
        int end = Math.min(filteredItems.size(), first + GRID_COLUMNS * GRID_ROWS_VISIBLE);
        for (int index = first; index < end; index++) {
            int relative = index - firstRow * GRID_COLUMNS;
            int column = relative % GRID_COLUMNS;
            int row = relative / GRID_COLUMNS;
            int slotX = x + column * SLOT_SIZE;
            int slotY = y + row * SLOT_SIZE - centerScroll % SLOT_SIZE;
            boolean selected = currentItem().itemId() != null
                    && currentItem().itemId().equals(filteredItems.get(index).id());
            context.fill(slotX, slotY, slotX + 17, slotY + 17,
                    selected ? 0xAA55FF55 : 0x80303030);
            context.drawItem(filteredItems.get(index).stack(), slotX, slotY);
        }
        context.disableScissor();
        int rows = (filteredItems.size() + GRID_COLUMNS - 1) / GRID_COLUMNS;
        renderPaneScrollbar(context, boxX + boxWidth - 7,
                y, GRID_HEIGHT, centerScroll, rows * SLOT_SIZE, GRID_HEIGHT);
    }

    private void renderEntryList(DrawContext context, int mouseX, int mouseY) {
        Layout layout = layout();
        int rowX = layout.leftX() + 4;
        int rowWidth = entryRowWidth(layout);
        boolean scrolling = leftContentHeight() > layout.height();
        context.enableScissor(layout.leftX() + 1, layout.top() + 1,
                layout.leftX() + layout.leftWidth() - (scrolling ? 8 : 1), layout.bottom() - 1);
        for (int index = 0; index < draft.entries().size(); index++) {
            int rowY = layout.top() + 4 + index * ENTRY_ROW_HEIGHT - leftScroll;
            boolean hovered = mouseX >= rowX && mouseX < rowX + rowWidth
                    && mouseY >= rowY && mouseY < rowY + ENTRY_ROW_HEIGHT - 2;
            boolean selected = index == selectedEntry;
            renderEntryRowBackground(context, rowX, rowY, rowWidth, selected, hovered);

            ReimbursementEntry entry = draft.entries().get(index);
            ItemStack icon = entryIcon(entry);
            context.drawItem(icon, rowX + 6, rowY + 9);
            int textWidth = rowWidth - 34 - (selected && hovered ? 22 : 0);
            String rowLabel = textRenderer.trimToWidth(entryLabel(entry).getString(), textWidth);
            context.drawTextWithShadow(textRenderer, rowLabel,
                    rowX + 28, rowY + 13, 0xFFFFFFFF);

            if (selected && hovered) {
                int removeX = rowX + rowWidth - 23;
                int removeY = rowY + 8;
                boolean removeHovered = mouseX >= removeX && mouseX < removeX + 18
                        && mouseY >= removeY && mouseY < removeY + 18;
                context.drawGuiTexture(RenderPipelines.GUI_TEXTURED,
                        removeHovered ? REMOVE_HIGHLIGHTED_TEXTURE : REMOVE_TEXTURE,
                        removeX, removeY, 18, 18);
            }
        }

        int addY = layout.top() + 4 + draft.entries().size() * ENTRY_ROW_HEIGHT - leftScroll;
        boolean addHovered = mouseX >= rowX && mouseX < rowX + rowWidth
                && mouseY >= addY && mouseY < addY + ENTRY_ROW_HEIGHT - 2;
        renderEntryRowBackground(context, rowX, addY, rowWidth, false, addHovered);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal("+  Add Item/Money"),
                rowX + rowWidth / 2, addY + 13, 0xFFFFFFFF);
        context.disableScissor();
    }

    private void renderEntryRowBackground(
            DrawContext context,
            int x,
            int y,
            int rowWidth,
            boolean selected,
            boolean hovered
    ) {
        if (selected) {
            context.fill(x, y, x + rowWidth, y + ENTRY_ROW_HEIGHT - 2, 0xFFA0A0A0);
            context.fill(x + 1, y + 1, x + rowWidth - 1,
                    y + ENTRY_ROW_HEIGHT - 3, 0xE0202020);
        } else if (hovered) {
            context.fill(x, y, x + rowWidth, y + ENTRY_ROW_HEIGHT - 2, 0x70404040);
        }
    }

    private ItemStack entryIcon(ReimbursementEntry entry) {
        if (entry instanceof MoneyEntry) return new ItemStack(Items.GOLD_INGOT);
        ItemEntry item = (ItemEntry) entry;
        return item.itemId() == null
                ? new ItemStack(Items.BARRIER)
                : Registries.ITEM.get(item.itemId()).getDefaultStack();
    }

    private void renderTexturedPanel(
            DrawContext context,
            int x,
            int y,
            int panelWidth,
            int panelHeight,
            int textureScroll
    ) {
        context.drawTexture(RenderPipelines.GUI_TEXTURED, LIST_BACKGROUND, x, y,
                x + panelWidth, y + panelHeight + textureScroll,
                panelWidth, panelHeight, 32, 32);
        context.drawTexture(RenderPipelines.GUI_TEXTURED, LIST_HEADER_SEPARATOR, x, y - 2,
                0.0F, 0.0F, panelWidth, 2, 32, 2);
        context.drawTexture(RenderPipelines.GUI_TEXTURED, LIST_FOOTER_SEPARATOR, x, y + panelHeight,
                0.0F, 0.0F, panelWidth, 2, 32, 2);
    }

    private void renderItemDetails(DrawContext context, Layout layout, ItemEntry item, int mouseX, int mouseY) {
        int y = layout.top() - rightScroll;
        int x = layout.rightX() + 4;
        int contentWidth = rightContentWidth(layout);
        ItemRightLayout rightLayout = itemRightLayout(layout);
        rightLabel(context, "Item Name", x, y + 10, layout);
        rightLabel(context, "Name Preview", x, y + 50, layout);
        renderPreviewLines(context, formattedPreviewLines(item.customName(), false, contentWidth),
                x, y + 64, contentWidth, layout);
        rightLabel(context, "Lore", x, y + rightLayout.loreLabel(), layout);
        rightLabel(context, "Lore Preview", x, y + rightLayout.lorePreviewLabel(), layout);
        renderPreviewLines(context, lorePreviewLines(item, contentWidth),
                x, y + rightLayout.lorePreviewStart(), contentWidth, layout);
        rightLabel(context, "Amount", x, y + rightLayout.amountLabel(), layout);
        rightLabel(context, "Enchantments",
                x, y + rightLayout.enchantLabel(), layout);
        renderEnchantments(context, layout, mouseX, mouseY);
    }

    private void renderDestinationTitle(
            DrawContext context,
            Layout layout,
            ReimbursementEntry entry
    ) {
        int base = entry instanceof ItemEntry ? itemRightLayout(layout).destinationBase() : 38;
        rightLabel(context, "Destination", layout.rightX() + 4,
                layout.top() + base - 15 - rightScroll, layout);
    }

    private void renderEnchantments(DrawContext context, Layout layout, int mouseX, int mouseY) {
        int y = layout.top() + itemRightLayout(layout).enchantmentRows() - rightScroll;
        int x = layout.rightX() + 4;
        boolean scrolling = rightContentHeight() > layout.height();
        int width = rightContentWidth(layout);
        context.enableScissor(layout.rightX() + 2, layout.top() + 2,
                layout.rightX() + layout.rightWidth() - (scrolling ? 8 : 1), layout.bottom() - 2);
        int index = 0;
        for (Map.Entry<Identifier, Integer> enchantment : currentItem().enchantments().entrySet()) {
            int rowY = y + index * 28;
            context.fill(x, rowY, x + width, rowY + 24, 0x60303030);
            String text = enchantmentName(enchantment.getKey());
            context.drawTextWithShadow(textRenderer,
                    textRenderer.trimToWidth(text, Math.max(0, width - 72)),
                    x + 3, rowY + 8, 0xFFFFFFFF);
            int removeX = x + width - 20;
            boolean rowHovered = mouseX >= x && mouseX < x + width
                    && mouseY >= rowY && mouseY < rowY + 24;
            boolean removeHovered = mouseX >= removeX && mouseX < removeX + 18
                    && mouseY >= rowY + 3 && mouseY < rowY + 21;
            if (rowHovered) {
                context.drawGuiTexture(RenderPipelines.GUI_TEXTURED,
                        removeHovered ? REMOVE_HIGHLIGHTED_TEXTURE : REMOVE_TEXTURE,
                        removeX, rowY + 3, 18, 18);
            }
            index++;
        }
        context.disableScissor();
    }

    private void renderConfig(DrawContext context) {
        Layout layout = layout();
        int formWidth = Math.min(460, width - 60);
        int x = (width - formWidth) / 2;
        int y = layout.top() - configScroll;
        label(context, "IGN", x, y + 8, layout);
        label(context, "Discord", x, y + 56, layout);
        context.drawTextWithShadow(textRenderer, Text.literal("/"), x + (formWidth - 6) / 2,
                y + 76, 0xFFCCCCCC);
        label(context, "Reason", x, y + 112, layout);
        label(context, "Ticket", x, y + 218, layout);

        ReimbursementPlan.Preparation preparation = ReimbursementPlan.prepare(draft);
        if (preparation.valid()) {
            int ping = 100;
            if (client.getNetworkHandler() != null) {
                var player = client.getNetworkHandler().getPlayerListEntry(client.getSession().getUsername());
                if (player != null) ping = player.getLatency();
            }
            boolean admin = DMLSConfig.staffRank() == StaffRank.ADMIN;
            ReimbursementEstimate estimate = ReimbursementEstimate.calculate(
                    preparation.plan(), admin, ping, OutboundSpamSafety.ticksUntilSafe(admin));
            context.drawCenteredTextWithShadow(textRenderer,
                    Text.literal("Estimated time: " + estimate.formatted() + " (approximate)"),
                    width / 2, layout.bottom() - 14, 0xFFFFFF55);
        } else {
            context.drawCenteredTextWithShadow(textRenderer, Text.literal(preparation.error()),
                    width / 2, layout.bottom() - 14, 0xFFFF5555);
        }
    }

    private void renderResult(DrawContext context) {
        int panelWidth = Math.min(560, width - 32);
        int panelX = (width - panelWidth) / 2;
        int top = contentTop();
        int bottom = footerTop() - 8;
        renderPanel(context, panelX, top, panelWidth, bottom - top);
        Text title = Text.literal(switch (result.kind()) {
            case SUCCESS -> "Reimbursement completed";
            case SUCCESS_WITH_WARNINGS -> "Reimbursement completed with warnings";
            case DRY_RUN -> "Reimbursement dry-run plan";
            case PREFLIGHT_REJECTED -> "Reimbursement did not start";
            case PARTIAL_FAILURE -> "Reimbursement stopped";
        });
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, top + 8, 0xFFFFFFFF);
        String body = result.requiresLogCopy() ? result.log() : summaryText();
        List<OrderedText> lines = textRenderer.wrapLines(Text.literal(body), panelWidth - 20);
        context.enableScissor(panelX + 4, top + 24, panelX + panelWidth - 8, bottom - 4);
        int y = top + 26 - resultScroll;
        for (OrderedText line : lines) {
            context.drawTextWithShadow(textRenderer, line, panelX + 8, y, 0xFFDDDDDD);
            y += 11;
        }
        context.disableScissor();
        renderPaneScrollbar(context, panelX + panelWidth - 7, top + 24, bottom - top - 28,
                resultScroll, lines.size() * 11 + 4, bottom - top - 28);
    }

    private String summaryText() {
        StringBuilder resultText = new StringBuilder(result.message());
        appendSummary(resultText, result.kind() == ReimbursementResult.Kind.DRY_RUN
                ? "Ordered plan" : "Completed", result.completed());
        appendSummary(resultText, "Retained with staff", result.retainedWithStaff());
        appendSummary(resultText, "Remaining", result.remaining());
        return resultText.toString();
    }

    private static void appendSummary(StringBuilder target, String title, List<String> values) {
        if (values.isEmpty()) return;
        target.append("\n\n").append(title).append(":\n");
        values.forEach(value -> target.append("- ").append(value).append('\n'));
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (result != null) return super.mouseClicked(click, doubled);
        if (handleSuggestionClick(click)) return true;
        if (activeTab == ViewTab.REIMBURSEMENT && clickEntryList(click)) return true;
        if (activeTab == ViewTab.REIMBURSEMENT && !draft.entries().isEmpty()) {
            Layout layout = layout();
            if (currentEntry() instanceof ItemEntry) {
                if (clickItemGrid(click, layout) || clickEnchantment(click, layout)) return true;
            }
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (scrollSuggestionPopup(mouseX, mouseY, verticalAmount)) return true;
        if (result != null) {
            resultScroll = Math.max(0, resultScroll - (int) (verticalAmount * 24));
            return true;
        }
        Layout layout = layout();
        if (activeTab == ViewTab.CONFIG) {
            configScroll = clampedScroll(configScroll, verticalAmount, 290, layout.height());
            layoutPaneChildren();
            return true;
        }
        if (mouseX >= layout.leftX() && mouseX < layout.leftX() + layout.leftWidth()) {
            leftScroll = clampedScroll(leftScroll, verticalAmount, leftContentHeight(), layout.height());
        } else if (mouseX >= layout.centerX() && mouseX < layout.centerX() + layout.centerWidth()) {
            if (!draft.entries().isEmpty() && currentEntry() instanceof ItemEntry
                    && isInsideItemGrid(mouseX, mouseY, layout)) {
                int rows = (filteredItems.size() + GRID_COLUMNS - 1) / GRID_COLUMNS;
                int direction = verticalAmount > 0 ? -1 : verticalAmount < 0 ? 1 : 0;
                centerScroll = Math.clamp(centerScroll + direction * SLOT_SIZE,
                        0, Math.max(0, rows * SLOT_SIZE - GRID_HEIGHT));
            } else {
                centerUiScroll = clampedScroll(centerUiScroll, verticalAmount,
                        centerContentHeight(), layout.height());
            }
        } else if (mouseX >= layout.rightX() && mouseX < layout.rightX() + layout.rightWidth()) {
            rightScroll = clampedScroll(rightScroll, verticalAmount, rightContentHeight(), layout.height());
        } else return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        layoutPaneChildren();
        return true;
    }

    private boolean scrollSuggestionPopup(double mouseX, double mouseY, double amount) {
        if (playerIgn != null && playerIgn.isFocused()
                && suggestions.size() > SUGGESTION_VISIBLE_ROWS) {
            SuggestionPopup popup = suggestionPopup(
                    playerIgn, suggestions.size(), suggestionWindowOffset);
            if (popup.contains(mouseX, mouseY)) {
                suggestionWindowOffset = scrollSuggestionWindow(
                        suggestionWindowOffset, suggestions.size(), amount);
                suggestionIndex = Math.clamp(suggestionIndex,
                        suggestionWindowOffset,
                        suggestionWindowOffset + popup.visibleCount() - 1);
                return true;
            }
        }
        if (enchantSearch != null && enchantSearch.isFocused()
                && filteredEnchantments.size() > SUGGESTION_VISIBLE_ROWS) {
            SuggestionPopup popup = suggestionPopup(
                    enchantSearch, filteredEnchantments.size(), enchantmentSuggestionWindowOffset);
            if (popup.contains(mouseX, mouseY)) {
                enchantmentSuggestionWindowOffset = scrollSuggestionWindow(
                        enchantmentSuggestionWindowOffset, filteredEnchantments.size(), amount);
                enchantmentSuggestionIndex = Math.clamp(enchantmentSuggestionIndex,
                        enchantmentSuggestionWindowOffset,
                        enchantmentSuggestionWindowOffset + popup.visibleCount() - 1);
                return true;
            }
        }
        return false;
    }

    private int scrollSuggestionWindow(int current, int total, double amount) {
        int direction = amount > 0 ? -1 : amount < 0 ? 1 : 0;
        return Math.clamp(current + direction, 0,
                Math.max(0, total - SUGGESTION_VISIBLE_ROWS));
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (result != null && result.requiresLogCopy() && !copied
                && (input.isEscape() || client.options.inventoryKey.matchesKey(input))) return true;
        if (playerIgn != null && playerIgn.isFocused() && !suggestions.isEmpty()) {
            if (input.isTab() || input.key() == GLFW.GLFW_KEY_ENTER
                    || input.key() == GLFW.GLFW_KEY_KP_ENTER) {
                acceptPlayerSuggestion();
                return true;
            }
            if (input.key() == GLFW.GLFW_KEY_DOWN) {
                suggestionIndex = Math.min(suggestions.size() - 1, suggestionIndex + 1);
                suggestionWindowOffset = visibleWindowOffset(
                        suggestionWindowOffset, suggestionIndex, suggestions.size());
                return true;
            }
            if (input.key() == GLFW.GLFW_KEY_UP) {
                suggestionIndex = Math.max(0, suggestionIndex - 1);
                suggestionWindowOffset = visibleWindowOffset(
                        suggestionWindowOffset, suggestionIndex, suggestions.size());
                return true;
            }
        }
        if (enchantSearch != null && enchantSearch.isFocused() && !filteredEnchantments.isEmpty()) {
            if (input.isTab() || input.key() == GLFW.GLFW_KEY_ENTER
                    || input.key() == GLFW.GLFW_KEY_KP_ENTER) {
                acceptEnchantmentSuggestion();
                return true;
            }
            if (input.key() == GLFW.GLFW_KEY_DOWN) {
                enchantmentSuggestionIndex = Math.min(
                        filteredEnchantments.size() - 1, enchantmentSuggestionIndex + 1);
                enchantmentSuggestionWindowOffset = visibleWindowOffset(
                        enchantmentSuggestionWindowOffset,
                        enchantmentSuggestionIndex, filteredEnchantments.size());
                return true;
            }
            if (input.key() == GLFW.GLFW_KEY_UP) {
                enchantmentSuggestionIndex = Math.max(0, enchantmentSuggestionIndex - 1);
                enchantmentSuggestionWindowOffset = visibleWindowOffset(
                        enchantmentSuggestionWindowOffset,
                        enchantmentSuggestionIndex, filteredEnchantments.size());
                return true;
            }
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return result == null || !result.requiresLogCopy() || copied;
    }

    @Override
    public void close() {
        if (result != null && result.requiresLogCopy() && !copied) return;
        super.close();
    }

    private boolean clickItemGrid(Click click, Layout layout) {
        int gridX = layout.centerX() + Math.max(4, (layout.centerWidth() - GRID_COLUMNS * SLOT_SIZE) / 2);
        int gridY = layout.top() + 68 - centerUiScroll;
        if (click.x() < gridX || click.x() >= gridX + GRID_COLUMNS * SLOT_SIZE
                || click.y() < gridY || click.y() >= gridY + GRID_HEIGHT) return false;
        int column = (int) (click.x() - gridX) / SLOT_SIZE;
        int row = ((int) (click.y() - gridY) + centerScroll) / SLOT_SIZE;
        int index = row * GRID_COLUMNS + column;
        if (index >= 0 && index < filteredItems.size()) {
            updateItem(currentItem().withItemId(filteredItems.get(index).id()));
            return true;
        }
        return false;
    }

    private boolean clickEntryList(Click click) {
        Layout layout = layout();
        int rowX = layout.leftX() + 4;
        int rowWidth = entryRowWidth(layout);
        if (click.x() < rowX || click.x() >= rowX + rowWidth
                || click.y() < layout.top() || click.y() >= layout.bottom()) return false;
        int relativeY = (int) click.y() - layout.top() - 4 + leftScroll;
        if (relativeY < 0) return false;
        int index = relativeY / ENTRY_ROW_HEIGHT;
        int insideRow = relativeY % ENTRY_ROW_HEIGHT;
        if (insideRow >= ENTRY_ROW_HEIGHT - 2) return false;
        if (index == draft.entries().size()) {
            addEntry();
            return true;
        }
        if (index < 0 || index >= draft.entries().size()) return false;

        if (index == selectedEntry) {
            int rowY = layout.top() + 4 + index * ENTRY_ROW_HEIGHT - leftScroll;
            int removeX = rowX + rowWidth - 23;
            int removeY = rowY + 8;
            if (click.x() >= removeX && click.x() < removeX + 18
                    && click.y() >= removeY && click.y() < removeY + 18) {
                removeEntry(index);
                return true;
            }
        }
        selectEntry(index);
        return true;
    }

    private boolean isInsideItemGrid(double mouseX, double mouseY, Layout layout) {
        int gridX = layout.centerX() + Math.max(4,
                (layout.centerWidth() - GRID_COLUMNS * SLOT_SIZE) / 2);
        int gridY = layout.top() + 68 - centerUiScroll;
        return mouseX >= gridX - 4 && mouseX < gridX + GRID_COLUMNS * SLOT_SIZE + 10
                && mouseY >= gridY - 4 && mouseY < gridY + GRID_HEIGHT + 4;
    }

    private boolean clickEnchantment(Click click, Layout layout) {
        int x = layout.rightX() + 4;
        int width = rightContentWidth(layout);
        int firstY = layout.top() + itemRightLayout(layout).enchantmentRows() - rightScroll;
        if (click.y() < firstY) return false;
        int index = (int) (click.y() - firstY) / 28;
        if (index < 0 || index >= currentItem().enchantments().size()) return false;
        int rowY = firstY + index * 28;
        int removeX = x + width - 20;
        if (click.x() < removeX || click.x() >= removeX + 18
                || click.y() < rowY + 3 || click.y() >= rowY + 21) return false;
        Identifier id = new ArrayList<>(currentItem().enchantments().keySet()).get(index);
        Map<Identifier, Integer> values = new LinkedHashMap<>(currentItem().enchantments());
        values.remove(id);
        updateItem(currentItem().withEnchantments(values));
        clearAndInit();
        return true;
    }

    private void refreshItems(String query) {
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        filteredItems = Registries.ITEM.getIds().stream()
                .filter(id -> !id.equals(Identifier.ofVanilla("air")))
                .filter(id -> client == null || client.world == null
                        || Registries.ITEM.get(id).getRequiredFeatures()
                        .isSubsetOf(client.world.getEnabledFeatures()))
                .map(id -> new ItemChoice(id, Registries.ITEM.get(id).getDefaultStack()))
                .filter(choice -> needle.isEmpty()
                        || choice.id().toString().contains(needle)
                        || choice.stack().getName().getString().toLowerCase(Locale.ROOT).contains(needle))
                .sorted(Comparator.comparing(choice -> choice.stack().getName().getString(),
                        String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private void loadEnchantments() {
        if (client == null || client.world == null) {
            availableEnchantments = List.of();
            return;
        }
        availableEnchantments = client.world.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT)
                .streamEntries()
                .filter(entry -> entry.registryKey().getValue().getNamespace().equals(Identifier.DEFAULT_NAMESPACE))
                .map(entry -> new EnchantmentChoice(entry.registryKey().getValue(),
                        Enchantment.getName(entry, 1).getString().replaceAll("\\s+[IVXLCDM]+$", "")))
                .sorted(Comparator.comparing(EnchantmentChoice::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private void refreshEnchantments(String query) {
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        filteredEnchantments = availableEnchantments.stream()
                .filter(choice -> !currentItem().enchantments().containsKey(choice.id()))
                .filter(choice -> needle.isEmpty()
                        || choice.id().toString().contains(needle)
                        || choice.name().toLowerCase(Locale.ROOT).contains(needle))
                .toList();
        enchantmentSuggestionIndex = Math.clamp(enchantmentSuggestionIndex,
                0, Math.max(0, filteredEnchantments.size() - 1));
        enchantmentSuggestionWindowOffset = visibleWindowOffset(
                enchantmentSuggestionWindowOffset, enchantmentSuggestionIndex,
                filteredEnchantments.size());
    }

    private String enchantmentName(Identifier id) {
        return availableEnchantments.stream()
                .filter(choice -> choice.id().equals(id))
                .map(EnchantmentChoice::name)
                .findFirst()
                .orElseGet(() -> id.getPath().replace('_', ' '));
    }

    private void refreshSuggestions() {
        if (playerIgn == null || !playerIgn.isFocused()) {
            suggestions = List.of();
        } else {
            String needle = playerIgn.getText().trim().toLowerCase(Locale.ROOT);
            suggestions = ClientUtils.getOnlinePlayerNames(client).stream()
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(needle))
                    .toList();
            suggestionIndex = Math.clamp(suggestionIndex, 0, Math.max(0, suggestions.size() - 1));
            suggestionWindowOffset = visibleWindowOffset(
                    suggestionWindowOffset, suggestionIndex, suggestions.size());
        }
        if (enchantSearch != null && enchantSearch.isFocused()) {
            refreshEnchantments(enchantSearch.getText());
        }
    }

    private void renderSuggestions(DrawContext context, int mouseX, int mouseY) {
        if (playerIgn != null && playerIgn.isFocused() && !suggestions.isEmpty()) {
            renderSuggestionPopup(context, playerIgn, suggestions,
                    suggestionIndex, suggestionWindowOffset);
        } else if (enchantSearch != null && enchantSearch.isFocused()
                && !filteredEnchantments.isEmpty()) {
            renderSuggestionPopup(context, enchantSearch,
                    filteredEnchantments.stream().map(EnchantmentChoice::name).toList(),
                    enchantmentSuggestionIndex, enchantmentSuggestionWindowOffset);
        }
    }

    private void renderSuggestionPopup(
            DrawContext context,
            TextFieldWidget field,
            List<String> values,
            int selected,
            int windowOffset
    ) {
        SuggestionPopup popup = suggestionPopup(field, values.size(), windowOffset);
        context.createNewRootLayer();
        if (popup.windowOffset() > 0) renderSuggestionOverflowDots(context, popup, true);
        if (popup.windowOffset() + popup.visibleCount() < popup.totalCount()) {
            renderSuggestionOverflowDots(context, popup, false);
        }
        for (int visibleIndex = 0; visibleIndex < popup.visibleCount(); visibleIndex++) {
            int index = popup.windowOffset() + visibleIndex;
            int rowY = popup.y() + visibleIndex * SUGGESTION_ROW_HEIGHT;
            context.fill(popup.x(), rowY, popup.x() + popup.width(),
                    rowY + SUGGESTION_ROW_HEIGHT, SUGGESTION_BACKGROUND);
            context.drawTextWithShadow(textRenderer,
                    textRenderer.trimToWidth(values.get(index), popup.width() - 2),
                    popup.x() + 1, rowY + 2,
                    index == selected ? SUGGESTION_SELECTED_TEXT : SUGGESTION_TEXT);
        }
    }

    private void renderSuggestionOverflowDots(
            DrawContext context,
            SuggestionPopup popup,
            boolean top
    ) {
        int y = top ? popup.y() - 1 : popup.y() + popup.height();
        for (int x = 0; x < popup.width(); x += 2) {
            context.fill(popup.x() + x, y, popup.x() + x + 1, y + 1, 0xFFFFFFFF);
        }
    }

    private boolean handleSuggestionClick(Click click) {
        if (playerIgn != null && playerIgn.isFocused() && !suggestions.isEmpty()) {
            SuggestionPopup popup = suggestionPopup(
                    playerIgn, suggestions.size(), suggestionWindowOffset);
            if (popup.contains(click)) {
                suggestionIndex = popup.index(click);
                acceptPlayerSuggestion();
                return true;
            }
        }
        if (enchantSearch != null && enchantSearch.isFocused() && !filteredEnchantments.isEmpty()) {
            SuggestionPopup popup = suggestionPopup(
                    enchantSearch, filteredEnchantments.size(), enchantmentSuggestionWindowOffset);
            if (popup.contains(click)) {
                enchantmentSuggestionIndex = popup.index(click);
                acceptEnchantmentSuggestion();
                return true;
            }
        }
        return false;
    }

    private void acceptPlayerSuggestion() {
        if (suggestions.isEmpty()) return;
        playerIgn.setText(suggestions.get(suggestionIndex));
        playerIgn.setCursorToEnd(false);
        setFocused(null);
        playerIgn.setFocused(false);
        suggestions = List.of();
    }

    private void acceptEnchantmentSuggestion() {
        if (filteredEnchantments.isEmpty()) return;
        Identifier id = filteredEnchantments.get(enchantmentSuggestionIndex).id();
        Map<Identifier, Integer> values = new LinkedHashMap<>(currentItem().enchantments());
        values.putIfAbsent(id, 1);
        updateItem(currentItem().withEnchantments(values));
        setFocused(null);
        enchantSearch.setFocused(false);
        clearAndInit();
    }

    private SuggestionPopup suggestionPopup(TextFieldWidget field, int totalCount, int windowOffset) {
        Layout layout = layout();
        int visibleCount = Math.min(totalCount, SUGGESTION_VISIBLE_ROWS);
        windowOffset = Math.clamp(windowOffset, 0, Math.max(0, totalCount - visibleCount));
        int popupHeight = visibleCount * SUGGESTION_ROW_HEIGHT;
        int centerY = (layout.top() + layout.bottom()) / 2;
        boolean openUp = field.getY() + field.getHeight() / 2 > centerY;
        int y = openUp ? field.getY() - popupHeight - 1 : field.getBottom() + 1;
        y = Math.clamp(y, layout.top(), Math.max(layout.top(), layout.bottom() - popupHeight));
        int x = Math.clamp(field.getX(), 0, Math.max(0, width - field.getWidth()));
        return new SuggestionPopup(
                x, y, field.getWidth(), popupHeight, totalCount, visibleCount, windowOffset);
    }

    private int visibleWindowOffset(int current, int selected, int total) {
        int visible = Math.min(total, SUGGESTION_VISIBLE_ROWS);
        int maximum = Math.max(0, total - visible);
        current = Math.clamp(current, 0, maximum);
        if (selected < current) return selected;
        if (selected >= current + visible) return Math.min(maximum, selected - visible + 1);
        return current;
    }

    private void updateMoneyAmount(String value) {
        try {
            updateEntry(((MoneyEntry) currentEntry()).withAmount(new BigDecimal(value)));
        } catch (NumberFormatException ignored) {
        }
    }

    private void updateEnchantmentLevel(Identifier enchantment, String value) {
        try {
            int level = Integer.parseInt(value);
            if (level < 1 || level > 255) return;
            Map<Identifier, Integer> values = new LinkedHashMap<>(currentItem().enchantments());
            values.put(enchantment, level);
            updateItem(currentItem().withEnchantments(values));
        } catch (NumberFormatException ignored) {
        }
    }

    private void addEntry() {
        draft.add(ItemEntry.empty());
        selectedEntry = draft.entries().size() - 1;
        clearAndInit();
    }

    private void removeEntry(int index) {
        if (index < 0 || index >= draft.entries().size()) return;
        draft.remove(index);
        selectedEntry = Math.clamp(selectedEntry, 0, Math.max(0, draft.entries().size() - 1));
        clearAndInit();
    }

    private void selectEntry(int index) {
        selectedEntry = index;
        clearAndInit();
    }

    private void changeType(boolean item) {
        ReimbursementEntry old = currentEntry();
        ReimbursementEntry replacement = item
                ? ItemEntry.empty().withDestination(old.destination() == Destination.CONTAINER
                ? Destination.CONTAINER : old.destination()).withPlayerIgn(old.playerIgn())
                : MoneyEntry.empty().withDestination(old.destination()).withPlayerIgn(old.playerIgn());
        updateEntry(replacement);
        clearAndInit();
    }

    private void selectContainers() {
        ItemEntry item = currentItem();
        ContainerSelectionController.begin(client, this, item.containers(), selected ->
                updateItem(currentItem().withContainers(selected)));
    }

    private void switchTab(ViewTab tab) {
        activeTab = tab;
        clearAndInit();
    }

    private void start() {
        ReimbursementPlan.Preparation preparation = ReimbursementPlan.prepare(draft);
        if (!preparation.valid()) {
            validation = preparation.error();
            return;
        }
        validation = "";
        module.start(client, preparation.plan(), this);
    }

    private void next() {
        if (draft.entries().isEmpty()) {
            validation = "Add at least one item or money entry.";
            return;
        }
        Map<String, Integer> nonContainerStacks = new LinkedHashMap<>();
        int totalStacks = 0;
        for (ReimbursementEntry entry : draft.entries()) {
            if (entry instanceof MoneyEntry money
                    && (money.amount().scale() > 2 || money.amount().compareTo(new BigDecimal("0.01")) < 0
                    || money.amount().compareTo(ReimbursementPlan.MAX_MONEY) > 0)) {
                validation = "Money must be between 0.01 and 999,999,999.99.";
                return;
            }
            if (entry.destination() == Destination.PLAYER
                    && !com.duperknight.client.utils.InputValidators.isUsername(entry.playerIgn().trim())) {
                validation = "Every Player destination needs a valid IGN.";
                return;
            }
            if (entry instanceof ItemEntry item
                    && (item.itemId() == null || item.amount() < 1
                    || item.amount() > ReimbursementPlan.MAX_ITEM_AMOUNT
                    || item.destination() == Destination.CONTAINER && item.containers().isEmpty())) {
                validation = "Finish configuring every item before continuing.";
                return;
            }
            if (entry instanceof ItemEntry item) {
                if (!item.customName().isBlank()
                        && !PrefixTextFormatter.serializeJson(item.customName()).valid()) {
                    validation = "An item name contains invalid formatting.";
                    return;
                }
                if (item.lore().stream().anyMatch(line ->
                        !PrefixTextFormatter.serializeJson(line).valid())) {
                    validation = "An item lore line contains invalid formatting.";
                    return;
                }
                if (item.enchantments().values().stream()
                        .anyMatch(level -> level < 1 || level > ReimbursementPlan.MAX_ENCHANTMENT_LEVEL)) {
                    validation = "Enchantment levels must be between 1 and 255.";
                    return;
                }
                int stacks = ReimbursementPlan.stackCount(item);
                totalStacks += stacks;
                if (item.destination() != Destination.CONTAINER) {
                    String target = item.destination() == Destination.ME
                            ? "me" : "player:" + item.playerIgn().trim().toLowerCase(Locale.ROOT);
                    if (nonContainerStacks.merge(target, stacks, Integer::sum)
                            > ReimbursementPlan.PLAYER_INVENTORY_SLOTS) {
                        validation = "A destination needs more than 36 slots. Reduce the items or use a container.";
                        return;
                    }
                }
            }
        }
        ReimbursementCommandPlanner.BuildResult commands = ReimbursementCommandPlanner.build(
                new ReimbursementPlan(draft.snapshot(), totalStacks));
        if (!commands.valid()) {
            validation = commands.error();
            return;
        }
        validation = "";
        switchTab(ViewTab.CONFIG);
    }

    public Screen returnParent() {
        return parent;
    }

    private void copyResult() {
        client.keyboard.setClipboard(result.log());
        copied = true;
        if (doneButton != null) doneButton.active = true;
    }

    private void done() {
        if (result.requiresLogCopy() && !copied) return;
        if (result.requiresLogCopy()) module.clearDraft();
        client.setScreen(parent);
    }

    private ReimbursementEntry currentEntry() {
        return draft.entries().get(selectedEntry);
    }

    private ItemEntry currentItem() {
        return (ItemEntry) currentEntry();
    }

    private void updateItem(ItemEntry entry) {
        updateEntry(entry);
    }

    private void updateEntry(ReimbursementEntry entry) {
        draft.set(selectedEntry, entry);
    }

    private Text entryLabel(ReimbursementEntry entry) {
        String target = switch (entry.destination()) {
            case ME -> "Me";
            case PLAYER -> entry.playerIgn().isBlank() ? "Player" : entry.playerIgn();
            case CONTAINER -> "Container";
        };
        if (entry instanceof MoneyEntry money) {
            return Text.literal("$" + money.amount().stripTrailingZeros().toPlainString() + " → " + target);
        }
        ItemEntry item = (ItemEntry) entry;
        PrefixTextFormatter.PlainResult customName = PrefixTextFormatter.plainText(item.customName());
        String name = customName.valid() ? customName.text()
                : item.itemId() == null ? "New item"
                : Registries.ITEM.get(item.itemId()).getDefaultStack().getName().getString();
        return Text.literal(item.amount() + "x " + name + " → " + target);
    }

    private void label(DrawContext context, String value, int x, int y, Layout layout) {
        if (y >= layout.top() && y < layout.bottom()) {
            context.drawTextWithShadow(textRenderer, Text.literal(value), x, y, 0xFFCCCCCC);
        }
    }

    private void rightLabel(DrawContext context, String value, int x, int y, Layout layout) {
        if (y < layout.top() || y >= layout.bottom()) return;
        int maxWidth = rightContentWidth(layout);
        context.enableScissor(x, layout.top(), x + maxWidth, layout.bottom());
        context.drawTextWithShadow(textRenderer,
                textRenderer.trimToWidth(value, maxWidth), x, y, 0xFFCCCCCC);
        context.disableScissor();
    }

    private void renderPreviewLines(
            DrawContext context,
            List<OrderedText> lines,
            int x,
            int y,
            int maxWidth,
            Layout layout
    ) {
        context.enableScissor(x, layout.top(), x + maxWidth, layout.bottom());
        for (OrderedText line : lines) {
            if (y >= layout.top() && y < layout.bottom()) {
                context.drawTextWithShadow(textRenderer, line, x, y, 0xFFFFFFFF);
            }
            y += 11;
        }
        context.disableScissor();
    }

    private List<OrderedText> formattedPreviewLines(String value, boolean blankAsSpace, int maxWidth) {
        if (value == null || value.isEmpty()) {
            if (!blankAsSpace) return List.of();
            value = " ";
        }
        PrefixTextFormatter.ParseResult preview = PrefixTextFormatter.parse(value);
        return preview.valid() ? textRenderer.wrapLines(preview.preview(), maxWidth) : List.of();
    }

    private List<OrderedText> lorePreviewLines(ItemEntry item, int maxWidth) {
        List<OrderedText> lines = new ArrayList<>();
        for (String loreLine : item.lore()) {
            lines.addAll(formattedPreviewLines(loreLine, true, maxWidth));
        }
        return lines;
    }

    private void renderPaneScrollbar(
            DrawContext context,
            int x,
            int y,
            int height,
            int scroll,
            int contentHeight,
            int viewportHeight
    ) {
        int max = Math.max(0, contentHeight - viewportHeight);
        if (max <= 0 || height <= 0) return;
        int thumb = scrollbarThumbHeight(height, viewportHeight, contentHeight);
        int thumbY = y + scroll * Math.max(0, height - thumb) / max;
        renderVanillaScrollbar(context, x, y, height, thumbY, thumb);
    }

    private int clampedScroll(int current, double verticalAmount, int contentHeight, int viewportHeight) {
        return Math.clamp(current - (int) (verticalAmount * 24),
                0, Math.max(0, contentHeight - viewportHeight));
    }

    private int leftContentHeight() {
        return Math.max(layout().height(), (draft.entries().size() + 1) * ENTRY_ROW_HEIGHT + 8);
    }

    private int entryRowWidth(Layout layout) {
        int rightPadding = leftContentHeight() > layout.height() ? 12 : 4;
        return layout.leftWidth() - 4 - rightPadding;
    }

    private int rightContentWidth(Layout layout) {
        if (!draft.entries().isEmpty() && currentEntry() instanceof ItemEntry) {
            return itemRightLayout(layout).contentWidth();
        }
        return layout.rightWidth() - 8;
    }

    private int centerContentHeight() {
        return Math.max(layout().height(), GRID_HEIGHT + 80);
    }

    private int rightContentHeight() {
        if (draft.entries().isEmpty() || currentEntry() instanceof MoneyEntry) return layout().height();
        Layout layout = layout();
        return Math.max(layout.height(), itemRightLayout(layout).contentHeight());
    }

    private ItemRightLayout itemRightLayout(Layout layout) {
        int fullWidth = Math.max(1, layout.rightWidth() - 8);
        ItemRightLayout withoutScrollbar = calculateItemRightLayout(layout, fullWidth);
        if (withoutScrollbar.contentHeight() <= layout.height()) return withoutScrollbar;
        return calculateItemRightLayout(layout, Math.max(1, layout.rightWidth() - 16));
    }

    private ItemRightLayout calculateItemRightLayout(Layout layout, int contentWidth) {
        ItemEntry item = currentItem();
        int nameLines = Math.max(1,
                formattedPreviewLines(item.customName(), false, contentWidth).size());
        int loreLabel = 64 + nameLines * 11 + 5;
        int loreField = loreLabel + 13;
        int lorePreviewLabel = loreField + 64;
        int lorePreviewStart = lorePreviewLabel + 13;
        int loreLines = Math.max(1, lorePreviewLines(item, contentWidth).size());
        int amountLabel = lorePreviewStart + loreLines * 11 + 6;
        int amountField = amountLabel + 13;
        int enchantLabel = amountField + 30;
        int enchantSearch = enchantLabel + 15;
        int enchantmentRows = enchantSearch + 28;
        int destinationBase = enchantmentRows + 18 + item.enchantments().size() * 28;
        return new ItemRightLayout(contentWidth, loreLabel, loreField,
                lorePreviewLabel, lorePreviewStart, amountLabel, amountField,
                enchantLabel, enchantSearch, enchantmentRows,
                destinationBase, destinationBase + 70);
    }

    private Layout layout() {
        int top = contentTop();
        int bottom = footerTop() - 8;
        int available = width - 24 - PANE_GAP * 2;
        int side = Math.clamp(available * 27 / 100, 150, 300);
        int center = Math.max(GRID_COLUMNS * SLOT_SIZE + 20, available - side * 2);
        if (center + side * 2 > available) {
            side = Math.max(120, (available - center) / 2);
        }
        int x = (width - (side * 2 + center + PANE_GAP * 2)) / 2;
        return new Layout(x, x + side + PANE_GAP, x + side + center + PANE_GAP * 2,
                side, center, side, top, bottom);
    }

    private int contentTop() {
        return settledHeaderHeight() + 8;
    }

    private int selectedContainerCount() {
        return draft.entries().stream()
                .filter(entry -> entry instanceof ItemEntry)
                .map(ItemEntry.class::cast)
                .mapToInt(item -> item.containers().size())
                .sum();
    }

    private int tabTotalWidth() {
        return Math.min(400, Math.max(220, width - scaled(380)));
    }

    private void renderHeaderSeparatorOutsideTabs(DrawContext context) {
        int totalWidth = tabTotalWidth();
        int tabX = (width - totalWidth) / 2;
        int separatorY = settledHeaderHeight() - 2;
        if (tabX > 0) {
            context.drawTexture(RenderPipelines.GUI_TEXTURED, LIST_HEADER_SEPARATOR,
                    0, separatorY, 0.0F, 0.0F, tabX, 2, 32, 2);
        }
        int rightX = tabX + totalWidth;
        if (rightX < width) {
            context.drawTexture(RenderPipelines.GUI_TEXTURED, LIST_HEADER_SEPARATOR,
                    rightX, separatorY, 0.0F, 0.0F, width - rightX, 2, 32, 2);
        }
    }

    private int footerTop() {
        return height - FOOTER_TOP_OFFSET;
    }

    private record PaneChild(ClickableWidget widget, Pane pane, int offset) {
    }

    private record ItemChoice(Identifier id, ItemStack stack) {
    }

    private record EnchantmentChoice(Identifier id, String name) {
    }

    private record SuggestionPopup(
            int x,
            int y,
            int width,
            int height,
            int totalCount,
            int visibleCount,
            int windowOffset
    ) {
        boolean contains(Click click) {
            return contains(click.x(), click.y());
        }

        boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + width
                    && mouseY >= y && mouseY < y + height;
        }

        int index(Click click) {
            int visibleIndex = Math.clamp(
                    (int) (click.y() - y) / SUGGESTION_ROW_HEIGHT, 0, visibleCount - 1);
            return windowOffset + visibleIndex;
        }
    }

    private record ItemRightLayout(
            int contentWidth,
            int loreLabel,
            int loreField,
            int lorePreviewLabel,
            int lorePreviewStart,
            int amountLabel,
            int amountField,
            int enchantLabel,
            int enchantSearch,
            int enchantmentRows,
            int destinationBase,
            int contentHeight
    ) {
    }

    private record Layout(
            int leftX,
            int centerX,
            int rightX,
            int leftWidth,
            int centerWidth,
            int rightWidth,
            int top,
            int bottom
    ) {
        int height() {
            return bottom - top;
        }
    }
}
