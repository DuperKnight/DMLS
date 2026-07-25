package com.duperknight.client.modules;

import com.duperknight.client.gui.modules.ReimbursementScreen;
import com.duperknight.client.reimbursement.ContainerSelectionController;
import com.duperknight.client.reimbursement.ReimbursementDraft;
import com.duperknight.client.reimbursement.ReimbursementOperation;
import com.duperknight.client.reimbursement.ReimbursementPlan;
import com.duperknight.client.reimbursement.ReimbursementResult;
import com.duperknight.client.session.OperationCoordinator;
import com.duperknight.client.session.OperationStartResult;
import com.duperknight.client.utils.ChatUtils;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.block.EnderChestBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;

import java.util.List;

/** Entry point and connection-scoped state owner for Reimbursement Helper. */
public final class ReimbursementModule extends DMLSModule {
    private static final String PREFIX = "§8[§6DMLS - Reimbursement§8] §7";
    private static final String OPERATION_ID = "reimbursement";

    private final ReimbursementDraft draft = new ReimbursementDraft();

    public ReimbursementModule() {
        super(StaffRank.SUPPORT);
    }

    @Override
    public Text displayName() {
        return Text.translatable("dmls.module.reimbursement.name");
    }

    @Override
    public ItemStack icon() {
        return new ItemStack(Items.GOLD_INGOT);
    }

    @Override
    public List<Text> description() {
        return List.of(
                Text.translatable("dmls.module.reimbursement.description.1"),
                Text.translatable("dmls.module.reimbursement.description.2")
        );
    }

    @Override
    public ModuleCategory category() {
        return ModuleCategory.GENERAL;
    }

    @Override
    public void openScreen(MinecraftClient client, Screen parent) {
        client.setScreen(new ReimbursementScreen(parent, this, draft, null));
    }

    @Override
    public void register() {
        ContainerSelectionController.register();
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.world == null) return;
            String dimension = client.world.getRegistryKey().getValue().toString();
            draft.removeContainersIf(target ->
                    dimension.equals(target.dimension())
                            && client.world.isChunkLoaded(target.position())
                            && (!(client.world.getBlockEntity(target.position()) instanceof Inventory)
                            || client.world.getBlockState(target.position()).getBlock()
                            instanceof EnderChestBlock));
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> draft.clear());
    }

    public OperationStartResult start(
            MinecraftClient client,
            ReimbursementPlan plan,
            ReimbursementScreen source
    ) {
        if (!canRunPrivilegedOperation(client)) return OperationStartResult.SERVER_BLOCKED;

        Screen returnParent = source.returnParent();
        ReimbursementOperation operation = new ReimbursementOperation(plan, new ReimbursementOperation.Listener() {
            @Override
            public void progress(int completedSteps, int totalSteps, int estimatedSecondsRemaining, String status) {
                client.inGameHud.setOverlayMessage(Text.literal(
                        status + " · About " + formatTime(estimatedSecondsRemaining) + " remaining"), false);
            }

            @Override
            public void finished(ReimbursementResult result) {
                if (result.kind() == ReimbursementResult.Kind.PARTIAL_FAILURE) {
                    draft.clear();
                }
                client.send(() -> client.setScreen(
                        new ReimbursementScreen(returnParent, ReimbursementModule.this, draft, result)));
            }
        });
        OperationStartResult started = OperationCoordinator.global().start(
                client, OPERATION_ID, "Reimbursement Helper", operation);
        if (started != OperationStartResult.STARTED) {
            ChatUtils.sendClientMessage(client, PREFIX + switch (started) {
                case BUSY -> "Another DMLS operation is already running.";
                case SERVER_BLOCKED -> "Reimbursement is blocked on this server.";
                default -> "The reimbursement could not be started.";
            });
        }
        return started;
    }

    public void clearDraft() {
        draft.clear();
    }

    private static String formatTime(int seconds) {
        int safe = Math.max(0, seconds);
        return "%d:%02d".formatted(safe / 60, safe % 60);
    }
}
