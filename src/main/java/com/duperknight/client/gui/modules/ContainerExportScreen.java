package com.duperknight.client.gui.modules;

import com.duperknight.client.gui.DMLSMenuScreen;
import com.duperknight.client.modules.EventsContainerExportModule;
import com.duperknight.client.reimbursement.ContainerTarget;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/** Select containers in the world (one by one or by area) and export their contents to JSON. */
public final class ContainerExportScreen extends DMLSMenuScreen {
    private static final int MAX_LISTED = 6;
    private static final int GAP = 4;

    private final EventsContainerExportModule module;
    private Button selectButton;
    private Button corner1Button;
    private Button corner2Button;
    private Button addAreaButton;
    private Button clearCornersButton;
    private Button clearButton;
    private Button exportButton;
    private Button copyButton;

    public ContainerExportScreen(Screen parent, EventsContainerExportModule module) {
        super(module.displayName(), parent);
        this.module = module;
    }

    public Screen returnParent() {
        return parent;
    }

    @Override
    protected void init() {
        configureScrollableContent(module, scaled(320));
        int formWidth = Math.min(scaled(360), width - scaled(48));
        int formX = (width - formWidth) / 2;
        int halfWidth = (formWidth - GAP) / 2;
        int rightX = formX + halfWidth + GAP;

        selectButton = addScrollableChild(Button.builder(selectLabel(),
                        button -> module.beginSelection(minecraft, this))
                .bounds(formX, contentY(scaled(14)), formWidth, STANDARD_BUTTON_HEIGHT).build(), scaled(14));

        // Area selection: two corners, then add everything between them.
        corner1Button = addScrollableChild(Button.builder(cornerLabel(true), button -> {
                    if (minecraft.player != null) {
                        module.setCorner(true, minecraft.player.blockPosition());
                    }
                    refreshButtons();
                })
                .bounds(formX, contentY(scaled(42)), halfWidth, STANDARD_BUTTON_HEIGHT).build(), scaled(42));
        corner2Button = addScrollableChild(Button.builder(cornerLabel(false), button -> {
                    if (minecraft.player != null) {
                        module.setCorner(false, minecraft.player.blockPosition());
                    }
                    refreshButtons();
                })
                .bounds(rightX, contentY(scaled(42)), halfWidth, STANDARD_BUTTON_HEIGHT).build(), scaled(42));
        addAreaButton = addScrollableChild(Button.builder(Component.translatable("dmls.container_export.button.add_area"), button -> {
                    module.addAreaToSelection(minecraft);
                    refreshButtons();
                })
                .bounds(formX, contentY(scaled(70)), halfWidth, STANDARD_BUTTON_HEIGHT).build(), scaled(70));
        clearCornersButton = addScrollableChild(Button.builder(Component.translatable("dmls.container_export.button.clear_corners"), button -> {
                    module.clearCorners();
                    refreshButtons();
                })
                .bounds(rightX, contentY(scaled(70)), halfWidth, STANDARD_BUTTON_HEIGHT).build(), scaled(70));

        clearButton = addScrollableChild(Button.builder(Component.translatable("dmls.container_export.button.clear"), button -> {
                    module.clearSelection();
                    refreshButtons();
                })
                .bounds(formX, contentY(scaled(98)), formWidth, STANDARD_BUTTON_HEIGHT).build(), scaled(98));
        exportButton = addScrollableChild(Button.builder(exportLabel(), button -> module.startExport(minecraft, this))
                .bounds(formX, contentY(scaled(126)), formWidth, STANDARD_BUTTON_HEIGHT).build(), scaled(126));
        copyButton = addScrollableChild(Button.builder(Component.translatable("dmls.container_export.button.copy_path"), button -> {
                    if (!module.lastExportPath().isBlank()) {
                        minecraft.keyboardHandler.setClipboard(module.lastExportPath());
                    }
                })
                .bounds(formX, contentY(scaled(154)), formWidth, STANDARD_BUTTON_HEIGHT).build(), scaled(154));

        addRenderableWidget(Button.builder(Component.translatable("dmls.container_export.button.back"), button -> minecraft.gui.setScreen(parent))
                .bounds(width / 2 - scaled(75), footerButtonY(), scaled(150), STANDARD_BUTTON_HEIGHT).build());
        refreshButtons();
    }

    @Override
    public void tick() {
        refreshButtons();
    }

    private void refreshButtons() {
        if (selectButton == null) return;
        selectButton.setMessage(selectLabel());
        corner1Button.setMessage(cornerLabel(true));
        corner2Button.setMessage(cornerLabel(false));
        exportButton.setMessage(exportLabel());
        addAreaButton.active = module.corner1() != null && module.corner2() != null;
        clearCornersButton.active = module.corner1() != null || module.corner2() != null;
        clearButton.active = module.selectionCount() > 0;
        exportButton.active = module.selectionCount() > 0;
        copyButton.active = !module.lastExportPath().isBlank();
    }

    private Component cornerLabel(boolean first) {
        BlockPos pos = first ? module.corner1() : module.corner2();
        String base = first ? "dmls.container_export.button.corner1" : "dmls.container_export.button.corner2";
        if (pos == null) return Component.translatable(base);
        return Component.translatable(base + ".set", pos.getX(), pos.getY(), pos.getZ());
    }

    private Component selectLabel() {
        int count = module.selectionCount();
        return count == 0
                ? Component.translatable("dmls.container_export.button.select")
                : Component.translatable("dmls.container_export.button.select.count", count);
    }

    private Component exportLabel() {
        int count = module.selectionCount();
        if (count == 0) return Component.translatable("dmls.container_export.button.export");
        return Component.translatable(count == 1
                ? "dmls.container_export.button.export.one"
                : "dmls.container_export.button.export.many", count);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        renderMenuBackground(context);
        renderModuleHeader(context, module);
        beginContentScissor(context);

        int formWidth = Math.min(scaled(360), width - scaled(48));
        int formX = (width - formWidth) / 2;
        int y = contentY(scaled(188));

        List<ContainerTarget> selected = module.selected();
        if (!selected.isEmpty()) {
            if (isContentVisible(y, font.lineHeight)) {
                context.text(font, Component.translatable("dmls.container_export.selected.header"), formX, y, 0xFFCCCCCC);
            }
            y += font.lineHeight + 2;
            for (int index = 0; index < Math.min(MAX_LISTED, selected.size()); index++) {
                if (isContentVisible(y, font.lineHeight)) {
                    context.text(font, Component.literal(selected.get(index).displayCoordinates()),
                            formX + 6, y, 0xFFAAAAAA);
                }
                y += font.lineHeight + 1;
            }
            if (selected.size() > MAX_LISTED) {
                if (isContentVisible(y, font.lineHeight)) {
                    context.text(font, Component.translatable("dmls.container_export.selected.more", selected.size() - MAX_LISTED),
                            formX + 6, y, 0xFFAAAAAA);
                }
                y += font.lineHeight + 1;
            }
            y += 6;
        } else if (isContentVisible(y, font.lineHeight)) {
            context.text(font, Component.translatable("dmls.container_export.selected.none"), formX, y, 0xFFAAAAAA);
            y += font.lineHeight + 6;
        }

        Component status = module.lastStatus();
        if (!status.getString().isBlank()) {
            int color = module.lastStatusIsError() ? 0xFFFF5555 : 0xFF55FF55;
            for (FormattedCharSequence line : font.split(status, formWidth)) {
                if (isContentVisible(y, font.lineHeight)) context.text(font, line, formX, y, color);
                y += font.lineHeight + 2;
            }
        }

        endContentScissor(context);
        super.extractRenderState(context, mouseX, mouseY, delta);
    }
}