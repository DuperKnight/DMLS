package com.duperknight.client.modules;

import com.duperknight.client.export.ContainerExportOperation;
import com.duperknight.client.export.ContainerExportResult;
import com.duperknight.client.gui.modules.ContainerExportScreen;
import com.duperknight.client.reimbursement.ContainerSelectionController;
import com.duperknight.client.reimbursement.ContainerTarget;
import com.duperknight.client.session.OperationCoordinator;
import com.duperknight.client.session.OperationStartResult;
import com.duperknight.client.utils.ChatUtils;
import com.duperknight.client.utils.DMLSConfig;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.EnderChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import com.mojang.blaze3d.platform.InputConstants;

import java.util.ArrayList;
import java.util.List;

/** Select containers in the world (one by one or by two-corner area), then export everything inside them to a JSON file. */
public final class EventsContainerExportModule extends DMLSModule {
    private static final String PREFIX = "§8[§6DMLS - Container Export§8] §7";
    private static final String OPERATION_ID = "container_export";
    private static final int MAX_AREA_CONTAINERS = 500;

    // NOTE: KeyMapping categories changed in recent versions. Adjust to match your project.
    private static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath("dmls", "container_export"));

    private final List<ContainerTarget> selected = new ArrayList<>();
    private Component lastStatus = Component.empty();
    private boolean lastStatusIsError;
    private String lastExportPath = "";
    private Object lastLevel;
    private long loadedTicks;

    private BlockPos corner1;
    private BlockPos corner2;

    public EventsContainerExportModule() {
        super(DepartmentRank.EVENTS_ADMIN);
    }

    @Override
    public Component displayName() {
        return Component.translatable("dmls.container_export.name");
    }

    @Override
    public ItemStack icon() {
        return new ItemStack(Items.BARREL);
    }

    @Override
    public List<Component> description() {
        return List.of(
                Component.translatable("dmls.container_export.description.pick"),
                Component.translatable("dmls.container_export.description.process"));
    }

    @Override
    public ModuleCategory category() {
        return ModuleCategory.EVENTS;
    }

    @Override
    public void openScreen(Minecraft client, Screen parent) {
        client.gui.setScreen(new ContainerExportScreen(parent, this));
    }

    @Override
    public void register() {
        ContainerSelectionController.register();

        KeyMapping corner1Key = KeyMappingHelper.registerKeyMapping(
                new KeyMapping("key.dmls.container_export.corner1",
                        InputConstants.Type.KEYBOARD, InputConstants.UNKNOWN.getValue(), CATEGORY));
        KeyMapping corner2Key = KeyMappingHelper.registerKeyMapping(
                new KeyMapping("key.dmls.container_export.corner2",
                        InputConstants.Type.KEYBOARD, InputConstants.UNKNOWN.getValue(), CATEGORY));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.level != lastLevel) {
                lastLevel = client.level;
                loadedTicks = 0L;
                // Corners from another dimension or world are meaningless.
                corner1 = null;
                corner2 = null;
            }
            if (client.level != null) loadedTicks++;
            pruneSelection(client);

            while (corner1Key.consumeClick()) pickLookedAtCorner(client, true);
            while (corner2Key.consumeClick()) pickLookedAtCorner(client, false);
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            selected.clear();
            corner1 = null;
            corner2 = null;
        });
    }

    // ---- state used by the screen ---------------------------------------------------------

    public List<ContainerTarget> selected() {
        return List.copyOf(selected);
    }

    public int selectionCount() {
        return selected.size();
    }

    public void clearSelection() {
        selected.clear();
        lastStatus = Component.translatable("dmls.container_export.status.cleared");
        lastStatusIsError = false;
    }

    public Component lastStatus() {
        return lastStatus;
    }

    public boolean lastStatusIsError() {
        return lastStatusIsError;
    }

    public String lastExportPath() {
        return lastExportPath;
    }

    // ---- area selection -------------------------------------------------------------------

    public BlockPos corner1() {
        return corner1;
    }

    public BlockPos corner2() {
        return corner2;
    }

    public void clearCorners() {
        corner1 = null;
        corner2 = null;
    }

    public void setCorner(boolean first, BlockPos pos) {
        if (first) corner1 = pos.immutable();
        else corner2 = pos.immutable();
        lastStatus = Component.translatable("dmls.container_export.status.corner",
                first ? 1 : 2, pos.getX(), pos.getY(), pos.getZ());
        lastStatusIsError = false;
    }

    private void pickLookedAtCorner(Minecraft client, boolean first) {
        if (client.player == null || !(client.hitResult instanceof BlockHitResult hit)
                || hit.getType() != HitResult.Type.BLOCK) {
            return;
        }
        setCorner(first, hit.getBlockPos());
        ChatUtils.sendClientMessage(client, PREFIX + lastStatus.getString());
    }

    /** Adds every container inside the two corners to the current selection. */
    public void addAreaToSelection(Minecraft client) {
        if (client.level == null) return;
        if (corner1 == null || corner2 == null) {
            fail("dmls.container_export.status.area.no_corners", OperationStartResult.INVALID);
            return;
        }

        BoundingBox box = BoundingBox.fromCorners(corner1, corner2);
        String dimension = client.level.dimension().identifier().toString();
        int unloaded = 0;
        int added = 0;
        boolean capped = false;

        outer:
        for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) {
            for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) {
                LevelChunk chunk = client.level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) {
                    unloaded++;
                    continue;
                }
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    BlockPos pos = be.getBlockPos();
                    if (!box.isInside(pos) || !isExportable(client, be)) continue;
                    if (alreadySelected(dimension, pos)) continue;
                    if (selected.size() >= MAX_AREA_CONTAINERS) {
                        capped = true;
                        break outer;
                    }
                    selected.add(new ContainerTarget(dimension, pos.immutable()));
                    added++;
                }
            }
        }

        lastStatus = Component.translatable(
                capped ? "dmls.container_export.status.area.capped"
                        : unloaded > 0 ? "dmls.container_export.status.area.partial"
                          : "dmls.container_export.status.area.added",
                added, selected.size(), unloaded);
        lastStatusIsError = added == 0 || capped;
    }

    /** Same rule pruneSelection uses, so area picks are not pruned on the next tick. */
    private static boolean isExportable(Minecraft client, BlockEntity be) {
        if (!(be instanceof Container)) return false;
        BlockState state = client.level.getBlockState(be.getBlockPos());
        if (state.getBlock() instanceof EnderChestBlock) return false;
        // A double chest is one inventory, so keep one half only.
        return !(state.getBlock() instanceof ChestBlock
                && state.getValue(ChestBlock.TYPE) == ChestType.RIGHT);
    }

    private boolean alreadySelected(String dimension, BlockPos pos) {
        for (ContainerTarget target : selected) {
            if (dimension.equals(target.dimension()) && pos.equals(target.position())) return true;
        }
        return false;
    }

    // ---- actions --------------------------------------------------------------------------

    public void beginSelection(Minecraft client, Screen screen) {
        ContainerSelectionController.begin(client, screen, selected, picked -> {
            selected.clear();
            selected.addAll(picked);
            lastStatus = Component.translatable(picked.size() == 1
                    ? "dmls.container_export.status.selected.one"
                    : "dmls.container_export.status.selected.many", picked.size());
            lastStatusIsError = false;
        });
    }

    public OperationStartResult startExport(Minecraft client, ContainerExportScreen source) {
        if (selected.isEmpty()) {
            return fail("dmls.validation.container_export.no_selection", OperationStartResult.INVALID);
        }
        if (client.player == null || client.level == null) {
            return fail("dmls.validation.container_export.no_world", OperationStartResult.INVALID);
        }
        if (!canRunPrivilegedOperation(client)) return OperationStartResult.SERVER_BLOCKED;
        if (!DMLSConfig.dryRun() && !client.player.getMainHandItem().isEmpty()) {
            return fail("dmls.validation.container_export.empty_hand", OperationStartResult.INVALID);
        }

        Screen returnParent = source.returnParent();
        ContainerExportOperation operation = new ContainerExportOperation(selected, () -> loadedTicks,
                new ContainerExportOperation.Listener() {
                    @Override
                    public void progress(int done, int total, String status) {
                        client.gui.hud.setOverlayMessage(Component.translatable(
                                "dmls.container_export.progress", status, done, total), false);
                    }

                    @Override
                    public void finished(ContainerExportResult result) {
                        // The result text is produced by the export operation itself.
                        lastStatus = Component.literal(result.message());
                        lastStatusIsError = result.isError();
                        if (!result.filePath().isBlank()) lastExportPath = result.filePath();
                        ChatUtils.sendClientMessage(client, PREFIX + result.message());
                        for (String note : result.notes()) {
                            ChatUtils.sendClientMessage(client, PREFIX + "§8- " + note);
                        }
                        client.execute(() -> client.gui.setScreen(
                                new ContainerExportScreen(returnParent, EventsContainerExportModule.this)));
                    }
                });
        OperationStartResult started = OperationCoordinator.global().start(
                client, OPERATION_ID, Component.translatable("dmls.container_export.name").getString(), operation);
        if (started != OperationStartResult.STARTED) {
            ChatUtils.sendTranslatedMessage(client, PREFIX, switch (started) {
                case BUSY -> "dmls.chat.container_export.busy";
                case SERVER_BLOCKED -> "dmls.chat.container_export.server_blocked";
                default -> "dmls.chat.container_export.failed";
            });
        }
        return started;
    }

    private OperationStartResult fail(String translationKey, OperationStartResult result) {
        lastStatus = Component.translatable(translationKey);
        lastStatusIsError = true;
        return result;
    }

    /** Drops selected containers that have been broken or replaced since they were picked. */
    private void pruneSelection(Minecraft client) {
        if (client.level == null || selected.isEmpty()) return;
        String dimension = client.level.dimension().identifier().toString();
        selected.removeIf(target -> dimension.equals(target.dimension())
                && client.level.hasChunkAt(target.position())
                && (!(client.level.getBlockEntity(target.position()) instanceof Container)
                || client.level.getBlockState(target.position()).getBlock() instanceof EnderChestBlock));
    }
}