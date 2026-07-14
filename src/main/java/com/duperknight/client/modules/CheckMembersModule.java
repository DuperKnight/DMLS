package com.duperknight.client.modules;

import com.duperknight.client.gui.modules.CheckMembersScreen;
import com.duperknight.client.parser.MembersMenuParser;
import com.duperknight.client.parser.MembersMenuParser.Group;
import com.duperknight.client.parser.MembersMenuParser.Scan;
import com.duperknight.client.session.CommandDispatch;
import com.duperknight.client.session.ManagedOperation;
import com.duperknight.client.session.OperationCancelReason;
import com.duperknight.client.session.OperationCoordinator;
import com.duperknight.client.session.OperationHandle;
import com.duperknight.client.session.OperationStartResult;
import com.duperknight.client.utils.ChatUtils;
import com.duperknight.client.utils.InputValidators;
import com.duperknight.client.utils.MenuCommandQuery;
import com.duperknight.client.utils.ScreenUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

import java.util.List;
import java.util.Objects;

public final class CheckMembersModule extends DMLSModule {
    public static final String OPERATION_ID = "check-members";
    private static final String PREFIX = "§8[§6DMLS - CheckMembers§8] §7";
    private static final int PLAYER_LIST_SLOT = ScreenUtils.slotIndex(4, 2);
    private static final int MENU_TIMEOUT_TICKS = 20 * 30;
    private static final int MAX_LAND_NAME_LENGTH = 64;

    private final OperationCoordinator coordinator;
    private CheckSession activeSession;

    public CheckMembersModule() {
        this(OperationCoordinator.global());
    }

    CheckMembersModule(OperationCoordinator coordinator) {
        super(StaffRank.MODERATOR);
        this.coordinator = Objects.requireNonNull(coordinator, "coordinator");
    }

    @Override
    public Text displayName() {
        return Text.translatable("dmls.module.check_members.name");
    }

    @Override
    public ItemStack icon() {
        return new ItemStack(Items.PLAYER_HEAD);
    }

    @Override
    public List<Text> description() {
        return List.of(
                Text.translatable("dmls.module.check_members.description.1"),
                Text.translatable("dmls.module.check_members.description.2")
        );
    }

    @Override
    public void openScreen(MinecraftClient client, Screen parent) {
        client.setScreen(new CheckMembersScreen(parent, this));
    }

    @Override
    public void register() {
        // Canonical command is registered under /dmls by DMLSClient.
    }

    /** Starts a mutually exclusive member-menu query. */
    public OperationStartResult submit(MinecraftClient client, String landInput) {
        if (!canRunPrivilegedOperation(client)) return OperationStartResult.SERVER_BLOCKED;
        String land = Objects.requireNonNullElse(landInput, "").trim();
        if (!InputValidators.isSafeCommandArgument(land, MAX_LAND_NAME_LENGTH)) {
            ChatUtils.sendTranslatedMessage(client, PREFIX, land.isEmpty()
                    ? "dmls.chat.check_members.no_land" : "dmls.chat.check_members.invalid_land");
            return OperationStartResult.INVALID;
        }

        replaceOwnOperation(client);
        CheckSession candidate = new CheckSession(land);
        activeSession = candidate;
        OperationStartResult result = coordinator.start(client, OPERATION_ID,
                "Check Members", candidate);
        if (result == OperationStartResult.STARTED && !candidate.acceptedAtStart()) {
            result = OperationStartResult.SERVER_BLOCKED;
        }
        if (result != OperationStartResult.STARTED && activeSession == candidate) activeSession = null;
        reportStartFailure(client, result);
        return result;
    }

    private void replaceOwnOperation(MinecraftClient client) {
        if (coordinator.activeDescriptor().filter(descriptor -> descriptor.operationId().equals(OPERATION_ID)).isPresent()) {
            coordinator.cancel(OPERATION_ID, client);
        }
    }

    private void reportStartFailure(MinecraftClient client, OperationStartResult result) {
        if (result == OperationStartResult.BUSY) {
            String owner = coordinator.activeDescriptor().map(descriptor -> descriptor.displayName()).orElse("Another operation");
            ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.operation.busy", owner);
        } else if (result != OperationStartResult.STARTED && result != OperationStartResult.SERVER_BLOCKED
                && result != OperationStartResult.INVALID) {
            ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.operation.start_failed");
        }
    }

    private final class CheckSession implements ManagedOperation {
        private final String land;
        private MenuCommandQuery activeQuery;
        private OperationHandle handle;
        private CommandDispatch initialDispatch = CommandDispatch.BLOCKED;

        private CheckSession(String land) {
            this.land = land;
        }

        @Override
        public void onStarted(OperationHandle handle, MinecraftClient client) {
            this.handle = handle;
            ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.check_members.checking", land);
            activeQuery = new MenuCommandQuery("la info " + land, land, MENU_TIMEOUT_TICKS, PLAYER_LIST_SLOT);
            initialDispatch = activeQuery.start(client, handle::dispatchCommand);
            if (initialDispatch == CommandDispatch.BLOCKED) {
                handle.cancel(client, OperationCancelReason.DISPATCH_BLOCKED);
            }
        }

        private boolean acceptedAtStart() {
            return initialDispatch != CommandDispatch.BLOCKED;
        }

        @Override
        public void onTick(OperationHandle handle, MinecraftClient client) {
            MenuCommandQuery.TickResult tickResult = activeQuery.tick(client);
            switch (tickResult.status()) {
                case WAITING -> { return; }
                case TIMED_OUT -> {
                    ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.menu_query.timeout",
                            "/la info " + land);
                    finish(client);
                    return;
                }
                case CANCELLED -> {
                    handle.cancel(client, OperationCancelReason.DISPATCH_BLOCKED);
                    return;
                }
                case SIMULATED -> {
                    ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.menu_query.simulated", "/la info " + land);
                    finish(client);
                    return;
                }
                case READY -> {
                    // Continue below.
                }
            }

            Scan scan = tickResult.result().flatMap(result -> result.tooltip(PLAYER_LIST_SLOT))
                    .map(MembersMenuParser::parse).orElseGet(() -> MembersMenuParser.parse(List.of()));
            if (scan.status() == MembersMenuParser.ParseStatus.MALFORMED) {
                ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.menu_query.malformed", "/la info " + land);
                finish(client);
                return;
            }
            report(client, scan);
            finish(client);
        }

        @Override
        public void onCancelled(OperationHandle handle, MinecraftClient client, OperationCancelReason reason) {
            ScreenUtils.closeHandledScreen(client);
            if (activeSession == this) activeSession = null;
            if (reason != OperationCancelReason.MODULE_REQUESTED) {
                ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.session.cancelled");
            }
        }

        private void report(MinecraftClient client, Scan scan) {
            String header = PREFIX + "Land §6" + land + "§7 ";
            ChatUtils.sendClientMessage(client, header + ChatUtils.separatorForChatWidth(client, header));
            for (Group group : scan.groups()) {
                MutableText line = Text.literal(group.formattedRank() + "§r§7 (" + group.players().size() + "): ");
                for (int i = 0; i < group.players().size(); i++) {
                    if (i > 0) line.append("§7, ");
                    line.append(clickablePlayer(group.players().get(i)));
                }
                ChatUtils.sendClientMessage(client, line);
            }
            if (scan.truncated()) ChatUtils.sendTranslatedMessage(client, "", "dmls.chat.check_members.truncated");
            ChatUtils.sendClientMessage(client, "§7" + ChatUtils.separatorForChatWidth(client, ""));
        }

        private MutableText clickablePlayer(String playerName) {
            return Text.literal("§6" + playerName).styled(style -> style
                    .withClickEvent(new ClickEvent.RunCommand("/dmls lands " + playerName))
                    .withHoverEvent(new HoverEvent.ShowText(
                            Text.translatable("dmls.chat.check_members.hover_lands", playerName))));
        }

        private void finish(MinecraftClient client) {
            ScreenUtils.closeHandledScreen(client);
            if (activeSession == this) activeSession = null;
            if (handle != null) handle.complete();
        }
    }
}
