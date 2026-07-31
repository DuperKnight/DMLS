package com.duperknight.client.modules;

import com.duperknight.client.gui.modules.EventRandomTeleportScreen;
import com.duperknight.client.message.MessageOrigin;
import com.duperknight.client.message.ServerMessageRouter;
import com.duperknight.client.session.CommandDispatch;
import com.duperknight.client.session.ManagedOperation;
import com.duperknight.client.session.OperationCancelReason;
import com.duperknight.client.session.OperationCoordinator;
import com.duperknight.client.session.OperationHandle;
import com.duperknight.client.session.OperationStartResult;
import com.duperknight.client.utils.ChatUtils;
import com.duperknight.client.utils.ClientUtils;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class EventRandomTeleportModule extends DMLSModule {
    private static final String PREFIX = "§8[§6DMLS - RandomTP§8] §7";
    private static final Random RANDOM = new Random();
    private final VanishTracker vanishTracker = new VanishTracker();

    public enum TeleportStatus {
        SENT,
        QUEUED,
        SIMULATED,
        NO_PLAYERS,
        BLOCKED
    }

    public record TeleportResult(TeleportStatus status, String target, boolean vanishRequested) {
        public boolean accepted() {
            return status == TeleportStatus.SENT || status == TeleportStatus.QUEUED
                    || status == TeleportStatus.SIMULATED;
        }
    }

    public EventRandomTeleportModule() {
        super(StaffDepartment.EVENTS);
    }

    @Override
    public Text displayName() {
        return Text.translatable("dmls.module.random_teleport.name");
    }

    @Override
    public ItemStack icon() {
        return new ItemStack(Items.ENDER_PEARL);
    }

    @Override
    public List<Text> description() {
        return List.of(Text.translatable("dmls.module.random_teleport.description"));
    }

    @Override
    public ModuleCategory category() {
        return ModuleCategory.EVENTS;
    }

    @Override
    public void openScreen(MinecraftClient client, Screen parent) {
        client.setScreen(new EventRandomTeleportScreen(parent, this));
    }

    @Override
    public void register() {
        OperationCoordinator.global().register();
        ServerMessageRouter.subscribe(EnumSet.of(MessageOrigin.SERVER_SYSTEM, MessageOrigin.OVERLAY),
                message -> vanishTracker.accept(message.cleanText()));
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> vanishTracker.reset());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> vanishTracker.reset());
    }

    /** Compatibility helper for callers that only need the accepted target. */
    public String teleportToRandomPlayer(MinecraftClient client) {
        TeleportResult result = teleport(client);
        return result.accepted() ? result.target() : null;
    }

    /** Enables vanish only when this session has observed it off, then queues one safe teleport. */
    public TeleportResult teleport(MinecraftClient client) {
        if (!canRunPrivilegedOperation(client)) {
            return new TeleportResult(TeleportStatus.BLOCKED, "", false);
        }
        if (ClientUtils.isNotConnected(client)) {
            ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.command.not_sent");
            return new TeleportResult(TeleportStatus.BLOCKED, "", false);
        }

        List<String> players = new ArrayList<>(ClientUtils.getOnlinePlayerNames(client));
        String selfName = client.getSession().getUsername();
        players.removeIf(player -> player.equalsIgnoreCase(selfName));

        if (players.isEmpty()) {
            return new TeleportResult(TeleportStatus.NO_PLAYERS, "", false);
        }

        String target = players.get(RANDOM.nextInt(players.size()));
        boolean needsVanish = vanishTracker.needsEnable();
        TeleportOperation operation = new TeleportOperation(target, needsVanish);
        OperationStartResult started = OperationCoordinator.global().start(
                client, "event-random-teleport", displayName().getString(), operation);
        if (started != OperationStartResult.STARTED) {
            ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.command.not_sent");
            return new TeleportResult(TeleportStatus.BLOCKED, target, needsVanish);
        }
        return operation.result;
    }

    private final class TeleportOperation implements ManagedOperation {
        private enum Step { VANISH, TELEPORT, COMPLETE }

        private final String target;
        private final boolean needsVanish;
        private Step step;
        private TeleportResult result;

        private TeleportOperation(String target, boolean needsVanish) {
            this.target = target;
            this.needsVanish = needsVanish;
            this.step = needsVanish ? Step.VANISH : Step.TELEPORT;
            this.result = new TeleportResult(TeleportStatus.QUEUED, target, needsVanish);
        }

        @Override
        public void onStarted(OperationHandle handle, MinecraftClient client) {
            tryDispatch(handle, client);
        }

        @Override
        public void onTick(OperationHandle handle, MinecraftClient client) {
            tryDispatch(handle, client);
        }

        @Override
        public void onCancelled(OperationHandle handle, MinecraftClient client, OperationCancelReason reason) {
            if (step != Step.COMPLETE) {
                result = new TeleportResult(TeleportStatus.BLOCKED, target, needsVanish);
                step = Step.COMPLETE;
            }
        }

        private void tryDispatch(OperationHandle handle, MinecraftClient client) {
            if (step == Step.COMPLETE || !handle.canDispatchAutomatedCommand()) return;
            if (step == Step.VANISH) {
                CommandDispatch dispatch = handle.dispatchCommand(client, "vanish");
                if (dispatch == CommandDispatch.BLOCKED) {
                    fail(handle, client);
                    return;
                }
                if (dispatch == CommandDispatch.SENT) vanishTracker.enableDispatched();
                step = Step.TELEPORT;
                if (dispatch == CommandDispatch.SIMULATED) tryDispatch(handle, client);
                return;
            }

            CommandDispatch dispatch = handle.dispatchCommand(client, "tp " + target);
            if (dispatch == CommandDispatch.BLOCKED) {
                fail(handle, client);
                return;
            }
            TeleportStatus status = dispatch == CommandDispatch.SIMULATED
                    ? TeleportStatus.SIMULATED : TeleportStatus.SENT;
            result = new TeleportResult(status, target, needsVanish);
            step = Step.COMPLETE;
            handle.complete();
            if (status == TeleportStatus.SIMULATED) {
                ChatUtils.sendTranslatedMessage(client, PREFIX,
                        needsVanish
                                ? "dmls.chat.random_teleport.simulated_with_vanish"
                                : "dmls.chat.random_teleport.simulated",
                        target);
            }
        }

        private void fail(OperationHandle handle, MinecraftClient client) {
            result = new TeleportResult(TeleportStatus.BLOCKED, target, needsVanish);
            step = Step.COMPLETE;
            ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.command.not_sent");
            handle.cancel(client, OperationCancelReason.DISPATCH_BLOCKED);
        }
    }

    static final class VanishTracker {
        private static final Pattern STATUS = Pattern.compile(
                "^vanish\\s*:\\s*(enabled|disabled)[.!]?$", Pattern.CASE_INSENSITIVE);
        private State state = State.OFF;

        enum State { OFF, ENABLING, ON }

        boolean needsEnable() {
            return state == State.OFF;
        }

        State state() {
            return state;
        }

        void enableDispatched() {
            if (state == State.OFF) state = State.ENABLING;
        }

        void accept(String message) {
            Matcher matcher = STATUS.matcher(message == null ? "" : message.trim());
            if (!matcher.matches()) return;
            state = matcher.group(1).equalsIgnoreCase("enabled") ? State.ON : State.OFF;
        }

        void reset() {
            state = State.OFF;
        }
    }
}
