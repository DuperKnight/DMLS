package com.duperknight.client.reimbursement;

import com.mojang.blaze3d.platform.InputConstants;

import com.duperknight.DMLS;
import net.fabricmc.fabric.api.event.client.player.ClientPreAttackCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.EnderChestBlock;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.BlockHitResult;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/** Modal in-world container picker with mouse callbacks, HUD guidance, and green outlines. */
public final class ContainerSelectionController {
    private static final int OUTLINE_COLOR = 0xFF39FF66;
    private static final int TINT_COLOR = 0x4039FF66;
    private static final Set<ContainerTarget> selected = new LinkedHashSet<>();
    private static boolean registered;
    private static boolean active;
    private static boolean enterWasDown;
    private static boolean escapeWasDown;
    private static Screen returnScreen;
    private static Consumer<List<ContainerTarget>> confirmation;
    private static String status = "";

    private ContainerSelectionController() {
    }

    public static void register() {
        if (registered) return;
        registered = true;
        ClientPreAttackCallback.EVENT.register((client, player, clickCount) -> {
            if (!active) return false;
            if (clickCount != 0 && client.hitResult instanceof BlockHitResult hit) {
                select(hit.getBlockPos(), false);
            }
            return true;
        });
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (!active || !world.isClientSide()) return InteractionResult.PASS;
            if (hand == InteractionHand.MAIN_HAND) select(hit.getBlockPos(), true);
            return InteractionResult.FAIL;
        });
        UseItemCallback.EVENT.register((player, world, hand) ->
                active && world.isClientSide() ? InteractionResult.FAIL : InteractionResult.PASS);
        UseEntityCallback.EVENT.register((player, world, hand, entity, hit) ->
                active && world.isClientSide() ? InteractionResult.FAIL : InteractionResult.PASS);
        ClientTickEvents.END_CLIENT_TICK.register(ContainerSelectionController::tick);
        // 26.x: WorldRenderEvents.BEFORE_DEBUG_RENDER became LevelRenderEvents.DEBUG_RENDER (interface DebugRender).
        LevelRenderEvents.BEFORE_GIZMOS.register(ContainerSelectionController::renderSelections);
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(DMLS.MOD_ID, "reimbursement_container_selection"),
                (context, tickCounter) -> renderHud(context));
    }

    public static void begin(
            Minecraft client,
            Screen screen,
            List<ContainerTarget> initial,
            Consumer<List<ContainerTarget>> onConfirmed
    ) {
        if (client == null || client.level == null || screen == null || onConfirmed == null) return;
        selected.clear();
        selected.addAll(initial);
        returnScreen = screen;
        confirmation = onConfirmed;
        status = "";
        active = true;
        enterWasDown = true;
        escapeWasDown = true;
        if (client.gameMode != null) client.gameMode.stopDestroyBlock();
        client.gui.setScreen(null);
    }

    public static boolean active() {
        return active;
    }

    private static void select(BlockPos clicked, boolean toggle) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return;
        BlockPos pos = canonicalChest(clicked);
        if (!isContainer(pos)) {
            status = "That block is not a supported physical container.";
            return;
        }
        ContainerTarget target = new ContainerTarget(
                client.level.dimension().identifier().toString(), pos);
        if (toggle) {
            if (!selected.remove(target)) selected.add(target);
        } else {
            selected.clear();
            selected.add(target);
        }
        status = selected.size() + (selected.size() == 1 ? " container selected." : " containers selected.");
    }

    private static boolean isContainer(BlockPos pos) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || !(client.level.getBlockEntity(pos) instanceof Container)) return false;
        return !(client.level.getBlockState(pos).getBlock() instanceof EnderChestBlock);
    }

    private static BlockPos canonicalChest(BlockPos pos) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || !(client.level.getBlockState(pos).getBlock() instanceof ChestBlock)) {
            return pos.immutable();
        }
        var state = client.level.getBlockState(pos);
        ChestType type = state.getValue(ChestBlock.TYPE);
        if (type == ChestType.SINGLE) return pos.immutable();
        BlockPos canonical = pos.immutable();
        for (var direction : net.minecraft.core.Direction.Plane.HORIZONTAL) {
            BlockPos neighbor = pos.relative(direction);
            var neighborState = client.level.getBlockState(neighbor);
            if (neighborState.getBlock() == state.getBlock()
                    && neighborState.getValue(ChestBlock.FACING) == state.getValue(ChestBlock.FACING)
                    && neighborState.getValue(ChestBlock.TYPE) == type.getOpposite()
                    && compare(neighbor, canonical) < 0) {
                canonical = neighbor.immutable();
            }
        }
        return canonical;
    }

    private static int compare(BlockPos first, BlockPos second) {
        int x = Integer.compare(first.getX(), second.getX());
        if (x != 0) return x;
        int y = Integer.compare(first.getY(), second.getY());
        return y != 0 ? y : Integer.compare(first.getZ(), second.getZ());
    }

    private static void tick(Minecraft client) {
        if (!active) return;
        if (client.level == null || client.player == null) {
            cancel(client);
            return;
        }
        int previousSize = selected.size();
        String currentDimension = client.level.dimension().identifier().toString();
        selected.removeIf(target -> !currentDimension.equals(target.dimension())
                || (client.level.hasChunkAt(target.position()) && !isContainer(target.position())));
        if (selected.size() != previousSize) {
            status = "A selected container was removed because it no longer exists.";
        }
        boolean enter = keyDown(client, InputConstants.KEY_RETURN) || keyDown(client, InputConstants.KEY_NUMPADENTER);
        boolean escape = keyDown(client, InputConstants.KEY_ESCAPE);
        if (enter && !enterWasDown) confirm(client);
        else if (escape && !escapeWasDown) cancel(client);
        enterWasDown = enter;
        escapeWasDown = escape;
    }

    private static boolean keyDown(Minecraft client, int key) {
        return InputConstants.isKeyDown(key);
    }

    private static void confirm(Minecraft client) {
        Consumer<List<ContainerTarget>> callback = confirmation;
        List<ContainerTarget> result = List.copyOf(selected);
        Screen screen = returnScreen;
        reset();
        if (callback != null) callback.accept(result);
        client.gui.setScreen(screen);
    }

    private static void cancel(Minecraft client) {
        Screen screen = returnScreen;
        reset();
        client.gui.setScreen(screen);
    }

    private static void reset() {
        active = false;
        selected.clear();
        returnScreen = null;
        confirmation = null;
        status = "";
    }

    private static void renderHud(net.minecraft.client.gui.GuiGraphicsExtractor context) {
        if (!active) return;
        Minecraft client = Minecraft.getInstance();
        Component instructions = Component.literal(
                "Left-click: select one  •  Right-click: toggle multiple  •  Enter: confirm  •  Esc: cancel");
        int x = (context.guiWidth() - client.font.width(instructions)) / 2;
        context.fill(x - 6, 8, x + client.font.width(instructions) + 6,
                8 + client.font.lineHeight + 6, 0xC0000000);
        context.text(client.font, instructions, x, 12, 0xFFFFFFFF);
        if (!status.isEmpty()) {
            int statusX = (context.guiWidth() - client.font.width(status)) / 2;
            context.text(client.font, status, statusX,
                    25, status.startsWith("That") ? 0xFFFF5555 : 0xFF55FF55);
        }
    }

    private static void renderSelections(LevelRenderContext context) {
        if (!active || selected.isEmpty()) return;
        GizmoStyle style = GizmoStyle.strokeAndFill(OUTLINE_COLOR, 3.0F, TINT_COLOR);
        for (ContainerTarget target : selected) {
            Gizmos.cuboid(target.position(), 0.002F, style).setAlwaysOnTop();
        }
    }
}