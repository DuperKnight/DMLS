package com.duperknight.client.moderation;

import com.duperknight.client.modules.StaffRank;
import com.duperknight.client.session.CommandDispatch;
import com.duperknight.client.session.ManagedOperation;
import com.duperknight.client.session.OperationCoordinator;
import com.duperknight.client.session.OperationHandle;
import com.duperknight.client.session.OperationStartResult;
import com.duperknight.client.utils.ClientUtils;
import com.duperknight.client.utils.DMLSConfig;
import net.minecraft.client.MinecraftClient;

/** Guarded moderation-view command dispatch. */
public final class ModerationActions {
    private ModerationActions() {
    }

    public static CommandDispatch sendToChannel(MinecraftClient client, ChatChannel channel, String message) {
        String clean = message == null ? "" : message.trim();
        if (channel == null || !channel.selectable() || clean.isEmpty() || clean.indexOf('§') >= 0
                || clean.chars().anyMatch(Character::isISOControl)) {
            return CommandDispatch.BLOCKED;
        }
        return ClientUtils.dispatchCommand(client, channel.sendCommand() + " " + clean);
    }

    public static Outcome punish(MinecraftClient client, PunishmentRequest request) {
        if (request == null) return Outcome.INVALID;
        StaffRank rank = DMLSConfig.staffRank();
        if (!rank.isStaff() || !rank.isAtLeast(request.type().minimumRank())) return Outcome.RANK_BLOCKED;

        OneShotOperation operation = new OneShotOperation(request.command());
        OperationStartResult start = OperationCoordinator.global().start(client,
                "moderation-" + request.type().name().toLowerCase(),
                request.type().displayName().getString(), operation);
        if (start == OperationStartResult.BUSY) return Outcome.BUSY;
        if (start != OperationStartResult.STARTED) return Outcome.BLOCKED;
        return switch (operation.dispatch) {
            case SENT -> Outcome.SENT;
            case SIMULATED -> Outcome.SIMULATED;
            case BLOCKED -> Outcome.BLOCKED;
        };
    }

    public enum Outcome { SENT, SIMULATED, INVALID, RANK_BLOCKED, BLOCKED, BUSY }

    private static final class OneShotOperation implements ManagedOperation {
        private final String command;
        private CommandDispatch dispatch = CommandDispatch.BLOCKED;

        private OneShotOperation(String command) {
            this.command = command;
        }

        @Override
        public void onStarted(OperationHandle handle, MinecraftClient client) {
            dispatch = handle.dispatchCommand(client, command);
            handle.complete();
        }
    }
}
