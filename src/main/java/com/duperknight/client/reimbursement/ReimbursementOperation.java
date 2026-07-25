package com.duperknight.client.reimbursement;

import com.duperknight.client.message.ServerMessage;
import com.duperknight.client.modules.StaffRank;
import com.duperknight.client.session.CommandDispatch;
import com.duperknight.client.session.ManagedOperation;
import com.duperknight.client.session.OperationCancelReason;
import com.duperknight.client.session.OperationHandle;
import com.duperknight.client.session.OutboundSpamSafety;
import com.duperknight.client.utils.DMLSConfig;
import com.duperknight.client.utils.ScreenUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Preflight-first, partial-result-preserving reimbursement state machine. */
public final class ReimbursementOperation implements ManagedOperation {
    private static final int SCREEN_TIMEOUT_TICKS = 20 * 5;
    private static final int INVENTORY_SYNC_TIMEOUT_TICKS = 20 * 5;
    private static final int GENERIC_COMMAND_WAIT_TICKS = 12;
    private static final double INTERACTION_DISTANCE_SQUARED = 16.0;

    public interface Listener {
        void progress(int completedSteps, int totalSteps, int estimatedSecondsRemaining, String status);

        void finished(ReimbursementResult result);
    }

    private enum State {
        STARTING,
        PREFLIGHT_PLAYER_COMMAND,
        PREFLIGHT_PLAYER_SCREEN,
        PREFLIGHT_CONTAINER,
        PREFLIGHT_TELEPORT_WAIT,
        PREFLIGHT_CONTAINER_SCREEN,
        RETURN_AFTER_SCAN_COMMAND,
        RETURN_AFTER_SCAN_WAIT,
        VALIDATE_CAPACITY,
        EXECUTE_NEXT,
        BUILD_DISPATCH,
        BUILD_WAIT,
        PLAYER_ROUTE_COMMAND,
        PLAYER_ROUTE_SCREEN,
        CONTAINER_ROUTE,
        CONTAINER_TELEPORT_WAIT,
        CONTAINER_ROUTE_SCREEN,
        RETURN_FINAL_COMMAND,
        RETURN_FINAL_WAIT,
        TERMINAL
    }

    private final ReimbursementPlan plan;
    private final ReimbursementCommandPlanner.BuildResult commands;
    private final Listener listener;
    private final Map<String, Integer> playerCapacity = new HashMap<>();
    private final Map<ContainerTarget, Integer> containerCapacity = new HashMap<>();
    private final List<String> completed = new ArrayList<>();
    private final List<String> retained = new ArrayList<>();
    private final List<String> remaining = new ArrayList<>();
    private final int totalSteps;

    private OperationHandle handle;
    private MinecraftClient client;
    private State state = State.STARTING;
    private boolean admin;
    private int waitTicks;
    private int completedSteps;
    private int playerScanIndex;
    private int containerScanIndex;
    private int entryIndex;
    private int stackIndex;
    private int buildCommandIndex;
    private int routeContainerIndex;
    private int operationTicks;
    private int selectedHotbar = -1;
    private int currentStackPlanCount = 1;
    private int inventoryItemCountBefore;
    private boolean liveReimbursementSent;
    private boolean failed;
    private String failureMessage = "";
    private String status = "Preparing reimbursement";
    private Vec3d origin;
    private float originYaw;
    private float originPitch;
    private String originDimension = "";
    private ReimbursementCommandPlanner.StackPlan currentStack;
    private List<Integer> emptySlotsBeforeGive = List.of();
    private final List<Integer> stagedInventorySlots = new ArrayList<>();

    public ReimbursementOperation(ReimbursementPlan plan, Listener listener) {
        this.plan = plan;
        this.listener = listener;
        this.commands = ReimbursementCommandPlanner.build(plan);
        this.totalSteps = Math.max(1, commands.commandCount()
                + plan.requiredStacks() + plan.playerTargets().size() + plan.containerTargets().size() + 2);
    }

    public boolean acceptedAtStart() {
        return state != State.TERMINAL;
    }

    public boolean sentLiveReimbursement() {
        return liveReimbursementSent;
    }

    @Override
    public void onStarted(OperationHandle handle, MinecraftClient client) {
        this.handle = handle;
        this.client = client;
        this.admin = DMLSConfig.staffRank() == StaffRank.ADMIN;
        if (client.player == null || client.world == null) {
            finishFailure("The player or world is no longer available.");
            return;
        }
        origin = client.player.getEntityPos();
        originYaw = client.player.getYaw();
        originPitch = client.player.getPitch();
        originDimension = dimension(client);

        if (handle.descriptor().dryRunCaptured()) {
            List<String> preview = dryRunPreview();
            listener.finished(new ReimbursementResult(
                    ReimbursementResult.Kind.DRY_RUN, "", preview, List.of(), List.of(),
                    "Dry run only: no inventories were opened and no commands were sent."));
            state = State.TERMINAL;
            handle.complete();
            return;
        }

        client.setScreen(null);
        state = plan.playerTargets().isEmpty() ? State.PREFLIGHT_CONTAINER : State.PREFLIGHT_PLAYER_COMMAND;
        publish("Checking destination capacity");
    }

    @Override
    public void onTick(OperationHandle handle, MinecraftClient client) {
        if (state == State.TERMINAL) return;
        this.client = client;
        operationTicks++;
        waitTicks++;
        switch (state) {
            case PREFLIGHT_PLAYER_COMMAND -> preflightPlayerCommand();
            case PREFLIGHT_PLAYER_SCREEN -> preflightPlayerScreen();
            case PREFLIGHT_CONTAINER -> preflightContainer();
            case PREFLIGHT_TELEPORT_WAIT -> preflightTeleportWait();
            case PREFLIGHT_CONTAINER_SCREEN -> preflightContainerScreen();
            case RETURN_AFTER_SCAN_COMMAND -> returnAfterScanCommand();
            case RETURN_AFTER_SCAN_WAIT -> {
                if (waitTicks >= GENERIC_COMMAND_WAIT_TICKS) transition(State.VALIDATE_CAPACITY);
            }
            case VALIDATE_CAPACITY -> validateCapacity();
            case EXECUTE_NEXT -> executeNext();
            case BUILD_DISPATCH -> buildDispatch();
            case BUILD_WAIT -> buildWait();
            case PLAYER_ROUTE_COMMAND -> playerRouteCommand();
            case PLAYER_ROUTE_SCREEN -> playerRouteScreen();
            case CONTAINER_ROUTE -> containerRoute();
            case CONTAINER_TELEPORT_WAIT -> containerTeleportWait();
            case CONTAINER_ROUTE_SCREEN -> containerRouteScreen();
            case RETURN_FINAL_COMMAND -> returnFinalCommand();
            case RETURN_FINAL_WAIT -> {
                if (waitTicks >= GENERIC_COMMAND_WAIT_TICKS) finishTerminal();
            }
            default -> {
            }
        }
    }

    @Override
    public void onServerMessage(OperationHandle handle, MinecraftClient client, ServerMessage message) {
        if (state == State.TERMINAL || failed || !isServerRejection(message.cleanText())) return;
        failed = true;
        failureMessage = "The server rejected a reimbursement step: " + message.cleanText();
        if (state == State.RETURN_FINAL_COMMAND || state == State.RETURN_FINAL_WAIT) {
            finishTerminal();
            return;
        }
        if (origin != null && client.player != null && client.player.squaredDistanceTo(origin) >= 1.0) {
            transition(State.RETURN_FINAL_COMMAND);
        } else {
            finishTerminal();
        }
    }

    static boolean isServerRejection(String message) {
        String clean = message == null ? "" : message.toLowerCase(Locale.ROOT);
        return clean.contains("unknown command") || clean.contains("incorrect argument")
                || clean.contains("no permission") || clean.contains("not permitted")
                || clean.contains("player not found") || clean.contains("cannot find");
    }

    @Override
    public void onCancelled(OperationHandle handle, MinecraftClient client, OperationCancelReason reason) {
        if (state == State.TERMINAL) return;
        failed = true;
        failureMessage = "Reimbursement cancelled: " + reason.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        state = State.TERMINAL;
        listener.finished(liveReimbursementSent
                ? partialResult()
                : new ReimbursementResult(ReimbursementResult.Kind.PREFLIGHT_REJECTED,
                "", completed, retained, remainingEntries(), failureMessage));
    }

    private void preflightPlayerCommand() {
        if (playerScanIndex >= plan.playerTargets().size()) {
            transition(State.PREFLIGHT_CONTAINER);
            return;
        }
        if (!safeToDispatch()) return;
        closeHandledScreen();
        String ign = plan.playerTargets().get(playerScanIndex);
        if (dispatch("invsee " + ign) == CommandDispatch.BLOCKED) {
            failAndReturn("Could not open /invsee for " + ign + ".");
            return;
        }
        transition(State.PREFLIGHT_PLAYER_SCREEN);
        publish("Checking " + ign + "'s inventory");
    }

    private void preflightPlayerScreen() {
        String ign = plan.playerTargets().get(playerScanIndex);
        if (client.currentScreen instanceof HandledScreen<?> screen
                && screen.getTitle().getString().toLowerCase(Locale.ROOT).contains(ign)) {
            playerCapacity.put(ign, countEmptySlots(screen.getScreenHandler(), 0, 36, null));
            closeHandledScreen();
            playerScanIndex++;
            completedSteps++;
            transition(State.PREFLIGHT_PLAYER_COMMAND);
            return;
        }
        if (waitTicks >= SCREEN_TIMEOUT_TICKS) failAndReturn("Timed out opening /invsee for " + ign + ".");
    }

    private void preflightContainer() {
        if (containerScanIndex >= plan.containerTargets().size()) {
            transition(State.RETURN_AFTER_SCAN_COMMAND);
            return;
        }
        ContainerTarget target = plan.containerTargets().get(containerScanIndex);
        if (!originDimension.equals(target.dimension())) {
            failAndReturn("A selected container is in another dimension. Reselect it before starting.");
            return;
        }
        if (client.player.squaredDistanceTo(Vec3d.ofCenter(target.position())) > INTERACTION_DISTANCE_SQUARED) {
            if (!safeToDispatch()) return;
            if (dispatch(teleportCommand(target.position())) == CommandDispatch.BLOCKED) {
                failAndReturn("Could not teleport to the selected container.");
                return;
            }
            transition(State.PREFLIGHT_TELEPORT_WAIT);
            return;
        }
        if (!containerChunkLoaded(target)) {
            if (waitTicks >= SCREEN_TIMEOUT_TICKS) removePreflightContainer(target);
            return;
        }
        if (!containerStillValid(target)) {
            removePreflightContainer(target);
            return;
        }
        interactContainer(target);
        transition(State.PREFLIGHT_CONTAINER_SCREEN);
    }

    private void preflightTeleportWait() {
        ContainerTarget target = plan.containerTargets().get(containerScanIndex);
        boolean withinReach = client.player.squaredDistanceTo(Vec3d.ofCenter(target.position()))
                <= INTERACTION_DISTANCE_SQUARED;
        if (withinReach && containerChunkLoaded(target)) {
            if (!containerStillValid(target)) {
                removePreflightContainer(target);
                return;
            }
            interactContainer(target);
            transition(State.PREFLIGHT_CONTAINER_SCREEN);
            return;
        }
        if (waitTicks >= SCREEN_TIMEOUT_TICKS) {
            removePreflightContainer(target);
        }
    }

    private void preflightContainerScreen() {
        ContainerTarget target = plan.containerTargets().get(containerScanIndex);
        if (!containerStillValid(target)) {
            closeHandledScreen();
            containerScanIndex++;
            transition(State.PREFLIGHT_CONTAINER);
            return;
        }
        if (client.currentScreen instanceof HandledScreen<?> screen) {
            containerCapacity.put(target, countContainerSlots(screen.getScreenHandler(), null));
            closeHandledScreen();
            containerScanIndex++;
            completedSteps++;
            transition(State.PREFLIGHT_CONTAINER);
            publish("Checked container " + containerScanIndex + " of " + plan.containerTargets().size());
            return;
        }
        if (waitTicks >= SCREEN_TIMEOUT_TICKS) failAndReturn("Timed out opening a selected container.");
    }

    private void returnAfterScanCommand() {
        if (origin == null || client.player.squaredDistanceTo(origin) < 1.0) {
            transition(State.VALIDATE_CAPACITY);
            return;
        }
        if (!safeToDispatch()) return;
        if (dispatch(returnCommand()) == CommandDispatch.BLOCKED) {
            failAndReturn("Could not return to the starting position after preflight.");
            return;
        }
        transition(State.RETURN_AFTER_SCAN_WAIT);
    }

    private void validateCapacity() {
        int staffSlots = (int) client.player.getInventory().getMainStacks().stream()
                .filter(ItemStack::isEmpty).count();
        if (!commands.stacks().isEmpty() && staffSlots == 0) {
            failed = true;
            failureMessage = "The staff inventory has no empty staging slot, so no items were created.";
            finishTerminal();
            return;
        }
        ReimbursementCapacityPlanner.Result capacity = ReimbursementCapacityPlanner.simulate(
                plan, staffSlots, playerCapacity, containerCapacity);
        if (!capacity.fits()) {
            failed = true;
            failureMessage = "Not enough combined destination and staff space; "
                    + capacity.missingSlots() + " additional slot(s) are required.";
            finishTerminal();
            return;
        }
        transition(State.EXECUTE_NEXT);
        publish("Capacity confirmed; starting reimbursement");
    }

    private void executeNext() {
        if (entryIndex >= plan.draft().entries().size()) {
            transition(State.RETURN_FINAL_COMMAND);
            return;
        }
        ReimbursementEntry entry = plan.draft().entries().get(entryIndex);
        if (entry instanceof MoneyEntry money) {
            if (!safeToDispatch()) return;
            String recipient = money.destination() == Destination.ME
                    ? staffIgn() : money.playerIgn().trim();
            String command = "eco give " + recipient + " " + money.amount().stripTrailingZeros().toPlainString();
            if (dispatch(command) == CommandDispatch.BLOCKED) {
                failAndReturn("Could not send the economy reimbursement.");
                return;
            }
            liveReimbursementSent = true;
            completed.add("$" + money.amount().toPlainString());
            completedSteps++;
            entryIndex++;
            waitTicks = 0;
            publish("Reimbursed money to " + recipient);
            return;
        }

        ItemEntry itemEntry = (ItemEntry) entry;
        if (stackIndex >= stackPlansBeforeEntry(entryIndex + 1) - stackPlansBeforeEntry(entryIndex)) {
            entryIndex++;
            stackIndex = 0;
            transition(State.EXECUTE_NEXT);
            return;
        }
        if (!reserveHotbarSlot()) {
            failAndReturn("The staff inventory no longer has a safe staging slot.");
            return;
        }
        int stackCursor = stackPlansBeforeEntry(entryIndex) + stackIndex;
        int stacksForEntry = stackPlansBeforeEntry(entryIndex + 1) - stackPlansBeforeEntry(entryIndex);
        int freeSlots = Math.min(countEmptyStaffSlots(), stacksForEntry - stackIndex);
        ReimbursementCommandPlanner.BatchPlan batch =
                ReimbursementCommandPlanner.batch(commands.stacks(), stackCursor, freeSlots,
                        staffIgn());
        currentStack = batch.stack();
        currentStackPlanCount = batch.consumedStacks();
        emptySlotsBeforeGive = emptyStaffSlots();
        inventoryItemCountBefore = countStaffItem(currentStack.entry());
        stagedInventorySlots.clear();
        buildCommandIndex = 0;
        transition(State.BUILD_DISPATCH);
    }

    private void buildDispatch() {
        if (buildCommandIndex >= currentStack.commands().size()) {
            routeContainerIndex = 0;
            transition(switch (currentStack.entry().destination()) {
                case ME -> State.EXECUTE_NEXT;
                case PLAYER -> State.PLAYER_ROUTE_COMMAND;
                case CONTAINER -> State.CONTAINER_ROUTE;
            });
            if (currentStack.entry().destination() == Destination.ME) completeCurrentStack(0);
            return;
        }
        if (!safeToDispatch()) return;
        String command = currentStack.commands().get(buildCommandIndex);
        if (dispatch(command) == CommandDispatch.BLOCKED) {
            failAndReturn("Could not create an item stack.");
            return;
        }
        liveReimbursementSent = true;
        transition(State.BUILD_WAIT);
    }

    private void buildWait() {
        boolean giveCommand = buildCommandIndex == 0;
        if (giveCommand) {
            int received = countStaffItem(currentStack.entry()) - inventoryItemCountBefore;
            if (received >= currentStack.count()) {
                captureStagedSlots();
                if (currentStack.entry().destination() != Destination.ME
                        && stagedItemCount() < currentStack.count()) {
                    failAndReturn("The created items could not be isolated in safe staging slots.");
                    return;
                }
                buildCommandIndex++;
                completedSteps++;
                transition(State.BUILD_DISPATCH);
                return;
            }
            if (waitTicks >= INVENTORY_SYNC_TIMEOUT_TICKS) {
                failAndReturn("Timed out waiting for the created item to enter the staff inventory.");
            }
            return;
        }
        if (waitTicks >= GENERIC_COMMAND_WAIT_TICKS) {
            buildCommandIndex++;
            completedSteps++;
            transition(State.BUILD_DISPATCH);
        }
    }

    private void playerRouteCommand() {
        if (!safeToDispatch()) return;
        closeHandledScreen();
        String ign = currentStack.entry().playerIgn().trim();
        if (dispatch("invsee " + ign) == CommandDispatch.BLOCKED) {
            retainCurrentStack("Could not reopen " + ign + "'s inventory.");
            return;
        }
        transition(State.PLAYER_ROUTE_SCREEN);
    }

    private void playerRouteScreen() {
        String ign = currentStack.entry().playerIgn().trim();
        if (client.currentScreen instanceof HandledScreen<?> screen
                && screen.getTitle().getString().toLowerCase(Locale.ROOT)
                .contains(ign.toLowerCase(Locale.ROOT))) {
            moveStagedStacks(screen.getScreenHandler(), 0,
                    Math.min(36, screen.getScreenHandler().slots.size()));
            if (!hasStagedItems()) {
                closeHandledScreen();
                completeCurrentStack(0);
            } else {
                closeHandledScreen();
                retainCurrentStack(ign + "'s inventory is full.");
            }
            return;
        }
        if (waitTicks >= SCREEN_TIMEOUT_TICKS) retainCurrentStack("Timed out reopening " + ign + "'s inventory.");
    }

    private void containerRoute() {
        if (routeContainerIndex >= currentStack.entry().containers().size()) {
            retainCurrentStack("All selected containers are full.");
            return;
        }
        ContainerTarget target = currentStack.entry().containers().get(routeContainerIndex);
        if (!dimension(client).equals(target.dimension())) {
            routeContainerIndex++;
            return;
        }
        if (client.player.squaredDistanceTo(Vec3d.ofCenter(target.position())) > INTERACTION_DISTANCE_SQUARED) {
            if (!safeToDispatch()) return;
            if (dispatch(teleportCommand(target.position())) == CommandDispatch.BLOCKED) {
                routeContainerIndex++;
                return;
            }
            transition(State.CONTAINER_TELEPORT_WAIT);
            return;
        }
        if (!containerChunkLoaded(target)) {
            if (waitTicks >= SCREEN_TIMEOUT_TICKS) skipRouteContainer(target);
            return;
        }
        if (!containerStillValid(target)) {
            skipRouteContainer(target);
            return;
        }
        interactContainer(target);
        transition(State.CONTAINER_ROUTE_SCREEN);
    }

    private void containerTeleportWait() {
        ContainerTarget target = currentStack.entry().containers().get(routeContainerIndex);
        boolean withinReach = client.player.squaredDistanceTo(Vec3d.ofCenter(target.position()))
                <= INTERACTION_DISTANCE_SQUARED;
        if (withinReach && containerChunkLoaded(target)) {
            if (!containerStillValid(target)) {
                skipRouteContainer(target);
                return;
            }
            interactContainer(target);
            transition(State.CONTAINER_ROUTE_SCREEN);
            return;
        }
        if (waitTicks >= SCREEN_TIMEOUT_TICKS) {
            skipRouteContainer(target);
        }
    }

    private void containerRouteScreen() {
        if (client.currentScreen instanceof HandledScreen<?> screen) {
            ScreenHandler handler = screen.getScreenHandler();
            int containerEnd = firstPlayerInventorySlot(handler);
            moveStagedStacks(handler, 0, containerEnd);
            if (!hasStagedItems()) {
                closeHandledScreen();
                completeCurrentStack(0);
            } else {
                closeHandledScreen();
                routeContainerIndex++;
                transition(State.CONTAINER_ROUTE);
            }
            return;
        }
        if (waitTicks >= SCREEN_TIMEOUT_TICKS) {
            routeContainerIndex++;
            transition(State.CONTAINER_ROUTE);
        }
    }

    private void returnFinalCommand() {
        closeHandledScreen();
        if (origin == null || client.player.squaredDistanceTo(origin) < 1.0) {
            finishTerminal();
            return;
        }
        if (!safeToDispatch()) return;
        if (dispatch(returnCommand()) == CommandDispatch.BLOCKED) {
            failureMessage = failureMessage.isEmpty()
                    ? "Reimbursement finished, but the starting position could not be restored."
                    : failureMessage;
            finishTerminal();
            return;
        }
        transition(State.RETURN_FINAL_WAIT);
    }

    private void completeCurrentStack(int retainedCount) {
        String description = currentStack.count() + "x "
                + net.minecraft.registry.Registries.ITEM.get(currentStack.entry().itemId())
                .getDefaultStack().getName().getString();
        completed.add(description);
        if (retainedCount > 0) {
            retained.add(retainedCount + "x "
                    + net.minecraft.registry.Registries.ITEM.get(currentStack.entry().itemId())
                    .getDefaultStack().getName().getString());
        }
        completedSteps++;
        stackIndex += currentStackPlanCount;
        currentStack = null;
        currentStackPlanCount = 1;
        emptySlotsBeforeGive = List.of();
        stagedInventorySlots.clear();
        transition(State.EXECUTE_NEXT);
        publish(retainedCount > 0 ? "Destination full; retained items with staff" : "Placed " + description);
    }

    private void retainCurrentStack(String warning) {
        failureMessage = warning;
        closeHandledScreen();
        completeCurrentStack(stagedItemCount());
    }

    private boolean reserveHotbarSlot() {
        ClientPlayerEntity player = client.player;
        for (int index = 0; index < 9; index++) {
            if (player.getInventory().getStack(index).isEmpty()) {
                selectHotbar(index);
                return true;
            }
        }
        int emptyMain = -1;
        for (int index = 9; index < 36; index++) {
            if (player.getInventory().getStack(index).isEmpty()) {
                emptyMain = index;
                break;
            }
        }
        if (emptyMain < 0) return false;

        int hotbar = player.getInventory().getSelectedSlot();
        ScreenHandler handler = player.playerScreenHandler;
        Slot hotbarSlot = findPlayerSlot(handler, hotbar);
        Slot emptySlot = findPlayerSlot(handler, emptyMain);
        if (hotbarSlot == null || emptySlot == null || client.interactionManager == null) return false;
        client.interactionManager.clickSlot(handler.syncId, hotbarSlot.id, 0, SlotActionType.PICKUP, player);
        client.interactionManager.clickSlot(handler.syncId, emptySlot.id, 0, SlotActionType.PICKUP, player);
        selectHotbar(hotbar);
        return true;
    }

    private void selectHotbar(int index) {
        selectedHotbar = index;
        client.player.getInventory().setSelectedSlot(index);
        if (client.getNetworkHandler() != null) {
            client.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(index));
        }
    }

    private void moveStagedStacks(ScreenHandler handler, int start, int end) {
        if (client.interactionManager == null) return;
        int safeEnd = Math.min(end, handler.slots.size());
        for (int stagedIndex : List.copyOf(stagedInventorySlots)) {
            Slot source = findPlayerSlot(handler, stagedIndex);
            if (source == null || !source.hasStack()) {
                stagedInventorySlots.remove(Integer.valueOf(stagedIndex));
                continue;
            }
            Slot target = null;
            for (int index = Math.max(0, start); index < safeEnd; index++) {
                Slot candidate = handler.getSlot(index);
                if (candidate.inventory == client.player.getInventory()) continue;
                if (!candidate.hasStack() && candidate.canInsert(source.getStack())) {
                    target = candidate;
                    break;
                }
            }
            if (target == null) return;
            client.interactionManager.clickSlot(handler.syncId, source.id, 0,
                    SlotActionType.PICKUP, client.player);
            client.interactionManager.clickSlot(handler.syncId, target.id, 0,
                    SlotActionType.PICKUP, client.player);
            stagedInventorySlots.remove(Integer.valueOf(stagedIndex));
        }
    }

    private int countEmptyStaffSlots() {
        return (int) client.player.getInventory().getMainStacks().stream()
                .filter(ItemStack::isEmpty)
                .count();
    }

    private List<Integer> emptyStaffSlots() {
        List<Integer> slots = new ArrayList<>();
        for (int index = 0; index < 36; index++) {
            if (client.player.getInventory().getStack(index).isEmpty()) slots.add(index);
        }
        return List.copyOf(slots);
    }

    private int countStaffItem(ItemEntry entry) {
        return client.player.getInventory().getMainStacks().stream()
                .filter(stack -> stack.isOf(net.minecraft.registry.Registries.ITEM.get(entry.itemId())))
                .mapToInt(ItemStack::getCount)
                .sum();
    }

    private void captureStagedSlots() {
        stagedInventorySlots.clear();
        for (int index : emptySlotsBeforeGive) {
            ItemStack stack = client.player.getInventory().getStack(index);
            if (!stack.isEmpty() && stack.isOf(
                    net.minecraft.registry.Registries.ITEM.get(currentStack.entry().itemId()))) {
                stagedInventorySlots.add(index);
            }
        }
    }

    private boolean hasStagedItems() {
        stagedInventorySlots.removeIf(index -> {
            ItemStack stack = client.player.getInventory().getStack(index);
            return stack.isEmpty() || !stack.isOf(
                    net.minecraft.registry.Registries.ITEM.get(currentStack.entry().itemId()));
        });
        return !stagedInventorySlots.isEmpty();
    }

    private int stagedItemCount() {
        if (currentStack == null) return 0;
        hasStagedItems();
        int count = 0;
        for (int index : stagedInventorySlots) {
            count += client.player.getInventory().getStack(index).getCount();
        }
        return count;
    }

    private static Slot findPlayerSlot(ScreenHandler handler, int inventoryIndex) {
        for (Slot slot : handler.slots) {
            if (MinecraftClient.getInstance().player != null
                    && slot.inventory == MinecraftClient.getInstance().player.getInventory()
                    && slot.getIndex() == inventoryIndex) return slot;
        }
        return null;
    }

    private int countContainerSlots(ScreenHandler handler, ItemStack stack) {
        int end = firstPlayerInventorySlot(handler);
        int count = 0;
        for (int index = 0; index < end; index++) {
            Slot slot = handler.getSlot(index);
            if (slot.hasStack()) continue;
            boolean acceptsEveryPlannedItem = plan.draft().entries().stream()
                    .filter(entry -> entry instanceof ItemEntry
                            && entry.destination() == Destination.CONTAINER)
                    .map(ItemEntry.class::cast)
                    .map(entry -> net.minecraft.registry.Registries.ITEM.get(entry.itemId()).getDefaultStack())
                    .allMatch(slot::canInsert);
            if (acceptsEveryPlannedItem && (stack == null || slot.canInsert(stack))) count++;
        }
        return count;
    }

    private int firstPlayerInventorySlot(ScreenHandler handler) {
        for (int index = 0; index < handler.slots.size(); index++) {
            if (handler.getSlot(index).inventory == client.player.getInventory()) return index;
        }
        return Math.max(0, handler.slots.size() - 36);
    }

    private static int countEmptySlots(ScreenHandler handler, int start, int end, ItemStack stack) {
        int count = 0;
        int safeEnd = Math.min(end, handler.slots.size());
        for (int index = Math.max(0, start); index < safeEnd; index++) {
            Slot slot = handler.getSlot(index);
            if (!slot.hasStack() && (stack == null || slot.canInsert(stack))) count++;
        }
        return count;
    }

    private boolean containerStillValid(ContainerTarget target) {
        return containerChunkLoaded(target)
                && client.world.getBlockEntity(target.position()) instanceof Inventory
                && !(client.world.getBlockState(target.position()).getBlock()
                instanceof net.minecraft.block.EnderChestBlock);
    }

    private boolean containerChunkLoaded(ContainerTarget target) {
        return client.world != null
                && dimension(client).equals(target.dimension())
                && client.world.isChunkLoaded(target.position());
    }

    private void removePreflightContainer(ContainerTarget target) {
        containerCapacity.remove(target);
        containerScanIndex++;
        transition(State.PREFLIGHT_CONTAINER);
        publish("Removed a container that no longer exists at " + target.displayCoordinates());
    }

    private void skipRouteContainer(ContainerTarget target) {
        closeHandledScreen();
        routeContainerIndex++;
        transition(State.CONTAINER_ROUTE);
        publish("Skipped a container that no longer exists at " + target.displayCoordinates());
    }

    private void interactContainer(ContainerTarget target) {
        closeHandledScreen();
        BlockPos pos = target.position();
        BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(pos), Direction.UP, pos, false);
        client.interactionManager.interactBlock(client.player, Hand.MAIN_HAND, hit);
    }

    private String teleportCommand(BlockPos pos) {
        BlockPos standing = safeStandingPosition(pos);
        return formatTeleportCommand(standing);
    }

    static String formatTeleportCommand(BlockPos standing) {
        return String.format(Locale.ROOT, "tp @s %.1f %d %.1f",
                standing.getX() + 0.5, standing.getY(), standing.getZ() + 0.5);
    }

    private BlockPos safeStandingPosition(BlockPos container) {
        for (Direction direction : Direction.Type.HORIZONTAL) {
            BlockPos feet = container.offset(direction);
            if (canStandAt(feet)) return feet;
            BlockPos raised = feet.up();
            if (canStandAt(raised)) return raised;
        }
        BlockPos above = container.up();
        return canStandAt(above) ? above : container.up(2);
    }

    private boolean canStandAt(BlockPos feet) {
        return client.world != null
                && client.world.getBlockState(feet).getCollisionShape(client.world, feet).isEmpty()
                && client.world.getBlockState(feet.up()).getCollisionShape(client.world, feet.up()).isEmpty()
                && !client.world.getBlockState(feet.down()).getCollisionShape(client.world, feet.down()).isEmpty();
    }

    private String returnCommand() {
        return formatReturnCommand(origin, originYaw, originPitch);
    }

    static String formatReturnCommand(Vec3d position, float yaw, float pitch) {
        return String.format(Locale.ROOT, "tp @s %.3f %.3f %.3f",
                position.x, position.y, position.z);
    }

    private boolean safeToDispatch() {
        return OutboundSpamSafety.canDispatch(admin);
    }

    private CommandDispatch dispatch(String command) {
        waitTicks = 0;
        return handle.dispatchCommand(client, command);
    }

    private String selfCommand(String command) {
        return command.replace("{self}", staffIgn());
    }

    private String staffIgn() {
        return client.player == null
                ? client.getSession().getUsername()
                : client.player.getGameProfile().name();
    }

    private void closeHandledScreen() {
        ScreenUtils.closeHandledScreen(client);
    }

    private void transition(State next) {
        state = next;
        waitTicks = 0;
    }

    private int stackPlansBeforeEntry(int exclusiveEntryIndex) {
        int count = 0;
        for (int index = 0; index < Math.min(exclusiveEntryIndex, plan.draft().entries().size()); index++) {
            if (plan.draft().entries().get(index) instanceof ItemEntry item) {
                count += ReimbursementPlan.stackCount(item);
            }
        }
        return count;
    }

    private void publish(String newStatus) {
        status = newStatus;
        int remainingSteps = Math.max(0, totalSteps - completedSteps);
        double observedTicksPerStep = completedSteps == 0
                ? (admin ? 7.0 : 20.0)
                : Math.max(admin ? 5.0 : 20.0, operationTicks / (double) completedSteps);
        int seconds = Math.max(1, (int) Math.ceil(
                remainingSteps * observedTicksPerStep / 20.0
                        + OutboundSpamSafety.ticksUntilSafe(admin) / 20.0));
        listener.progress(completedSteps, totalSteps, seconds, status);
    }

    private List<String> dryRunPreview() {
        List<String> preview = new ArrayList<>();
        preview.add("Theoretical item capacity: " + plan.requiredStacks()
                + " stack slot(s); live inventories are not opened in dry run.");
        preview.add("Safety pacing: " + (admin
                ? "Admin spam-delay bypass; server synchronization waits still apply."
                : "At least 20 ticks between commands, with a modeled 160-point ceiling."));
        ReimbursementEstimate estimate = ReimbursementEstimate.calculate(
                plan, admin, 100, OutboundSpamSafety.ticksUntilSafe(admin));
        preview.add("Estimated duration: " + estimate.formatted() + " (approximate).");

        int stackCursor = 0;
        for (ReimbursementEntry entry : plan.draft().entries()) {
            String route = switch (entry.destination()) {
                case ME -> "staff inventory";
                case PLAYER -> "first four /invsee rows for " + entry.playerIgn().trim()
                        + ", then staff fallback";
                case CONTAINER -> "selected container pool, then staff fallback";
            };
            preview.add("Route: " + route);
            if (entry instanceof MoneyEntry money) {
                String recipient = money.destination() == Destination.ME
                        ? staffIgn() : money.playerIgn().trim();
                preview.add("/eco give " + recipient + " "
                        + money.amount().stripTrailingZeros().toPlainString());
                continue;
            }
            int stacksForEntry = ReimbursementPlan.stackCount((ItemEntry) entry);
            for (int count = 0; count < stacksForEntry; count++) {
                ReimbursementCommandPlanner.StackPlan stack = commands.stacks().get(stackCursor++);
                stack.commands().forEach(command -> preview.add("/" + command));
            }
        }
        return preview;
    }

    private void failAndReturn(String message) {
        failed = true;
        failureMessage = message;
        if (origin != null && client.player != null && client.player.squaredDistanceTo(origin) >= 1.0) {
            transition(State.RETURN_FINAL_COMMAND);
        } else {
            finishTerminal();
        }
    }

    private void finishFailure(String message) {
        failed = true;
        failureMessage = message;
        finishTerminal();
    }

    private void finishTerminal() {
        if (state == State.TERMINAL) return;
        state = State.TERMINAL;
        ReimbursementResult result;
        if (failed) {
            result = liveReimbursementSent ? partialResult()
                    : new ReimbursementResult(ReimbursementResult.Kind.PREFLIGHT_REJECTED,
                    "", completed, retained, remainingEntries(), failureMessage);
        } else {
            ReimbursementResult.Kind kind = retained.isEmpty()
                    ? ReimbursementResult.Kind.SUCCESS
                    : ReimbursementResult.Kind.SUCCESS_WITH_WARNINGS;
            result = new ReimbursementResult(kind,
                    ReimbursementLogFormatter.format(plan, client.world),
                    completed, retained, List.of(), failureMessage);
        }
        listener.finished(result);
        handle.complete();
    }

    private ReimbursementResult partialResult() {
        return new ReimbursementResult(ReimbursementResult.Kind.PARTIAL_FAILURE,
                "", completed, retained, remainingEntries(), failureMessage);
    }

    private List<String> remainingEntries() {
        List<String> values = new ArrayList<>();
        for (int index = entryIndex; index < plan.draft().entries().size(); index++) {
            ReimbursementEntry entry = plan.draft().entries().get(index);
            if (entry instanceof MoneyEntry money) values.add("$" + money.amount().toPlainString());
            else {
                ItemEntry item = (ItemEntry) entry;
                values.add(item.amount() + "x " + net.minecraft.registry.Registries.ITEM.get(item.itemId())
                        .getDefaultStack().getName().getString());
            }
        }
        return values;
    }

    private static String dimension(MinecraftClient client) {
        return client.world == null ? "" : client.world.getRegistryKey().getValue().toString();
    }
}
