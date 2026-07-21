package com.duperknight.client.gui.modules;

import com.duperknight.DMLS;
import com.duperknight.client.gui.DMLSMenuScreen;
import com.duperknight.client.rulebook.DocumentBlock;
import com.duperknight.client.rulebook.DocumentBlock.ImageBlock;
import com.duperknight.client.rulebook.DocumentBlock.InlineItem;
import com.duperknight.client.rulebook.DocumentBlock.RichText;
import com.duperknight.client.rulebook.DocumentBlock.RuleBox;
import com.duperknight.client.rulebook.DocumentBlock.Spacer;
import com.duperknight.client.rulebook.DocumentBlock.TableBlock;
import com.duperknight.client.rulebook.DocumentBlock.TableCell;
import com.duperknight.client.rulebook.DocumentBlock.TextBlock;
import com.duperknight.client.rulebook.DocumentBlock.TextRun;
import com.duperknight.client.rulebook.RulebookDocument;
import com.duperknight.client.rulebook.RulebookRule;
import com.duperknight.client.rulebook.RulebookService;
import com.duperknight.client.rulebook.RulebookSnapshot;
import com.duperknight.client.rulebook.RulebookStatus;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Native continuous reader for the anonymous Google Docs export. */
public final class RulebookScreen extends DMLSMenuScreen {
    private static final int LINE_GAP = 2;
    private static final int BLOCK_GAP = 6;
    private static final int RULE_RAIL_WIDTH = 58;
    private static final int RULE_PADDING = 10;
    private static final int TABLE_PADDING = 8;
    private static final int LIST_BASE_INDENT = 20;
    private static final int LIST_LEVEL_INDENT = 20;
    private static final int MAX_IMAGE_WIDTH = 360;
    private static final int WHITE_BORDER = 0xFFD8D8D8;
    private static final int FIND_HIGHLIGHT = 0x7046D369;
    private static final int FIND_HIGHLIGHT_ACTIVE = 0xFF35B957;
    private static final ItemStack GOLD_INGOT = new ItemStack(Items.GOLD_INGOT);
    private static final ItemStack IRON_INGOT = new ItemStack(Items.IRON_INGOT);
    private static final ItemStack COPPER_INGOT = new ItemStack(Items.COPPER_INGOT);

    private final String initialRuleId;
    private final Map<ImageBlock, Texture> textures = new IdentityHashMap<>();
    private final Map<Layout, List<SearchMatch>> matchesByLayout = new IdentityHashMap<>();
    private List<Layout> layouts = List.of();
    private List<SearchMatch> findMatches = List.of();
    private RulebookSnapshot snapshot;
    private RulebookRule selectedRule;
    private ButtonWidget reloadButton;
    private TextFieldWidget findField;
    private ButtonWidget findPreviousButton;
    private ButtonWidget findNextButton;
    private ButtonWidget findCloseButton;
    private int findPanelX;
    private int findPanelY;
    private int findPanelWidth;
    private int findPanelHeight;
    private int findCountX;
    private int findCountWidth;
    private int documentX;
    private int documentWidth;
    private int viewerTop;
    private int viewerBottom;
    private long observedRevision;
    private boolean initialAnchorApplied;
    private boolean findOpen;
    private String findQuery = "";
    private int selectedFindIndex = -1;

    public RulebookScreen(Screen parent, String initialRuleId) {
        super(Text.translatable("dmls.rulebook.title"), parent);
        this.initialRuleId = initialRuleId;
    }

    @Override
    protected void init() {
        snapshot = RulebookService.shared().snapshot();
        observedRevision = snapshot.revision();
        documentWidth = Math.min(scaled(620), width - scaled(34));
        documentX = (width - documentWidth) / 2;
        viewerTop = scaled(24);
        viewerBottom = height - FOOTER_TOP_OFFSET - scaled(6);

        RulebookDocument document = snapshot.document();
        if (document == null) {
            layouts = List.of();
            configureScrollableContent(viewerTop, viewerBottom, scaled(48));
        } else {
            layouts = layoutDocument(document);
            int contentHeight = layouts.isEmpty() ? scaled(48)
                    : layouts.getLast().y + layouts.getLast().height + scaled(12);
            configureScrollableContent(viewerTop, viewerBottom, contentHeight);
            if (selectedRule != null) {
                selectedRule = document.rule(selectedRule.id()).filter(RulebookRule::punishable).orElse(null);
            }
            RulebookRule requestedRule = initialRuleId == null ? null : document.rule(initialRuleId).orElse(null);
            if (selectedRule == null && requestedRule != null && requestedRule.punishable()) selectedRule = requestedRule;
            RulebookRule anchorRule = requestedRule != null ? requestedRule : selectedRule;
            if (!initialAnchorApplied && anchorRule != null) {
                Layout anchor = layouts.stream().filter(layout -> layout.block instanceof RuleBox box
                        && box.ruleId().equalsIgnoreCase(anchorRule.id())).findFirst().orElse(null);
                if (anchor != null) setContentScrollOffset(Math.max(0, anchor.y - scaled(8)));
                initialAnchorApplied = true;
            }
        }

        addDrawableChild(ButtonWidget.builder(ScreenTexts.BACK, button -> close())
                .dimensions(leftPairedButtonX(), footerButtonY(), pairedButtonWidth(), STANDARD_BUTTON_HEIGHT).build());
        reloadButton = addDrawableChild(ButtonWidget.builder(Text.empty(),
                        button -> RulebookService.shared().refresh())
                .dimensions(contentScrollbarX() - STANDARD_BUTTON_HEIGHT - scaled(5),
                        viewerBottom - STANDARD_BUTTON_HEIGHT - scaled(5),
                        STANDARD_BUTTON_HEIGHT, STANDARD_BUTTON_HEIGHT).build());
        reloadButton.active = !snapshot.refreshing();
        ButtonWidget makeLog = addDrawableChild(ButtonWidget.builder(logLabel(), button -> {
                    if (selectedRule != null) client.setScreen(new BanLogScreen(this, selectedRule));
                }).dimensions(rightPairedButtonX(), footerButtonY(), pairedButtonWidth(), STANDARD_BUTTON_HEIGHT).build());
        makeLog.active = selectedRule != null;
        if (selectedRule == null) {
            makeLog.setTooltip(Tooltip.of(Text.translatable("dmls.rulebook.select_rule_tooltip")));
        }

        initFindWidgets();
        rebuildFindMatches(false);
    }

    private Text logLabel() {
        return selectedRule == null ? Text.translatable("dmls.rulebook.make_log_disabled")
                : Text.translatable("dmls.rulebook.make_log", selectedRule.id());
    }

    private void initFindWidgets() {
        findPanelWidth = Math.min(scaled(320), width - scaled(8));
        findPanelHeight = STANDARD_BUTTON_HEIGHT + scaled(8);
        findPanelX = width - findPanelWidth - scaled(6);
        findPanelY = scaled(4);
        int inset = scaled(4);
        int gap = scaled(3);
        int buttonSize = STANDARD_BUTTON_HEIGHT;
        findCountWidth = scaled(72);
        int fieldWidth = Math.max(scaled(60), findPanelWidth - inset * 2 - findCountWidth
                - buttonSize * 3 - gap * 4);
        int widgetY = findPanelY + (findPanelHeight - STANDARD_BUTTON_HEIGHT) / 2;

        findField = addDrawableChild(new TextFieldWidget(textRenderer, findPanelX + inset, widgetY,
                fieldWidth, STANDARD_BUTTON_HEIGHT, Text.literal("Find in rulebook")));
        findField.setMaxLength(128);
        findField.setPlaceholder(Text.literal("Find"));
        findField.setText(findQuery);
        findField.setChangedListener(this::onFindChanged);

        findCountX = findField.getX() + findField.getWidth() + gap;
        int buttonX = findCountX + findCountWidth + gap;
        findPreviousButton = addDrawableChild(ButtonWidget.builder(Text.literal("↑"), button -> selectFindResult(-1))
                .dimensions(buttonX, widgetY, buttonSize, STANDARD_BUTTON_HEIGHT).build());
        findNextButton = addDrawableChild(ButtonWidget.builder(Text.literal("↓"), button -> selectFindResult(1))
                .dimensions(buttonX + buttonSize + gap, widgetY, buttonSize, STANDARD_BUTTON_HEIGHT).build());
        findCloseButton = addDrawableChild(ButtonWidget.builder(Text.literal("×"), button -> closeFind())
                .dimensions(buttonX + (buttonSize + gap) * 2, widgetY, buttonSize, STANDARD_BUTTON_HEIGHT).build());
        layoutFindWidgets();
        setFindWidgetsVisible(findOpen);
    }

    private void layoutFindWidgets() {
        if (findField == null) return;
        int inset = scaled(4);
        int gap = scaled(3);
        int buttonSize = STANDARD_BUTTON_HEIGHT;
        int widgetY = findPanelY + (findPanelHeight - STANDARD_BUTTON_HEIGHT) / 2;
        String widestCount = findMatches.isEmpty() ? "0 of 0"
                : findMatches.size() + " of " + findMatches.size();
        findCountWidth = Math.max(scaled(64), textRenderer.getWidth(widestCount) + scaled(12));
        int fieldWidth = Math.max(scaled(60), findPanelWidth - inset * 2 - findCountWidth
                - buttonSize * 3 - gap * 4);
        findField.setDimensionsAndPosition(fieldWidth, STANDARD_BUTTON_HEIGHT, findPanelX + inset, widgetY);
        findCountX = findField.getRight() + gap;
        int buttonX = findCountX + findCountWidth + gap;
        findPreviousButton.setDimensionsAndPosition(buttonSize, STANDARD_BUTTON_HEIGHT, buttonX, widgetY);
        findNextButton.setDimensionsAndPosition(buttonSize, STANDARD_BUTTON_HEIGHT,
                buttonX + buttonSize + gap, widgetY);
        findCloseButton.setDimensionsAndPosition(buttonSize, STANDARD_BUTTON_HEIGHT,
                buttonX + (buttonSize + gap) * 2, widgetY);
    }

    private void onFindChanged(String query) {
        findQuery = query;
        rebuildFindMatches(true);
    }

    private void openFind() {
        findOpen = true;
        setFindWidgetsVisible(true);
        rebuildFindMatches(true);
        setFocused(findField);
        findField.setFocused(true);
        findField.setCursorToEnd(false);
    }

    private void closeFind() {
        findOpen = false;
        setFindWidgetsVisible(false);
        setFocused(null);
        findMatches = List.of();
        matchesByLayout.clear();
        selectedFindIndex = -1;
    }

    private void setFindWidgetsVisible(boolean visible) {
        if (findField != null) findField.setVisible(visible);
        if (findPreviousButton != null) findPreviousButton.visible = visible;
        if (findNextButton != null) findNextButton.visible = visible;
        if (findCloseButton != null) findCloseButton.visible = visible;
        syncFindButtons();
    }

    private void rebuildFindMatches(boolean resetSelection) {
        matchesByLayout.clear();
        if (!findOpen || findQuery.isBlank()) {
            findMatches = List.of();
            selectedFindIndex = -1;
            layoutFindWidgets();
            syncFindButtons();
            return;
        }

        String needle = findQuery.toLowerCase(Locale.ROOT);
        List<SearchMatch> matches = new ArrayList<>();
        for (Layout layout : layouts) collectFindMatches(layout, needle, matches);
        findMatches = List.copyOf(matches);
        for (SearchMatch match : findMatches) {
            matchesByLayout.computeIfAbsent(match.layout, ignored -> new ArrayList<>()).add(match);
        }
        if (findMatches.isEmpty()) {
            selectedFindIndex = -1;
        } else if (resetSelection || selectedFindIndex < 0) {
            selectedFindIndex = 0;
        } else {
            selectedFindIndex = Math.min(selectedFindIndex, findMatches.size() - 1);
        }
        layoutFindWidgets();
        syncFindButtons();
        if (resetSelection && selectedFindIndex >= 0) scrollToSelectedFindResult();
    }

    private void collectFindMatches(Layout layout, String needle, List<SearchMatch> matches) {
        if (layout.block instanceof RuleBox box) {
            String searchable = box.ruleId().toLowerCase(Locale.ROOT);
            int from = 0;
            while (from <= searchable.length() - needle.length()) {
                int start = searchable.indexOf(needle, from);
                if (start < 0) break;
                matches.add(new SearchMatch(layout, -1, start, start + needle.length(), layout.absoluteY));
                from = start + Math.max(1, needle.length());
            }
        } else if (layout.block instanceof TextBlock) {
            for (int lineIndex = 0; lineIndex < layout.lines.size(); lineIndex++) {
                String line = orderedText(layout.lines.get(lineIndex));
                String searchable = line.toLowerCase(Locale.ROOT);
                int from = 0;
                while (from <= searchable.length() - needle.length()) {
                    int start = searchable.indexOf(needle, from);
                    if (start < 0) break;
                    matches.add(new SearchMatch(layout, lineIndex, start, start + needle.length(),
                            layout.absoluteY + lineIndex * layout.lineStep));
                    from = start + Math.max(1, needle.length());
                }
            }
        }
        for (Layout child : layout.children) collectFindMatches(child, needle, matches);
        if (layout.table == null) return;
        for (TableRow row : layout.table.rows) {
            for (TableCellLayout cell : row.cells) {
                for (Layout child : cell.content) collectFindMatches(child, needle, matches);
            }
        }
    }

    private String orderedText(OrderedText text) {
        StringBuilder result = new StringBuilder();
        text.accept((index, style, codePoint) -> {
            result.appendCodePoint(codePoint);
            return true;
        });
        return result.toString();
    }

    private void selectFindResult(int direction) {
        if (findMatches.isEmpty()) return;
        selectedFindIndex = Math.floorMod(selectedFindIndex + direction, findMatches.size());
        scrollToSelectedFindResult();
    }

    private void scrollToSelectedFindResult() {
        if (selectedFindIndex < 0 || selectedFindIndex >= findMatches.size()) return;
        int viewportHeight = Math.max(1, viewerBottom - viewerTop);
        setContentScrollOffset(findMatches.get(selectedFindIndex).contentY - viewportHeight / 3);
    }

    private void syncFindButtons() {
        boolean navigable = findOpen && !findMatches.isEmpty();
        if (findPreviousButton != null) findPreviousButton.active = navigable;
        if (findNextButton != null) findNextButton.active = navigable;
    }

    @Override
    public void tick() {
        super.tick();
        if (RulebookService.shared().snapshot().revision() != observedRevision) {
            releaseTextures();
            clearAndInit();
        }
    }

    private List<Layout> layoutDocument(RulebookDocument document) {
        List<Layout> result = new ArrayList<>();
        int y = scaled(4);
        boolean firstTopLevelImage = true;
        for (int index = 0; index < document.blocks().size(); index++) {
            DocumentBlock block = document.blocks().get(index);
            Layout layout = layout(block, documentWidth);
            layout.y = y;
            if (block instanceof ImageBlock && firstTopLevelImage) layout.primaryCrest = true;
            assignAbsoluteY(layout, y);
            result.add(layout);
            DocumentBlock next = index + 1 < document.blocks().size() ? document.blocks().get(index + 1) : null;
            int gap = gapBetween(block, next);
            if (block instanceof ImageBlock && firstTopLevelImage) {
                gap += scaled(18);
                firstTopLevelImage = false;
            }
            y += layout.height + gap;
        }
        return result;
    }

    private void assignAbsoluteY(Layout layout, int absoluteY) {
        layout.absoluteY = absoluteY;
        for (Layout child : layout.children) assignAbsoluteY(child, absoluteY + child.y);
        if (layout.table == null) return;
        int rowY = absoluteY;
        for (TableRow row : layout.table.rows) {
            for (TableCellLayout cell : row.cells) {
                for (Layout child : cell.content) assignAbsoluteY(child, rowY + child.y);
            }
            rowY += row.height;
        }
    }

    private Layout layout(DocumentBlock block, int availableWidth) {
        Layout layout = new Layout(block);
        if (block instanceof TextBlock text) {
            layout.textScale = switch (text.kind()) {
                case TITLE, HEADING_1 -> 1.6F;
                case HEADING_2 -> 1.25F;
                case HEADING_3 -> 1.1F;
                default -> 1.0F;
            };
            layout.centered = text.kind() == DocumentBlock.TextKind.TITLE
                    || text.kind() == DocumentBlock.TextKind.HEADING_1;
            int indent = text.kind() == DocumentBlock.TextKind.LIST_ITEM
                    ? scaled(LIST_BASE_INDENT + text.indent() * LIST_LEVEL_INDENT)
                    : scaled(text.indent() * LIST_LEVEL_INDENT);
            layout.indent = indent;
            layout.lines = textRenderer.wrapLines(toText(text.text(), isHeading(text.kind())),
                    Math.max(24, (int) ((availableWidth - indent) / layout.textScale)));
            layout.lineStep = Math.max(1, Math.round(lineHeight() * layout.textScale));
            layout.height = Math.max(Math.round(textRenderer.fontHeight * layout.textScale),
                    layout.lines.size() * layout.lineStep);
        } else if (block instanceof ImageBlock image) {
            layout.renderWidth = Math.min(Math.min(availableWidth, scaled(MAX_IMAGE_WIDTH)), scaled(image.displayWidth()));
            layout.height = Math.max(1, layout.renderWidth * image.displayHeight() / image.displayWidth());
        } else if (block instanceof RuleBox rule) {
            int bodyWidth = Math.max(40, availableWidth - scaled(RULE_RAIL_WIDTH) - scaled(RULE_PADDING * 2));
            int childY = scaled(RULE_PADDING);
            for (int index = 0; index < rule.body().size(); index++) {
                DocumentBlock child = rule.body().get(index);
                Layout childLayout = layout(child, bodyWidth);
                childLayout.y = childY;
                layout.children.add(childLayout);
                DocumentBlock next = index + 1 < rule.body().size() ? rule.body().get(index + 1) : null;
                childY += childLayout.height + gapBetween(child, next);
            }
            layout.height = Math.max(scaled(42), childY + scaled(RULE_PADDING));
        } else if (block instanceof TableBlock table) {
            layout.table = layoutTable(table, availableWidth);
            layout.height = layout.table.totalHeight;
        } else if (block instanceof Spacer spacer) {
            layout.height = scaled(spacer.height());
        }
        return layout;
    }

    private TableLayout layoutTable(TableBlock table, int availableWidth) {
        int columns = table.rows().stream().mapToInt(List::size).max().orElse(1);
        int[] columnWidths = distributeWidths(availableWidth, table.columnWeights(), columns);
        List<TableRow> rows = new ArrayList<>();
        int totalHeight = 0;
        for (int rowIndex = 0; rowIndex < table.rows().size(); rowIndex++) {
            List<TableCell> row = table.rows().get(rowIndex);
            List<TableCellLayout> cells = new ArrayList<>();
            int rowHeight = textRenderer.fontHeight + scaled(8);
            for (int column = 0; column < row.size(); column++) {
                TableCell cell = row.get(column);
                int contentWidth = Math.max(20, columnWidths[column] - scaled(TABLE_PADDING * 2));
                List<Layout> content = new ArrayList<>();
                int cellY = scaled(TABLE_PADDING);
                for (int index = 0; index < cell.content().size(); index++) {
                    DocumentBlock child = cell.content().get(index);
                    Layout childLayout = layout(child, contentWidth);
                    childLayout.y = cellY;
                    content.add(childLayout);
                    DocumentBlock next = index + 1 < cell.content().size() ? cell.content().get(index + 1) : null;
                    cellY += childLayout.height + gapBetween(child, next);
                }
                int cellHeight = cellY + scaled(TABLE_PADDING);
                cells.add(new TableCellLayout(content, cell.background()));
                rowHeight = Math.max(rowHeight, cellHeight);
            }
            rows.add(new TableRow(cells, rowHeight));
            totalHeight += rowHeight;
        }
        return new TableLayout(rows, columns, columnWidths, totalHeight);
    }

    private int[] distributeWidths(int availableWidth, List<Float> weights, int columns) {
        int[] result = new int[columns];
        float totalWeight = 0;
        for (int index = 0; index < columns; index++) totalWeight += weights.get(index);
        int allocated = 0;
        for (int index = 0; index < columns; index++) {
            result[index] = index == columns - 1 ? availableWidth - allocated
                    : Math.max(1, Math.round(availableWidth * weights.get(index) / totalWeight));
            allocated += result[index];
        }
        return result;
    }

    private int gapBetween(DocumentBlock block, DocumentBlock next) {
        if (next == null || block instanceof RuleBox && next instanceof RuleBox) return 0;
        if (next instanceof TextBlock following && following.kind() == DocumentBlock.TextKind.TITLE) {
            return scaled(30);
        }
        if (block instanceof Spacer || next instanceof Spacer) return 0;
        if (block instanceof TextBlock current && next instanceof TextBlock following) {
            if (isFullyItalic(current.text()) && isFullyItalic(following.text())) return 0;
            boolean currentHeading = isHeading(current.kind());
            boolean followingHeading = isHeading(following.kind());
            if (currentHeading) return scaled(12);
            if (followingHeading) return scaled(16);
            if (current.kind() == DocumentBlock.TextKind.LIST_ITEM
                    && following.kind() == DocumentBlock.TextKind.LIST_ITEM) return scaled(1);
            if (current.kind() == DocumentBlock.TextKind.LIST_ITEM
                    || following.kind() == DocumentBlock.TextKind.LIST_ITEM) return scaled(5);
        }
        return scaled(BLOCK_GAP + 2);
    }

    private static boolean isHeading(DocumentBlock.TextKind kind) {
        return kind == DocumentBlock.TextKind.TITLE || kind.name().startsWith("HEADING");
    }

    private static boolean isFullyItalic(RichText text) {
        boolean foundText = false;
        for (TextRun run : text.runs()) {
            if (run.text().isBlank()) continue;
            foundText = true;
            if (!run.italic()) return false;
        }
        return foundText;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderMenuBackgroundWithoutHeader(context);
        renderStatus(context);
        beginContentScissor(context);
        for (Layout layout : layouts) {
            int y = contentY(layout.y);
            if (isContentVisible(y, layout.height)) renderLayout(context, layout, documentX, y, documentWidth);
        }
        if (snapshot.document() == null) {
            Text message = snapshot.status() == RulebookStatus.LOADING
                    ? Text.translatable("dmls.rulebook.connecting") : Text.translatable("dmls.rulebook.unavailable");
            context.drawCenteredTextWithShadow(textRenderer, message, width / 2, contentY(scaled(16)),
                    snapshot.status() == RulebookStatus.LOADING ? 0xFFFFFF55 : 0xFFFF5555);
        }
        endContentScissor(context);
        renderFindHint(context);
        if (findOpen) renderFindPanel(context);
        super.render(context, mouseX, mouseY, delta);
        if (snapshot.status() == RulebookStatus.STALE && snapshot.document() != null) renderStaleOutline(context);
        renderReloadIcon(context);
        if (findOpen) renderFindCount(context);
    }

    private void renderStatus(DrawContext context) {
        Text status;
        int color;
        if (snapshot.fetchedAt() != null) {
            String timestamp = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
                    .withLocale(Locale.getDefault()).withZone(ZoneId.systemDefault()).format(snapshot.fetchedAt());
            status = snapshot.error().isBlank() ? Text.translatable("dmls.rulebook.fetched_at", timestamp)
                    : Text.translatable("dmls.rulebook.reload_failed", timestamp);
            color = snapshot.status() == RulebookStatus.STALE || !snapshot.error().isBlank() ? 0xFFFF7777 : 0xFFBBBBBB;
        } else {
            status = Text.translatable(snapshot.refreshing() ? "dmls.rulebook.connecting" : "dmls.rulebook.unavailable");
            color = snapshot.refreshing() ? 0xFFFFFF55 : 0xFFFF5555;
        }
        context.drawCenteredTextWithShadow(textRenderer, status, width / 2, scaled(8), color);
    }

    private void renderFindPanel(DrawContext context) {
        context.fill(findPanelX, findPanelY, findPanelX + findPanelWidth,
                findPanelY + findPanelHeight, 0xF0181818);
        context.drawStrokedRectangle(findPanelX, findPanelY, findPanelWidth, findPanelHeight, 0xFFD8D8D8);
    }

    private void renderFindCount(DrawContext context) {
        int countY = findPanelY + (findPanelHeight - textRenderer.fontHeight + 1) / 2;
        context.drawCenteredTextWithShadow(textRenderer, Text.literal(findCountText()),
                findCountX + findCountWidth / 2, countY, 0xFFDDDDDD);
    }

    private String findCountText() {
        return findMatches.isEmpty() ? "0 of 0" : selectedFindIndex + 1 + " of " + findMatches.size();
    }

    private void renderFindHint(DrawContext context) {
        context.drawCenteredTextWithShadow(textRenderer, Text.translatable("dmls.rulebook.find_hint"),
                width / 2, footerButtonY() - scaled(14), 0xFFBBBBBB);
    }

    private void renderStaleOutline(DrawContext context) {
        double pulse = (Math.sin(System.currentTimeMillis() / 320.0) + 1.0) / 2.0;
        int alpha = 55 + (int) (pulse * 145);
        context.drawStrokedRectangle(documentX - 2, viewerTop - 2, documentWidth + 4,
                viewerBottom - viewerTop + 4, alpha << 24 | 0xFF3030);
    }

    private void renderLayout(DrawContext context, Layout layout, int x, int y, int availableWidth) {
        if (layout.block instanceof TextBlock) {
            int lineY = y;
            for (int lineIndex = 0; lineIndex < layout.lines.size(); lineIndex++) {
                OrderedText line = layout.lines.get(lineIndex);
                if (layout.textScale == 1.0F) {
                    drawStyledLine(context, line, x + layout.indent, lineY, layout, lineIndex);
                } else {
                    float lineWidth = textRenderer.getWidth(line) * layout.textScale;
                    float lineX = layout.centered
                            ? x + (availableWidth - lineWidth) / 2.0F
                            : x + layout.indent;
                    context.getMatrices().pushMatrix();
                    context.getMatrices().translate(lineX, lineY);
                    context.getMatrices().scale(layout.textScale, layout.textScale);
                    drawStyledLine(context, line, 0, 0, layout, lineIndex);
                    context.getMatrices().popMatrix();
                }
                lineY += layout.lineStep;
            }
        } else if (layout.block instanceof ImageBlock image) {
            Texture texture = texture(image);
            if (texture == null) {
                context.drawCenteredTextWithShadow(textRenderer, Text.translatable("dmls.rulebook.image_unavailable"),
                        x + availableWidth / 2, y + scaled(8), 0xFFAAAAAA);
            } else {
                int imageCenter = layout.primaryCrest ? documentX + documentWidth / 2 : x + availableWidth / 2;
                int imageX = imageCenter - layout.renderWidth / 2;
                context.drawTexture(RenderPipelines.GUI_TEXTURED, texture.identifier, imageX, y, 0, 0,
                        layout.renderWidth, layout.height, texture.width, texture.height, texture.width, texture.height);
            }
        } else if (layout.block instanceof RuleBox box) {
            int railWidth = scaled(RULE_RAIL_WIDTH);
            context.fill(x, y, x + railWidth, y + layout.height, 0xE81E2124);
            context.drawStrokedRectangle(x, y, availableWidth, layout.height, WHITE_BORDER);
            context.fill(x + railWidth - 1, y, x + railWidth, y + layout.height, WHITE_BORDER);
            boolean selected = selectedRule != null && selectedRule.id().equalsIgnoreCase(box.ruleId());
            if (selected) context.drawStrokedRectangle(x + 1, y + 1, availableWidth - 2, layout.height - 2, 0xFFFFAA00);
            Style ruleIdStyle = Style.EMPTY.withBold(true).withColor(box.ruleColor());
            Text ruleId = Text.literal(box.ruleId()).setStyle(ruleIdStyle);
            int ruleIdX = x + (railWidth - textRenderer.getWidth(ruleId)) / 2;
            int ruleIdY = y + scaled(11);
            drawRuleIdSearchHighlights(context, layout, box.ruleId(), ruleIdStyle, ruleIdX, ruleIdY);
            context.drawTextWithShadow(textRenderer, ruleId, ruleIdX, ruleIdY, 0xFFFFFFFF);
            int bodyX = x + railWidth + scaled(RULE_PADDING);
            int bodyWidth = availableWidth - railWidth - scaled(RULE_PADDING * 2);
            for (Layout child : layout.children) renderLayout(context, child, bodyX, y + child.y, bodyWidth);
        } else if (layout.block instanceof TableBlock) {
            renderTable(context, layout.table, x, y, availableWidth);
        }
    }

    private void renderTable(DrawContext context, TableLayout table, int x, int y, int availableWidth) {
        int rowY = y;
        for (TableRow row : table.rows) {
            context.drawStrokedRectangle(x, rowY, availableWidth, row.height, WHITE_BORDER);
            int cellX = x;
            for (int column = 0; column < table.columns; column++) {
                int cellWidth = table.columnWidths[column];
                if (column < row.cells.size() && row.cells.get(column).background >= 0) {
                    int color = row.cells.get(column).background;
                    context.fill(cellX, rowY, cellX + cellWidth, rowY + row.height, 0xE0000000 | color);
                }
                if (column > 0) context.fill(cellX, rowY, cellX + 1, rowY + row.height, WHITE_BORDER);
                if (column < row.cells.size()) {
                    int contentX = cellX + scaled(TABLE_PADDING);
                    int contentWidth = Math.max(20, cellWidth - scaled(TABLE_PADDING * 2));
                    for (Layout child : row.cells.get(column).content) {
                        renderLayout(context, child, contentX, rowY + child.y, contentWidth);
                    }
                }
                cellX += cellWidth;
            }
            rowY += row.height;
        }
    }

    private MutableText toText(RichText rich, boolean forceBold) {
        MutableText result = Text.empty();
        for (TextRun run : rich.runs()) {
            Style style = Style.EMPTY.withBold(forceBold || run.bold()).withItalic(run.italic())
                    .withUnderline(run.underlined()).withStrikethrough(run.strikethrough())
                    .withColor(readableColor(run.color()));
            if (run.backgroundColor() != null) {
                style = style.withInsertion("dmls-bg:" + Integer.toHexString(run.backgroundColor()));
            }
            if (run.href() != null) style = style.withClickEvent(new ClickEvent.OpenUrl(URI.create(run.href()))).withUnderline(true);
            if (run.inlineItem() == null) {
                result.append(Text.literal(run.text()).setStyle(style));
            } else {
                int itemCount = run.text().codePointCount(0, run.text().length());
                for (int index = 0; index < itemCount; index++) {
                    result.append(Text.literal(" ").setStyle(style.withInsertion("dmls-item:" + run.inlineItem().name())));
                    result.append(Text.literal("  ").setStyle(style.withInsertion(null)));
                }
            }
        }
        return result;
    }

    private void drawStyledLine(DrawContext context, OrderedText line, int x, int y,
                                Layout layout, int lineIndex) {
        float[] cursor = {x};
        line.accept((index, style, codePoint) -> {
            float next = cursor[0] + styledGlyphWidth(style, codePoint);
            String insertion = style.getInsertion();
            if (insertion != null && insertion.startsWith("dmls-item:")) {
                try {
                    drawInlineItem(context, InlineItem.valueOf(insertion.substring("dmls-item:".length())), cursor[0], y);
                } catch (IllegalArgumentException ignored) {
                }
            } else if (insertion != null && insertion.startsWith("dmls-bg:")) {
                try {
                    int background = Integer.parseInt(insertion.substring("dmls-bg:".length()), 16);
                    context.fill((int) Math.floor(cursor[0]), y - 1, (int) Math.ceil(next),
                            y + textRenderer.fontHeight + 1, 0xE0000000 | background);
                } catch (NumberFormatException ignored) {
                }
            }
            cursor[0] = next;
            return true;
        });
        context.drawTextWithShadow(textRenderer, line, x, y, 0xFFFFFFFF);
        if (drawSearchHighlights(context, line, x, y, layout, lineIndex)) {
            context.drawTextWithShadow(textRenderer, line, x, y, 0xFFFFFFFF);
        }
    }

    private boolean drawSearchHighlights(DrawContext context, OrderedText line, int x, int y,
                                         Layout layout, int lineIndex) {
        List<SearchMatch> layoutMatches = matchesByLayout.get(layout);
        if (!findOpen || layoutMatches == null || layoutMatches.isEmpty()) return false;
        SearchMatch active = selectedFindIndex >= 0 && selectedFindIndex < findMatches.size()
                ? findMatches.get(selectedFindIndex) : null;
        boolean highlighted = false;
        for (SearchMatch match : layoutMatches) {
            if (match.lineIndex != lineIndex) continue;
            float[] bounds = textBounds(line, match.start, match.end);
            int left = (int) Math.floor(x + bounds[0]);
            int right = Math.max(left + 1, (int) Math.ceil(x + bounds[1]));
            context.fill(left, y - 1, right, y + textRenderer.fontHeight + 1,
                    match == active ? FIND_HIGHLIGHT_ACTIVE : FIND_HIGHLIGHT);
            highlighted = true;
        }
        return highlighted;
    }

    private void drawRuleIdSearchHighlights(DrawContext context, Layout layout, String ruleId,
                                            Style style, int x, int y) {
        List<SearchMatch> layoutMatches = matchesByLayout.get(layout);
        if (!findOpen || layoutMatches == null) return;
        SearchMatch active = selectedFindIndex >= 0 && selectedFindIndex < findMatches.size()
                ? findMatches.get(selectedFindIndex) : null;
        for (SearchMatch match : layoutMatches) {
            if (match.lineIndex != -1) continue;
            int left = x + textRenderer.getWidth(Text.literal(ruleId.substring(0, match.start)).setStyle(style));
            int right = left + textRenderer.getWidth(Text.literal(ruleId.substring(match.start, match.end)).setStyle(style));
            context.fill(left, y - 1, Math.max(left + 1, right), y + textRenderer.fontHeight + 1,
                    match == active ? FIND_HIGHLIGHT_ACTIVE : FIND_HIGHLIGHT);
        }
    }

    private float[] textBounds(OrderedText line, int start, int end) {
        int[] offset = {0};
        float[] cursor = {0};
        float[] bounds = {0, 0};
        boolean[] started = {false};
        line.accept((index, style, codePoint) -> {
            int nextOffset = offset[0] + Character.charCount(codePoint);
            float nextX = cursor[0] + styledGlyphWidth(style, codePoint);
            if (!started[0] && nextOffset > start) {
                bounds[0] = cursor[0];
                started[0] = true;
            }
            if (offset[0] < end) bounds[1] = nextX;
            offset[0] = nextOffset;
            cursor[0] = nextX;
            return offset[0] < end;
        });
        return bounds;
    }

    private int styledGlyphWidth(Style style, int codePoint) {
        return textRenderer.getWidth(Text.literal(new String(Character.toChars(codePoint))).setStyle(style));
    }

    private void drawInlineItem(DrawContext context, InlineItem item, float x, int y) {
        ItemStack stack = switch (item) {
            case GOLD_INGOT -> GOLD_INGOT;
            case IRON_INGOT -> IRON_INGOT;
            case COPPER_INGOT -> COPPER_INGOT;
        };
        float scale = 0.75F;
        context.getMatrices().pushMatrix();
        context.getMatrices().translate(x, y - 2);
        context.getMatrices().scale(scale, scale);
        context.drawItem(stack, 0, 0);
        context.getMatrices().popMatrix();
    }

    private int readableColor(Integer source) {
        if (source == null) return 0xFFFFFF;
        int red = source >> 16 & 0xFF;
        int green = source >> 8 & 0xFF;
        int blue = source & 0xFF;
        int luminance = (red * 299 + green * 587 + blue * 114) / 1000;
        int spread = Math.max(red, Math.max(green, blue)) - Math.min(red, Math.min(green, blue));
        return luminance < 70 || spread < 24 && luminance < 220 ? 0xFFFFFF : source;
    }

    private int lineHeight() {
        return textRenderer.fontHeight + scaled(LINE_GAP);
    }

    private Texture texture(ImageBlock image) {
        if (textures.containsKey(image)) return textures.get(image);
        try {
            NativeImage decoded = NativeImage.read(new ByteArrayInputStream(image.encoded()));
            Identifier identifier = Identifier.of(DMLS.MOD_ID.toLowerCase(Locale.ROOT),
                    "rulebook/image_" + Integer.toUnsignedString(System.identityHashCode(image), 36));
            NativeImageBackedTexture backed = new NativeImageBackedTexture(() -> "DMLS live rulebook image", decoded);
            client.getTextureManager().registerTexture(identifier, backed);
            Texture texture = new Texture(identifier, decoded.getWidth(), decoded.getHeight());
            textures.put(image, texture);
            return texture;
        } catch (IOException | RuntimeException exception) {
            DMLS.LOGGER.warn("Could not decode an inline rulebook image", exception);
            textures.put(image, null);
            return null;
        }
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int modifiers = input.modifiers();
        boolean findModifier = (modifiers & (InputUtil.GLFW_MOD_CONTROL | InputUtil.GLFW_MOD_SUPER)) != 0;
        if (input.getKeycode() == InputUtil.GLFW_KEY_F && findModifier) {
            openFind();
            return true;
        }
        if (findOpen && input.isEscape()) {
            closeFind();
            return true;
        }
        if (findOpen && (input.getKeycode() == InputUtil.GLFW_KEY_ENTER
                || input.getKeycode() == InputUtil.GLFW_KEY_KP_ENTER)) {
            selectFindResult((modifiers & InputUtil.GLFW_MOD_SHIFT) != 0 ? -1 : 1);
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (findOpen && click.x() >= findPanelX && click.x() < findPanelX + findPanelWidth
                && click.y() >= findPanelY && click.y() < findPanelY + findPanelHeight) {
            return super.mouseClicked(click, doubled);
        }
        if (click.button() == 0 && click.y() >= viewerTop && click.y() < viewerBottom) {
            for (Layout layout : layouts) {
                Style style = styleAt(layout, documentX, contentY(layout.y), documentWidth, click.x(), click.y());
                if (style != null && style.getClickEvent() != null) {
                    handleClickEvent(style.getClickEvent(), client, this);
                    return true;
                }
            }
        }
        if (click.button() == 0 && click.x() >= documentX && click.x() < documentX + documentWidth
                && click.y() >= viewerTop && click.y() < viewerBottom && snapshot.document() != null) {
            for (Layout layout : layouts) {
                if (!(layout.block instanceof RuleBox box)) continue;
                int y = contentY(layout.y);
                if (click.y() >= y && click.y() < y + layout.height) {
                    RulebookRule clickedRule = snapshot.document().rule(box.ruleId()).orElse(null);
                    if (clickedRule != null && clickedRule.punishable()) {
                        client.getSoundManager().play(PositionedSoundInstance.ui(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                        selectedRule = clickedRule;
                        clearAndInit();
                        return true;
                    }
                }
            }
        }
        return super.mouseClicked(click, doubled);
    }

    private void renderReloadIcon(DrawContext context) {
        if (reloadButton == null || !reloadButton.visible) return;
        Text icon = Text.literal(snapshot.refreshing() ? "…" : "↻");
        float scale = 1.6F;
        context.getMatrices().pushMatrix();
        context.getMatrices().translate(reloadButton.getX() + reloadButton.getWidth() / 2.0F,
                reloadButton.getY() + reloadButton.getHeight() / 2.0F
                        - textRenderer.fontHeight * scale / 2.0F - scaled(1));
        context.getMatrices().scale(scale, scale);
        context.drawCenteredTextWithShadow(textRenderer, icon, 0, 0,
                reloadButton.active ? 0xFFFFFFFF : 0xFF999999);
        context.getMatrices().popMatrix();
    }

    private Style styleAt(Layout layout, int x, int y, int availableWidth, double mouseX, double mouseY) {
        if (mouseY < y || mouseY >= y + layout.height) return null;
        if (layout.block instanceof TextBlock) {
            int line = (int) (mouseY - y) / lineHeight();
            if (line < 0 || line >= layout.lines.size()) return null;
            return styleAt(layout.lines.get(line), (float) mouseX - x - layout.indent);
        }
        if (layout.block instanceof RuleBox) {
            int railWidth = scaled(RULE_RAIL_WIDTH);
            int bodyX = x + railWidth + scaled(RULE_PADDING);
            int bodyWidth = availableWidth - railWidth - scaled(RULE_PADDING * 2);
            for (Layout child : layout.children) {
                Style style = styleAt(child, bodyX, y + child.y, bodyWidth, mouseX, mouseY);
                if (style != null) return style;
            }
        }
        if (layout.block instanceof TableBlock && layout.table != null) {
            int rowY = y;
            for (TableRow row : layout.table.rows) {
                if (mouseY >= rowY && mouseY < rowY + row.height) {
                    int column = layout.table.columnAt((int) mouseX - x);
                    if (column >= row.cells.size()) return null;
                    int cellX = x + layout.table.columnX(column);
                    int contentX = cellX + scaled(TABLE_PADDING);
                    int contentWidth = Math.max(20, layout.table.columnWidths[column] - scaled(TABLE_PADDING * 2));
                    for (Layout child : row.cells.get(column).content) {
                        Style style = styleAt(child, contentX, rowY + child.y, contentWidth, mouseX, mouseY);
                        if (style != null) return style;
                    }
                    return null;
                }
                rowY += row.height;
            }
        }
        return null;
    }

    private Style styleAt(OrderedText line, float targetX) {
        if (targetX < 0 || targetX > textRenderer.getWidth(line)) return null;
        float[] x = {0};
        Style[] found = {null};
        line.accept((index, style, codePoint) -> {
            float next = x[0] + textRenderer.getWidth(new String(Character.toChars(codePoint)));
            if (targetX >= x[0] && targetX < next) {
                found[0] = style;
                return false;
            }
            x[0] = next;
            return true;
        });
        return found[0];
    }

    @Override
    protected int contentScrollbarX() {
        return width - SCROLLBAR_WIDTH - scaled(10);
    }

    @Override
    protected int contentScrollbarTop() {
        return findOpen ? Math.max(viewerTop, findPanelY + findPanelHeight + scaled(4)) : viewerTop;
    }

    @Override
    protected int contentScrollbarBottom() {
        return viewerBottom;
    }

    @Override
    public void removed() {
        releaseTextures();
        super.removed();
    }

    private void releaseTextures() {
        if (client != null) {
            for (Texture texture : textures.values()) if (texture != null) client.getTextureManager().destroyTexture(texture.identifier);
        }
        textures.clear();
    }

    private static final class Layout {
        private final DocumentBlock block;
        private int y;
        private int absoluteY;
        private int height;
        private int indent;
        private int renderWidth;
        private int lineStep;
        private float textScale = 1.0F;
        private boolean centered;
        private boolean primaryCrest;
        private List<OrderedText> lines = List.of();
        private final List<Layout> children = new ArrayList<>();
        private TableLayout table;

        private Layout(DocumentBlock block) {
            this.block = block;
        }
    }

    private record TableLayout(List<TableRow> rows, int columns, int[] columnWidths, int totalHeight) {
        private int columnAt(int x) {
            int current = 0;
            for (int column = 0; column < columnWidths.length; column++) {
                current += columnWidths[column];
                if (x < current) return column;
            }
            return columnWidths.length - 1;
        }

        private int columnX(int target) {
            int x = 0;
            for (int column = 0; column < target; column++) x += columnWidths[column];
            return x;
        }
    }

    private record TableCellLayout(List<Layout> content, int background) {
    }

    private record TableRow(List<TableCellLayout> cells, int height) {
    }

    private record Texture(Identifier identifier, int width, int height) {
    }

    private record SearchMatch(Layout layout, int lineIndex, int start, int end, int contentY) {
    }
}
