package com.duperknight.client.reimbursement;

import com.duperknight.DMLS;
import net.fabricmc.fabric.api.event.client.player.ClientPreAttackCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.EnderChestBlock;
import net.minecraft.block.enums.ChestType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.DrawStyle;
import net.minecraft.inventory.Inventory;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.debug.gizmo.GizmoDrawing;
import org.lwjgl.glfw.GLFW;

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
            if (clickCount != 0 && client.crosshairTarget instanceof BlockHitResult hit) {
                select(hit.getBlockPos(), false);
            }
            return true;
        });
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (!active || !world.isClient()) return ActionResult.PASS;
            if (hand == Hand.MAIN_HAND) select(hit.getBlockPos(), true);
            return ActionResult.FAIL;
        });
        UseItemCallback.EVENT.register((player, world, hand) ->
                active && world.isClient() ? ActionResult.FAIL : ActionResult.PASS);
        UseEntityCallback.EVENT.register((player, world, hand, entity, hit) ->
                active && world.isClient() ? ActionResult.FAIL : ActionResult.PASS);
        ClientTickEvents.END_CLIENT_TICK.register(ContainerSelectionController::tick);
        WorldRenderEvents.BEFORE_DEBUG_RENDER.register(ContainerSelectionController::renderSelections);
        HudElementRegistry.addLast(Identifier.of(DMLS.MOD_ID, "reimbursement_container_selection"),
                (context, tickCounter) -> renderHud(context));
    }

    public static void begin(
            MinecraftClient client,
            Screen screen,
            List<ContainerTarget> initial,
            Consumer<List<ContainerTarget>> onConfirmed
    ) {
        if (client == null || client.world == null || screen == null || onConfirmed == null) return;
        selected.clear();
        selected.addAll(initial);
        returnScreen = screen;
        confirmation = onConfirmed;
        status = "";
        active = true;
        enterWasDown = true;
        escapeWasDown = true;
        if (client.interactionManager != null) client.interactionManager.cancelBlockBreaking();
        client.setScreen(null);
    }

    public static boolean active() {
        return active;
    }

    private static void select(BlockPos clicked, boolean toggle) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) return;
        BlockPos pos = canonicalChest(clicked);
        if (!isContainer(pos)) {
            status = "That block is not a supported physical container.";
            return;
        }
        ContainerTarget target = new ContainerTarget(
                client.world.getRegistryKey().getValue().toString(), pos);
        if (toggle) {
            if (!selected.remove(target)) selected.add(target);
        } else {
            selected.clear();
            selected.add(target);
        }
        status = selected.size() + (selected.size() == 1 ? " container selected." : " containers selected.");
    }

    private static boolean isContainer(BlockPos pos) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || !(client.world.getBlockEntity(pos) instanceof Inventory)) return false;
        return !(client.world.getBlockState(pos).getBlock() instanceof EnderChestBlock);
    }

    private static BlockPos canonicalChest(BlockPos pos) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || !(client.world.getBlockState(pos).getBlock() instanceof ChestBlock)) {
            return pos.toImmutable();
        }
        var state = client.world.getBlockState(pos);
        ChestType type = state.get(ChestBlock.CHEST_TYPE);
        if (type == ChestType.SINGLE) return pos.toImmutable();
        BlockPos canonical = pos.toImmutable();
        for (var direction : net.minecraft.util.math.Direction.Type.HORIZONTAL) {
            BlockPos neighbor = pos.offset(direction);
            var neighborState = client.world.getBlockState(neighbor);
            if (neighborState.getBlock() == state.getBlock()
                    && neighborState.get(ChestBlock.FACING) == state.get(ChestBlock.FACING)
                    && neighborState.get(ChestBlock.CHEST_TYPE) == type.getOpposite()
                    && compare(neighbor, canonical) < 0) {
                canonical = neighbor.toImmutable();
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

    private static void tick(MinecraftClient client) {
        if (!active) return;
        if (client.world == null || client.player == null) {
            cancel(client);
            return;
        }
        int previousSize = selected.size();
        String currentDimension = client.world.getRegistryKey().getValue().toString();
        selected.removeIf(target -> !currentDimension.equals(target.dimension())
                || (client.world.isChunkLoaded(target.position()) && !isContainer(target.position())));
        if (selected.size() != previousSize) {
            status = "A selected container was removed because it no longer exists.";
        }
        boolean enter = keyDown(client, GLFW.GLFW_KEY_ENTER) || keyDown(client, GLFW.GLFW_KEY_KP_ENTER);
        boolean escape = keyDown(client, GLFW.GLFW_KEY_ESCAPE);
        if (enter && !enterWasDown) confirm(client);
        else if (escape && !escapeWasDown) cancel(client);
        enterWasDown = enter;
        escapeWasDown = escape;
    }

    private static boolean keyDown(MinecraftClient client, int key) {
        return org.lwjgl.glfw.GLFW.glfwGetKey(client.getWindow().getHandle(), key) == GLFW.GLFW_PRESS;
    }

    private static void confirm(MinecraftClient client) {
        Consumer<List<ContainerTarget>> callback = confirmation;
        List<ContainerTarget> result = List.copyOf(selected);
        Screen screen = returnScreen;
        reset();
        if (callback != null) callback.accept(result);
        client.setScreen(screen);
    }

    private static void cancel(MinecraftClient client) {
        Screen screen = returnScreen;
        reset();
        client.setScreen(screen);
    }

    private static void reset() {
        active = false;
        selected.clear();
        returnScreen = null;
        confirmation = null;
        status = "";
    }

    private static void renderHud(net.minecraft.client.gui.DrawContext context) {
        if (!active) return;
        MinecraftClient client = MinecraftClient.getInstance();
        Text instructions = Text.literal(
                "Left-click: select one  •  Right-click: toggle multiple  •  Enter: confirm  •  Esc: cancel");
        int x = (context.getScaledWindowWidth() - client.textRenderer.getWidth(instructions)) / 2;
        context.fill(x - 6, 8, x + client.textRenderer.getWidth(instructions) + 6,
                8 + client.textRenderer.fontHeight + 6, 0xC0000000);
        context.drawTextWithShadow(client.textRenderer, instructions, x, 12, 0xFFFFFFFF);
        if (!status.isEmpty()) {
            int statusX = (context.getScaledWindowWidth() - client.textRenderer.getWidth(status)) / 2;
            context.drawTextWithShadow(client.textRenderer, status, statusX,
                    25, status.startsWith("That") ? 0xFFFF5555 : 0xFF55FF55);
        }
    }

    private static void renderSelections(WorldRenderContext context) {
        if (!active || selected.isEmpty()) return;
        DrawStyle style = DrawStyle.filledAndStroked(OUTLINE_COLOR, 3.0F, TINT_COLOR);
        for (ContainerTarget target : selected) {
            GizmoDrawing.box(target.position(), 0.002F, style).ignoreOcclusion();
        }
    }
}
