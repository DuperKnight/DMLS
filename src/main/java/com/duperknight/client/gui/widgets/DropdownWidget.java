package com.duperknight.client.gui.widgets;

import com.duperknight.client.gui.DMLSMenuScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.screen.narration.NarrationPart;
import net.minecraft.client.gui.widget.PressableWidget;
import net.minecraft.client.input.AbstractInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.util.InputUtil;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * A reusable DMLS panel-styled dropdown for choosing one value from a fixed list.
 *
 * <p>The owning screen should render {@link #renderDropdown(DrawContext, int, int, float)} after its normal
 * children so the expanded list stays above nearby controls. While the list is open, the screen should also
 * offer this widget mouse input before other children; this lets an option cover another control safely.</p>
 */
public final class DropdownWidget<T> extends PressableWidget {
    private static final int DEFAULT_MAX_VISIBLE_ROWS = 6;
    private static final int ARROW_AREA_WIDTH = 18;
    private static final int SCROLLBAR_GAP = 2;
    private static final int MIN_SCROLLBAR_THUMB_HEIGHT = 8;
    private static final int INDICATOR_TEXTURE_SIZE = 16;
    private static final int INDICATOR_RENDER_SIZE = 12;

    private final Text optionText;
    private final List<T> values;
    private final Function<T, Text> valueToText;
    private final UpdateCallback<T> callback;
    private final boolean showOptionLabel;
    private final int maxVisibleRows;
    private final List<OptionButton> optionButtons;
    private final Identifier indicatorTexture;
    private final Predicate<T> showOptionIndicator;
    private final BooleanSupplier showCollapsedIndicator;

    private T value;
    private boolean open;
    private boolean draggingScrollbar;
    private int highlightedIndex;
    private int scrollOffset;
    private int dropdownTopBound;
    private int dropdownBottomBound = Integer.MAX_VALUE;

    private DropdownWidget(Builder<T> builder) {
        super(builder.x, builder.y, builder.width, builder.height, Text.empty());
        if (builder.values.isEmpty()) {
            throw new IllegalArgumentException("A dropdown needs at least one value");
        }

        optionText = builder.optionText;
        values = List.copyOf(builder.values);
        valueToText = builder.valueToText;
        callback = builder.callback;
        showOptionLabel = builder.showOptionLabel;
        maxVisibleRows = builder.maxVisibleRows;
        indicatorTexture = builder.indicatorTexture;
        showOptionIndicator = builder.showOptionIndicator;
        showCollapsedIndicator = builder.showCollapsedIndicator;
        value = values.contains(builder.initialValue) ? builder.initialValue : values.getFirst();
        highlightedIndex = values.indexOf(value);
        optionButtons = values.stream().map(OptionButton::new).toList();
        updateMessage();
    }

    public static <T> Builder<T> builder(Text optionText, List<T> values, T initialValue,
                                          Function<T, Text> valueToText, UpdateCallback<T> callback) {
        return new Builder<>(optionText, values, initialValue, valueToText, callback);
    }

    /** Keeps the expanded list inside these vertical screen coordinates. */
    public void setDropdownBounds(int top, int bottom) {
        dropdownTopBound = top;
        dropdownBottomBound = Math.max(top + height, bottom);
        clampScrollOffset();
    }

    public T getValue() {
        return value;
    }

    public void setValue(T value) {
        int index = values.indexOf(value);
        if (index < 0) {
            return;
        }
        this.value = value;
        highlightedIndex = index;
        ensureHighlightedVisible();
        updateMessage();
    }

    public boolean isDropdownOpen() {
        return open;
    }

    public boolean isDraggingScrollbar() {
        return draggingScrollbar;
    }

    public void closeDropdown() {
        open = false;
        draggingScrollbar = false;
    }

    @Override
    public void onPress(AbstractInput input) {
        if (open) {
            closeDropdown();
        } else {
            open = true;
            highlightedIndex = values.indexOf(value);
            ensureHighlightedVisible();
        }
    }

    @Override
    protected void drawIcon(DrawContext context, int mouseX, int mouseY, float delta) {
        drawPanel(context, getX(), getY(), width, height);
        drawLeftAlignedLabel(context, this, ARROW_AREA_WIDTH);
        drawArrow(context);
    }

    /** Draws the expanded list or collapsed indicator in a fresh layer above the screen's regular controls. */
    public void renderDropdown(DrawContext context, int mouseX, int mouseY, float delta) {
        if (!visible) {
            return;
        }
        if (!open) {
            if (indicatorTexture != null && showCollapsedIndicator.getAsBoolean()) {
                context.createNewRootLayer();
                drawIndicator(context, getRight() - INDICATOR_RENDER_SIZE / 2,
                        getY() - INDICATOR_RENDER_SIZE / 2);
            }
            return;
        }

        layoutVisibleButtons();
        context.createNewRootLayer();
        drawPanel(context, getX(), menuTop(), width, menuHeight());
        int end = Math.min(values.size(), scrollOffset + visibleRowCount());
        for (int index = scrollOffset; index < end; index++) {
            optionButtons.get(index).render(context, mouseX, mouseY, delta);
        }
        if (hasScrollbar()) {
            renderScrollbar(context);
        }
        // Rows and the scrollbar are intentionally inset; redraw the frame last to keep one crisp outer border.
        context.drawStrokedRectangle(getX(), menuTop(), width, menuHeight(), DMLSMenuScreen.PANEL_BORDER_COLOR);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (!open) {
            return super.mouseClicked(click, doubled);
        }

        layoutVisibleButtons();
        if (hasScrollbar() && isOverScrollbar(click.x(), click.y())) {
            draggingScrollbar = true;
            updateScrollFromMouse(click.y());
            return true;
        }

        int optionIndex = optionIndexAt(click.x(), click.y());
        if (optionIndex >= 0) {
            return optionButtons.get(optionIndex).mouseClicked(click, doubled);
        }
        if (super.mouseClicked(click, doubled)) {
            return true;
        }

        closeDropdown();
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (!open || !isOverExpandedArea(mouseX, mouseY)) {
            return false;
        }
        if (hasScrollbar() && verticalAmount != 0.0) {
            scrollOffset = Math.clamp(scrollOffset - (int) Math.signum(verticalAmount), 0, maxScrollOffset());
        }
        return true;
    }

    @Override
    public boolean mouseDragged(Click click, double deltaX, double deltaY) {
        if (!draggingScrollbar) {
            return false;
        }
        updateScrollFromMouse(click.y());
        return true;
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (!draggingScrollbar) {
            return false;
        }
        draggingScrollbar = false;
        return true;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (!open) {
            return super.keyPressed(input);
        }
        if (input.isEscape()) {
            closeDropdown();
            return true;
        }
        if (input.isTab()) {
            closeDropdown();
            return false;
        }
        if (input.isUp()) {
            moveHighlight(-1);
            return true;
        }
        if (input.isDown()) {
            moveHighlight(1);
            return true;
        }
        if (input.getKeycode() == InputUtil.GLFW_KEY_HOME) {
            setHighlight(0);
            return true;
        }
        if (input.getKeycode() == InputUtil.GLFW_KEY_END) {
            setHighlight(values.size() - 1);
            return true;
        }
        if (input.getKeycode() == InputUtil.GLFW_KEY_PAGE_UP) {
            setHighlight(Math.max(0, highlightedIndex - visibleRowCount()));
            return true;
        }
        if (input.getKeycode() == InputUtil.GLFW_KEY_PAGE_DOWN) {
            setHighlight(Math.min(values.size() - 1, highlightedIndex + visibleRowCount()));
            return true;
        }
        if (input.isEnterOrSpace()) {
            playDownSound(MinecraftClient.getInstance().getSoundManager());
            select(highlightedIndex);
            return true;
        }
        return false;
    }

    @Override
    public void setFocused(boolean focused) {
        super.setFocused(focused);
        if (!focused) {
            closeDropdown();
        }
    }

    @Override
    protected MutableText getNarrationMessage() {
        T narratedValue = open ? values.get(highlightedIndex) : value;
        return getNarrationMessage(ScreenTexts.composeGenericOptionText(optionText, valueToText.apply(narratedValue)));
    }

    @Override
    public void appendClickableNarrations(NarrationMessageBuilder builder) {
        builder.put(NarrationPart.TITLE, getNarrationMessage());
        if (active) {
            builder.put(NarrationPart.USAGE, Text.translatable(open
                    ? "dmls.dropdown.usage.open"
                    : "dmls.dropdown.usage.closed"));
        }
    }

    private void select(int index) {
        T selected = values.get(index);
        boolean changed = !Objects.equals(value, selected);
        value = selected;
        highlightedIndex = index;
        updateMessage();
        closeDropdown();
        if (changed) {
            callback.onValueChanged(this, selected);
        }
    }

    private void moveHighlight(int amount) {
        setHighlight(Math.floorMod(highlightedIndex + amount, values.size()));
    }

    private void setHighlight(int index) {
        highlightedIndex = index;
        ensureHighlightedVisible();
    }

    private void ensureHighlightedVisible() {
        int rows = visibleRowCount();
        if (highlightedIndex < scrollOffset) {
            scrollOffset = highlightedIndex;
        } else if (highlightedIndex >= scrollOffset + rows) {
            scrollOffset = highlightedIndex - rows + 1;
        }
        clampScrollOffset();
    }

    private void updateMessage() {
        Text valueText = valueToText.apply(value);
        setMessage(showOptionLabel ? ScreenTexts.composeGenericOptionText(optionText, valueText) : valueText);
    }

    private void drawArrow(DrawContext context) {
        int centerX = getRight() - ARROW_AREA_WIDTH / 2;
        int centerY = getY() + height / 2;
        int color = active ? 0xFFE0E0E0 : 0xFFA0A0A0;
        if (open) {
            context.fill(centerX - 3, centerY + 1, centerX + 4, centerY + 2, color);
            context.fill(centerX - 2, centerY, centerX + 3, centerY + 1, color);
            context.fill(centerX - 1, centerY - 1, centerX + 2, centerY, color);
        } else {
            context.fill(centerX - 3, centerY - 1, centerX + 4, centerY, color);
            context.fill(centerX - 2, centerY, centerX + 3, centerY + 1, color);
            context.fill(centerX - 1, centerY + 1, centerX + 2, centerY + 2, color);
        }
    }

    private void drawIndicator(DrawContext context, int x, int y) {
        context.drawTexture(RenderPipelines.GUI_TEXTURED, indicatorTexture, x, y, 0.0F, 0.0F,
                INDICATOR_RENDER_SIZE, INDICATOR_RENDER_SIZE,
                INDICATOR_TEXTURE_SIZE, INDICATOR_TEXTURE_SIZE,
                INDICATOR_TEXTURE_SIZE, INDICATOR_TEXTURE_SIZE);
    }

    private static void drawLeftAlignedLabel(DrawContext context, PressableWidget widget, int rightInset) {
        int left = widget.getX() + 8;
        int right = Math.max(left + 1, widget.getRight() - rightInset);
        context.getHoverListener(widget, DrawContext.HoverType.NONE)
                .marqueedText(widget.getMessage(), left, left, right, widget.getY(), widget.getBottom());
    }

    private static void drawPanel(DrawContext context, int x, int y, int width, int height) {
        context.fill(x, y, x + width, y + height, DMLSMenuScreen.PANEL_BACKGROUND_COLOR);
        context.drawStrokedRectangle(x, y, width, height, DMLSMenuScreen.PANEL_BORDER_COLOR);
    }

    private void layoutVisibleButtons() {
        int rowWidth = width - (hasScrollbar() ? DMLSMenuScreen.SCROLLBAR_WIDTH + SCROLLBAR_GAP : 0);
        int top = menuTop();
        int end = Math.min(values.size(), scrollOffset + visibleRowCount());
        for (int index = scrollOffset; index < end; index++) {
            OptionButton button = optionButtons.get(index);
            button.setDimensionsAndPosition(rowWidth, height, getX(), top + (index - scrollOffset) * height);
        }
    }

    private int optionIndexAt(double mouseX, double mouseY) {
        if (mouseX < getX() || mouseX >= getRight() - (hasScrollbar() ? DMLSMenuScreen.SCROLLBAR_WIDTH + SCROLLBAR_GAP : 0)
                || mouseY < menuTop() || mouseY >= menuTop() + menuHeight()) {
            return -1;
        }
        int index = scrollOffset + (int) ((mouseY - menuTop()) / height);
        return index < values.size() ? index : -1;
    }

    private boolean isOverExpandedArea(double mouseX, double mouseY) {
        return mouseX >= getX() && mouseX < getRight()
                && mouseY >= menuTop() && mouseY < menuTop() + menuHeight();
    }

    private boolean isOverScrollbar(double mouseX, double mouseY) {
        return mouseX >= scrollbarX() && mouseX < getRight()
                && mouseY >= menuTop() && mouseY < menuTop() + menuHeight();
    }

    private void renderScrollbar(DrawContext context) {
        int menuHeight = menuHeight();
        int thumbHeight = scrollbarThumbHeight();
        int thumbTravel = menuHeight - thumbHeight;
        int thumbY = menuTop() + (maxScrollOffset() == 0 ? 0 : scrollOffset * thumbTravel / maxScrollOffset());
        DMLSMenuScreen.renderVanillaScrollbar(context, scrollbarX(), menuTop(), menuHeight, thumbY, thumbHeight);
    }

    private void updateScrollFromMouse(double mouseY) {
        int thumbHeight = scrollbarThumbHeight();
        int track = Math.max(1, menuHeight() - thumbHeight);
        double relative = Math.clamp(mouseY - menuTop() - thumbHeight / 2.0, 0.0, track);
        scrollOffset = Math.clamp((int) Math.round(relative * maxScrollOffset() / track), 0, maxScrollOffset());
    }

    private int scrollbarThumbHeight() {
        return Math.clamp(menuHeight() * visibleRowCount() / values.size(),
                MIN_SCROLLBAR_THUMB_HEIGHT, menuHeight());
    }

    private int scrollbarX() {
        return getRight() - DMLSMenuScreen.SCROLLBAR_WIDTH;
    }

    private boolean hasScrollbar() {
        return values.size() > visibleRowCount();
    }

    private int maxScrollOffset() {
        return Math.max(0, values.size() - visibleRowCount());
    }

    private void clampScrollOffset() {
        scrollOffset = Math.clamp(scrollOffset, 0, maxScrollOffset());
    }

    private int menuTop() {
        return opensUpwards() ? getY() - menuHeight() : getBottom();
    }

    private int menuHeight() {
        return visibleRowCount() * height;
    }

    private int visibleRowCount() {
        int desiredRows = Math.min(maxVisibleRows, values.size());
        int available = opensUpwardsUnclamped()
                ? getY() - dropdownTopBound
                : dropdownBottomBound - getBottom();
        return Math.max(1, Math.min(desiredRows, Math.max(1, available / Math.max(1, height))));
    }

    private boolean opensUpwards() {
        return opensUpwardsUnclamped();
    }

    private boolean opensUpwardsUnclamped() {
        int desiredHeight = Math.min(maxVisibleRows, values.size()) * height;
        int downSpace = dropdownBottomBound - getBottom();
        int upSpace = getY() - dropdownTopBound;
        return downSpace < desiredHeight && upSpace > downSpace;
    }

    @FunctionalInterface
    public interface UpdateCallback<T> {
        void onValueChanged(DropdownWidget<T> dropdown, T value);
    }

    public static final class Builder<T> {
        private final Text optionText;
        private final List<T> values;
        private final T initialValue;
        private final Function<T, Text> valueToText;
        private final UpdateCallback<T> callback;
        private int x;
        private int y;
        private int width = 150;
        private int height = 20;
        private int maxVisibleRows = DEFAULT_MAX_VISIBLE_ROWS;
        private boolean showOptionLabel;
        private Identifier indicatorTexture;
        private Predicate<T> showOptionIndicator = ignored -> false;
        private BooleanSupplier showCollapsedIndicator = () -> false;

        private Builder(Text optionText, List<T> values, T initialValue,
                        Function<T, Text> valueToText, UpdateCallback<T> callback) {
            this.optionText = Objects.requireNonNull(optionText);
            this.values = List.copyOf(values);
            this.initialValue = initialValue;
            this.valueToText = Objects.requireNonNull(valueToText);
            this.callback = Objects.requireNonNull(callback);
        }

        public Builder<T> dimensions(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            return this;
        }

        public Builder<T> maxVisibleRows(int maxVisibleRows) {
            if (maxVisibleRows < 1) {
                throw new IllegalArgumentException("maxVisibleRows must be positive");
            }
            this.maxVisibleRows = maxVisibleRows;
            return this;
        }

        /** Shows "Option: Value" on the collapsed button instead of only the selected value. */
        public Builder<T> showOptionLabel(boolean showOptionLabel) {
            this.showOptionLabel = showOptionLabel;
            return this;
        }

        /** Adds a dynamic corner indicator and matching indicators beside marked options. */
        public Builder<T> indicator(Identifier texture, Predicate<T> showForOption,
                                    BooleanSupplier showWhenCollapsed) {
            this.indicatorTexture = Objects.requireNonNull(texture);
            this.showOptionIndicator = Objects.requireNonNull(showForOption);
            this.showCollapsedIndicator = Objects.requireNonNull(showWhenCollapsed);
            return this;
        }

        public DropdownWidget<T> build() {
            if (width < 1 || height < 1) {
                throw new IllegalArgumentException("Dropdown dimensions must be positive");
            }
            return new DropdownWidget<>(this);
        }
    }

    private final class OptionButton extends PressableWidget {
        private final T option;

        private OptionButton(T option) {
            // The real dimensions are assigned whenever the expanded list is laid out.
            super(0, 0, 1, 1, valueToText.apply(option));
            this.option = option;
        }

        @Override
        public void onPress(AbstractInput input) {
            select(values.indexOf(option));
        }

        @Override
        protected void drawIcon(DrawContext context, int mouseX, int mouseY, float delta) {
            int inset = 1;
            boolean selected = Objects.equals(option, value);
            if (isHovered()) {
                context.fill(getX() + inset, getY(), getRight() - inset, getBottom(), 0x604A4A4A);
            } else if (selected) {
                context.fill(getX() + inset, getY(), getRight() - inset, getBottom(), 0x403A3A3A);
            }
            if (selected) {
                context.drawStrokedRectangle(getX(), getY(), getWidth(), getHeight(),
                        DMLSMenuScreen.PANEL_BORDER_COLOR);
            }
            boolean showIndicator = indicatorTexture != null && showOptionIndicator.test(option);
            drawLeftAlignedLabel(context, this, showIndicator ? INDICATOR_RENDER_SIZE + 8 : 8);
            if (showIndicator) {
                int afterLabel = getX() + 8 + MinecraftClient.getInstance().textRenderer.getWidth(getMessage()) + 4;
                int rightmost = getRight() - INDICATOR_RENDER_SIZE - 4;
                drawIndicator(context, Math.min(afterLabel, rightmost),
                        getY() + (getHeight() - INDICATOR_RENDER_SIZE) / 2);
            }
        }

        @Override
        public void appendClickableNarrations(NarrationMessageBuilder builder) {
            appendDefaultNarrations(builder);
        }
    }
}
