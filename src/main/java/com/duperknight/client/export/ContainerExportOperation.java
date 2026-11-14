package com.duperknight.client.export;

import com.duperknight.client.message.ServerMessage;
import com.duperknight.client.reimbursement.ContainerTarget;
import com.duperknight.client.session.CommandDispatch;
import com.duperknight.client.session.ManagedOperation;
import com.duperknight.client.session.OperationCancelReason;
import com.duperknight.client.session.OperationHandle;
import com.duperknight.client.utils.ScreenUtils;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChiseledBookShelfBlock;
import net.minecraft.world.level.block.DecoratedPotBlock;
import net.minecraft.world.level.block.EnderChestBlock;
import net.minecraft.world.level.block.JukeboxBlock;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.LongSupplier;

/**
 * Opens each selected container in turn (teleporting next to far ones, like Reimbursement Helper),
 * reads its contents, and writes one JSON file under {@code dmls-exports} in this layout:
 *
 * <pre>
 * { "minecraft:overworld": { "memories": { "x,y,z": {
 *     "items": [ { "id": "...", "count": 1, "components": { ... } } ],
 *     "container": "minecraft:barrel",
 *     "loadedTimestamp": 0, "worldTimestamp": 0, "realTimestamp": "..." } } } }
 * </pre>
 */
public final class ContainerExportOperation implements ManagedOperation {
    private static final int OPEN_TIMEOUT_TICKS = 20 * 5;
    private static final int TELEPORT_TIMEOUT_TICKS = 20 * 5;
    private static final int SYNC_TICKS = 3;
    private static final int RETURN_WAIT_TICKS = 12;
    private static final double INTERACTION_DISTANCE_SQUARED = 16.0;
    private static final DateTimeFormatter FILE_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    // Default Gson escaping on purpose: it matches the reference file (e.g. "=" written as \u003d).
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public interface Listener {
        void progress(int done, int total, String status);

        void finished(ContainerExportResult result);
    }

    private enum State { NEXT, TELEPORT_WAIT, OPEN_WAIT, RETURN_COMMAND, RETURN_WAIT, TERMINAL }

    private final List<ContainerTarget> targets;
    private final LongSupplier loadedTicks;
    private final Listener listener;
    private final JsonObject memories = new JsonObject();
    private final List<String> notes = new ArrayList<>();

    private OperationHandle handle;
    private Minecraft client;
    private State state = State.NEXT;
    private int index;
    private int waitTicks;
    private int screenTicks;
    private int exported;
    private int skipped;
    private int itemCount;
    private boolean failed;
    private String failureMessage = "";
    private double originX;
    private double originY;
    private double originZ;
    private String originDimension = "";

    public ContainerExportOperation(List<ContainerTarget> targets, LongSupplier loadedTicks, Listener listener) {
        this.targets = List.copyOf(targets);
        this.loadedTicks = loadedTicks;
        this.listener = listener;
    }

    @Override
    public void onStarted(OperationHandle handle, Minecraft client) {
        this.handle = handle;
        this.client = client;
        if (client.player == null || client.level == null) {
            failed = true;
            failureMessage = "The player or world is no longer available.";
            finish();
            return;
        }
        originX = client.player.getX();
        originY = client.player.getY();
        originZ = client.player.getZ();
        originDimension = dimension();

        if (handle.descriptor().dryRunCaptured()) {
            state = State.TERMINAL;
            listener.finished(new ContainerExportResult(ContainerExportResult.Kind.DRY_RUN, "", 0, 0, 0, List.of(),
                    "Dry run only: no containers were opened and no file was written. "
                            + targets.size() + " container(s) would be exported."));
            handle.complete();
            return;
        }
        if (targets.isEmpty()) {
            failed = true;
            failureMessage = "No containers were selected.";
            finish();
            return;
        }
        client.gui.setScreen(null);
        publish("Starting export");
    }

    @Override
    public void onTick(OperationHandle handle, Minecraft client) {
        if (state == State.TERMINAL) return;
        this.client = client;
        waitTicks++;
        if (client.player == null || client.level == null) {
            failed = true;
            failureMessage = "The player or world is no longer available.";
            finish();
            return;
        }
        switch (state) {
            case NEXT -> next();
            case TELEPORT_WAIT -> teleportWait();
            case OPEN_WAIT -> openWait();
            case RETURN_COMMAND -> returnCommand();
            case RETURN_WAIT -> {
                if (waitTicks >= RETURN_WAIT_TICKS) finish();
            }
            default -> {
            }
        }
    }

    @Override
    public void onServerMessage(OperationHandle handle, Minecraft client, ServerMessage message) {
        if (state == State.TERMINAL || failed || !isServerRejection(message.cleanText())) return;
        failAndReturn("The server rejected a step: " + message.cleanText());
    }

    @Override
    public void onCancelled(OperationHandle handle, Minecraft client, OperationCancelReason reason) {
        if (state == State.TERMINAL) return;
        state = State.TERMINAL;
        listener.finished(new ContainerExportResult(ContainerExportResult.Kind.CANCELLED, "", exported, skipped,
                itemCount, notes, "Export cancelled: " + reason.name().toLowerCase(Locale.ROOT).replace('_', ' ')
                + ". Nothing was written."));
    }

    static boolean isServerRejection(String message) {
        String clean = message == null ? "" : message.toLowerCase(Locale.ROOT);
        return clean.contains("unknown command") || clean.contains("incorrect argument")
                || clean.contains("no permission") || clean.contains("not permitted");
    }

    // ---- state handlers -------------------------------------------------------------------

    private void next() {
        if (index >= targets.size()) {
            beginReturnOrFinish();
            return;
        }
        ContainerTarget target = targets.get(index);
        BlockPos pos = target.position();
        if (!originDimension.equals(target.dimension())) {
            skip(target, "It is in a different dimension.");
            return;
        }
        if (!client.level.hasChunkAt(pos)) {
            if (waitTicks >= OPEN_TIMEOUT_TICKS) skip(target, "Its chunk is not loaded.");
            return;
        }
        if (!isContainer(pos)) {
            skip(target, "It is no longer a container.");
            return;
        }
        String unsafe = unsafeReason(client.level.getBlockState(pos).getBlock());
        if (unsafe != null) {
            skip(target, unsafe);
            return;
        }
        if (distanceSquared(pos) > INTERACTION_DISTANCE_SQUARED) {
            if (!handle.canDispatchAutomatedCommand()) return;
            if (handle.dispatchCommand(client, teleportCommand(pos)) == CommandDispatch.BLOCKED) {
                failAndReturn("Could not teleport next to a selected container.");
                return;
            }
            transition(State.TELEPORT_WAIT);
            return;
        }
        interact(target);
    }

    private void teleportWait() {
        ContainerTarget target = targets.get(index);
        BlockPos pos = target.position();
        if (client.level.hasChunkAt(pos) && distanceSquared(pos) <= INTERACTION_DISTANCE_SQUARED) {
            if (!isContainer(pos)) {
                skip(target, "It is no longer a container.");
                return;
            }
            interact(target);
            return;
        }
        if (waitTicks >= TELEPORT_TIMEOUT_TICKS) skip(target, "Teleporting next to it timed out.");
    }

    private void openWait() {
        ContainerTarget target = targets.get(index);
        BlockPos pos = target.position();
        if (!isContainer(pos)) {
            ScreenUtils.closeHandledScreen(client);
            skip(target, "It is no longer a container.");
            return;
        }
        if (client.gui.screen() instanceof AbstractContainerScreen<?> screen) {
            screenTicks++;
            if (screenTicks < SYNC_TICKS) return; // let the server's contents packet arrive
            readContainer(target, screen.getMenu());
            ScreenUtils.closeHandledScreen(client);
            index++;
            transition(State.NEXT);
            publish("Exported " + exported + " of " + targets.size());
            return;
        }
        if (waitTicks >= OPEN_TIMEOUT_TICKS) {
            skip(target, "It did not open (protected, or the server refused).");
        }
    }

    private void returnCommand() {
        if (!handle.canDispatchAutomatedCommand()) return;
        String command = String.format(Locale.ROOT, "tp @s %.3f %.3f %.3f", originX, originY, originZ);
        if (handle.dispatchCommand(client, command) == CommandDispatch.BLOCKED) {
            notes.add("Could not teleport back to the starting position.");
            finish();
            return;
        }
        transition(State.RETURN_WAIT);
    }

    // ---- reading and output ---------------------------------------------------------------

    private void readContainer(ContainerTarget target, AbstractContainerMenu menu) {
        BlockPos pos = target.position();
        int containerSlots = Math.max(0, menu.slots.size() - 36);

        JsonArray items = new JsonArray();
        int containerItems = 0;
        for (int slotIndex = 0; slotIndex < containerSlots; slotIndex++) {
            ItemStack stack = menu.getSlot(slotIndex).getItem();
            if (stack.isEmpty()) continue;
            items.add(stackJson(stack));
            containerItems += stack.getCount();
        }

        JsonObject entry = new JsonObject();
        entry.add("items", items);
        entry.addProperty("container",
                BuiltInRegistries.BLOCK.getKey(client.level.getBlockState(pos).getBlock()).toString());
        entry.addProperty("loadedTimestamp", loadedTicks.getAsLong());
        entry.addProperty("worldTimestamp", client.level.getGameTime());
        entry.addProperty("realTimestamp", Instant.now().toString());
        memories.add(pos.getX() + "," + pos.getY() + "," + pos.getZ(), entry);

        exported++;
        itemCount += containerItems;
    }

    /**
     * One stack as {@code {"id": ..., "count": ..., "components": {...}}} using the game's own item
     * codec; {@code components} only appears when the stack has any. Falls back to id and count.
     */
    private JsonElement stackJson(ItemStack stack) {
        try {
            var ops = client.level.registryAccess().createSerializationContext(JsonOps.INSTANCE);
            JsonElement encoded = ItemStack.CODEC.encodeStart(ops, stack).result().orElse(null);
            if (encoded != null) return encoded;
        } catch (RuntimeException exception) {
            // fall through to the plain form
        }
        JsonObject plain = new JsonObject();
        plain.addProperty("id", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        plain.addProperty("count", stack.getCount());
        return plain;
    }

    private ContainerExportResult buildResult() {
        String skippedText = skipped > 0 ? ", " + skipped + " skipped" : "";
        if (exported == 0) {
            String message = failed ? failureMessage : "Nothing was exported" + skippedText + ".";
            return new ContainerExportResult(ContainerExportResult.Kind.FAILED, "", 0, skipped, 0, notes, message);
        }
        try {
            JsonObject dimension = new JsonObject();
            dimension.add("memories", memories);
            JsonObject root = new JsonObject();
            root.add(originDimension, dimension);

            Path gameDir = FabricLoader.getInstance().getGameDir();
            Path directory = gameDir.resolve("dmls-exports");
            Path file = directory.resolve("containers-" + FILE_STAMP.format(LocalDateTime.now()) + ".json");
            Files.createDirectories(directory);
            Files.writeString(file, GSON.toJson(root), StandardCharsets.UTF_8);

            String shown;
            try {
                shown = gameDir.relativize(file).toString();
            } catch (IllegalArgumentException exception) {
                shown = file.toString();
            }
            ContainerExportResult.Kind kind = failed ? ContainerExportResult.Kind.PARTIAL
                    : ContainerExportResult.Kind.SUCCESS;
            String message = "Exported " + exported + " container(s) with " + itemCount + " item(s)"
                    + skippedText + " to " + shown + "."
                    + (failed ? " Stopped early: " + failureMessage : "");
            return new ContainerExportResult(kind, shown, exported, skipped, itemCount, notes, message);
        } catch (IOException exception) {
            return new ContainerExportResult(ContainerExportResult.Kind.FAILED, "", exported, skipped, itemCount,
                    notes, "Could not write the export file: " + exception.getMessage());
        }
    }

    // ---- helpers --------------------------------------------------------------------------

    private void interact(ContainerTarget target) {
        ScreenUtils.closeHandledScreen(client);
        BlockPos pos = target.position();
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        client.gameMode.useItemOn(client.player, InteractionHand.MAIN_HAND, hit);
        screenTicks = 0;
        transition(State.OPEN_WAIT);
    }

    private void skip(ContainerTarget target, String reason) {
        skipped++;
        notes.add(target.displayCoordinates() + ": " + reason);
        index++;
        transition(State.NEXT);
        publish("Skipped a container (" + reason + ")");
    }

    private boolean isContainer(BlockPos pos) {
        return client.level.hasChunkAt(pos)
                && client.level.getBlockEntity(pos) instanceof Container
                && !(client.level.getBlockState(pos).getBlock() instanceof EnderChestBlock);
    }

    /** Blocks that are containers but do something else (eject, hand out items) when used. */
    private static String unsafeReason(Block block) {
        if (block instanceof JukeboxBlock) return "Jukeboxes eject their disc when used.";
        if (block instanceof ChiseledBookShelfBlock) return "Chiseled bookshelves hand out books when used.";
        if (block instanceof DecoratedPotBlock) return "Decorated pots do not open a menu.";
        if (block instanceof LecternBlock) return "Lecterns do not open a normal container menu.";
        return null;
    }

    private double distanceSquared(BlockPos pos) {
        double dx = client.player.getX() - (pos.getX() + 0.5);
        double dy = client.player.getY() - (pos.getY() + 0.5);
        double dz = client.player.getZ() - (pos.getZ() + 0.5);
        return dx * dx + dy * dy + dz * dz;
    }

    private String teleportCommand(BlockPos container) {
        BlockPos standing = safeStandingPosition(container);
        return String.format(Locale.ROOT, "tp @s %.1f %d %.1f",
                standing.getX() + 0.5, standing.getY(), standing.getZ() + 0.5);
    }

    private BlockPos safeStandingPosition(BlockPos container) {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos feet = container.relative(direction);
            if (canStandAt(feet)) return feet;
            BlockPos raised = feet.above();
            if (canStandAt(raised)) return raised;
        }
        BlockPos above = container.above();
        return canStandAt(above) ? above : container.above(2);
    }

    private boolean canStandAt(BlockPos feet) {
        return client.level.getBlockState(feet).getCollisionShape(client.level, feet).isEmpty()
                && client.level.getBlockState(feet.above()).getCollisionShape(client.level, feet.above()).isEmpty()
                && !client.level.getBlockState(feet.below()).getCollisionShape(client.level, feet.below()).isEmpty();
    }

    private void beginReturnOrFinish() {
        double dx = client.player.getX() - originX;
        double dy = client.player.getY() - originY;
        double dz = client.player.getZ() - originZ;
        if (dx * dx + dy * dy + dz * dz >= 1.0) transition(State.RETURN_COMMAND);
        else finish();
    }

    private void failAndReturn(String message) {
        failed = true;
        failureMessage = message;
        beginReturnOrFinish();
    }

    private void finish() {
        if (state == State.TERMINAL) return;
        state = State.TERMINAL;
        ContainerExportResult result = buildResult();
        listener.finished(result);
        if (handle != null) handle.complete();
    }

    private void transition(State next) {
        state = next;
        waitTicks = 0;
    }

    private void publish(String status) {
        listener.progress(Math.min(index, targets.size()), targets.size(), status);
    }

    private String dimension() {
        return client.level == null ? "" : client.level.dimension().identifier().toString();
    }
}
