package com.duperknight.client.modules;

import com.duperknight.client.gui.modules.WarManagerScreen;
import com.duperknight.client.message.MessageOrigin;
import com.duperknight.client.message.ServerMessage;
import com.duperknight.client.session.CommandDispatch;
import com.duperknight.client.session.ManagedOperation;
import com.duperknight.client.session.OperationCancelReason;
import com.duperknight.client.session.OperationCoordinator;
import com.duperknight.client.session.OperationHandle;
import com.duperknight.client.session.OperationStartResult;
import com.duperknight.client.session.PendingConfirmation;
import com.duperknight.client.utils.ChatUtils;
import com.duperknight.client.utils.InputValidators;
import com.duperknight.client.utils.ScreenUtils;
import com.duperknight.client.utils.ServerGuard;
import com.duperknight.client.utils.TooltipUtils;
import com.duperknight.client.war.CompactDurationFormatter;
import com.duperknight.client.war.NationMenuParser;
import com.duperknight.client.war.WarLeaveResponseParser;
import com.duperknight.client.war.WarManagerState;
import com.duperknight.client.war.WarManagerState.Claim;
import com.duperknight.client.war.WarManagerState.Membership;
import com.duperknight.client.war.WarManagerState.Phase;
import com.duperknight.client.war.WarManagerState.PurgeTransition;
import com.duperknight.client.war.WarManagerState.Side;
import com.duperknight.client.war.WarManagerState.Status;
import com.duperknight.client.war.WarManagerState.War;
import com.duperknight.client.war.WarManagerStore;
import com.duperknight.client.war.WarTimestampParser;
import com.duperknight.client.war.WarTimeline;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.ParentElement;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.dialog.DialogScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Durable, response-tracked scheduler for wars and their shared purge window. */
public final class WarManagerModule extends DMLSModule {
    public static final String OPERATION_ID = "war-manager";
    public static final int MAX_COUNTDOWN_MINUTES = 60;
    public static final long WAR_DURATION_MILLIS = 60L * 60L * 1000L;
    private static final int RESPONSE_TIMEOUT_TICKS = 20 * 30;
    private static final int NATION_SLOT = 12;
    private static final String PREFIX = "Â§8[Â§6DMLS - War ManagerÂ§8] Â§7";

    private final OperationCoordinator coordinator;
    private final WarManagerStore store;
    private WarManagerState state;
    private PendingConfirmation<WarDraft> pending;
    private PendingConfirmation<String> pendingCancel;
    private long lastMaintenanceAttempt;

    public WarManagerModule() {
        this(OperationCoordinator.global(), new WarManagerStore());
    }

    WarManagerModule(OperationCoordinator coordinator, WarManagerStore store) {
        super(DepartmentRank.SENIOR_WAR_STAFF);
        this.coordinator = Objects.requireNonNull(coordinator, "coordinator");
        this.store = Objects.requireNonNull(store, "store");
        this.state = new WarManagerState();
    }

    @Override
    public Text displayName() {
        return Text.translatable("dmls.module.war_manager.name");
    }

    @Override
    public ItemStack icon() {
        return new ItemStack(Items.IRON_SWORD);
    }

    @Override
    public List<Text> description() {
        return List.of(
                Text.translatable("dmls.module.war_manager.description.1"),
                Text.translatable("dmls.module.war_manager.description.2")
        );
    }

    @Override
    public ModuleCategory category() {
        return ModuleCategory.WAR;
    }

    @Override
    public void openScreen(MinecraftClient client, Screen parent) {
        client.setScreen(new WarManagerScreen(parent, this));
    }

    @Override
    public void register() {
        state = store.load();
        boolean reconciled = false;
        for (War war : state.wars) {
            if (war.id.equals("storage-error") || war.status == Status.COMPLETED
                    || war.status == Status.PAUSED) continue;
            if (!war.pendingCommand.isBlank()) {
                war.status = Status.PAUSED;
                war.error = "Dispatch state is uncertain for /" + war.pendingCommand
                        + "; verify server state before retrying.";
                reconciled = true;
            } else if (war.status == Status.SETUP || war.status == Status.RESTORING
                    || (war.status == Status.CANCELLING && war.phase == Phase.CANCEL_WAR)) {
                war.status = Status.PAUSED;
                war.error = "Minecraft closed during a response-tracked "
                        + (isSetupPhase(war.phase) ? "setup"
                        : war.phase == Phase.CANCEL_WAR ? "war ending" : "restoration")
                        + "; retry from the saved checkpoint.";
                reconciled = true;
            }
        }
        if (reconciled) store.save(state);
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }

    public Optional<HomeView> home() {
        WarManagerState.Home home = state.home;
        return home == null ? Optional.empty()
                : Optional.of(new HomeView(home.server, home.x, home.y, home.z));
    }

    public List<WarView> wars() {
        return state.wars.stream()
                .filter(war -> !war.id.equals("storage-error"))
                .sorted(Comparator.comparingLong((War war) -> war.scheduledStartMillis == 0
                        ? Long.MAX_VALUE : war.scheduledStartMillis).reversed())
                .map(war -> new WarView(war.id, war.attacker, war.defender, war.countdownMinutes,
                        war.scheduledStartMillis, war.warStartMillis, war.scheduledEndMillis,
                        war.cancelledAtMillis,
                        war.status, war.error))
                .toList();
    }

    public SaveHomeResult saveHome(MinecraftClient client) {
        if (storageLocked()) return SaveHomeResult.IO_ERROR;
        if (!hasRequiredRank(client)) return SaveHomeResult.RANK_BLOCKED;
        ServerGuard.GuardResult guard = ServerGuard.check(client);
        if (!guard.allowed() || client.player == null) return SaveHomeResult.SERVER_BLOCKED;
        BlockPos pos = client.player.getBlockPos();
        WarManagerState.Home previous = state.home;
        state.home = new WarManagerState.Home(guard.address(), pos.getX(), pos.getY(), pos.getZ());
        if (!store.save(state)) {
            state.home = previous;
            return SaveHomeResult.IO_ERROR;
        }
        return SaveHomeResult.SAVED;
    }

    public StageResult stage(MinecraftClient client, String attacker, String defender,
                             int countdownMinutes, String timestampInput) {
        return stage(client, attacker, defender, countdownMinutes, timestampInput, "");
    }

    public StageResult stage(MinecraftClient client, String attacker, String defender,
                             int countdownMinutes, String timestampInput, String editingWarId) {
        invalidatePending();
        WarDraft draft = prepare(attacker, defender, countdownMinutes, timestampInput,
                System.currentTimeMillis(), editingWarId);
        if (!draft.valid()) {
            StageStatus status = switch (draft.validation()) {
                case INVALID_TIMESTAMP -> StageStatus.INVALID_TIMESTAMP;
                case TIMESTAMP_NOT_FUTURE -> StageStatus.TIMESTAMP_NOT_FUTURE;
                default -> StageStatus.INVALID;
            };
            return new StageResult(status, "", draft);
        }
        if (storageLocked()) return new StageResult(StageStatus.STORAGE_ERROR, "", draft);
        if (!canRunPrivilegedOperation(client)) return new StageResult(StageStatus.BLOCKED, "", draft);
        if (state.home == null) return new StageResult(StageStatus.NO_HOME, "", draft);
        String server = ServerGuard.check(client).address();
        if (!sameServer(state.home.server, server)) return new StageResult(StageStatus.WRONG_SERVER, "", draft);
        if (!draft.editingWarId().isBlank()) {
            Optional<War> editing = findWar(draft.editingWarId());
            if (editing.isEmpty() || !isWaitingForAuthoritativeStart(editing.get())
                    || System.currentTimeMillis() >= editing.get().scheduledStartMillis) {
                return new StageResult(StageStatus.EDIT_UNAVAILABLE, "", draft);
            }
        }
        if (claimReserved(draft.attacker(), draft.editingWarId())
                || claimReserved(draft.defender(), draft.editingWarId())) {
            return new StageResult(StageStatus.CLAIM_RESERVED, "", draft);
        }
        pending = new PendingConfirmation<>(draft);
        return new StageResult(StageStatus.STAGED, pending.token(), draft);
    }

    public boolean confirm(MinecraftClient client, String token) {
        PendingConfirmation<WarDraft> current = pending;
        if (current == null) return false;
        PendingConfirmation.ConsumeResult<WarDraft> consumed = current.consume(token);
        if (consumed.status() != PendingConfirmation.ConsumeStatus.CONFIRMED) return false;
        pending = null;
        WarDraft draft = consumed.request().orElseThrow();
        if (!canRunPrivilegedOperation(client)) return false;
        long setupMillis = draft.immediate()
                ? System.currentTimeMillis() : draft.scheduledStartMillis();
        long warStartMillis = projectedWarStartMillis(setupMillis, draft.countdownMinutes());
        long endMillis = warStartMillis + WAR_DURATION_MILLIS;
        if (com.duperknight.client.utils.DMLSConfig.dryRun()) {
            ChatUtils.sendClientMessage(client, PREFIX + "Dry run: would schedule "
                    + draft.attacker() + " vs " + draft.defender() + " at Unix timestamp "
                    + setupMillis / 1000L + ", passing "
                    + CompactDurationFormatter.formatMinutes(draft.countdownMinutes())
                    + " to the server command.");
            return true;
        }

        String server = ServerGuard.check(client).address();
        if (state.home == null || !sameServer(state.home.server, server)
                || claimReserved(draft.attacker(), draft.editingWarId())
                || claimReserved(draft.defender(), draft.editingWarId())) return false;

        War war;
        boolean editing = !draft.editingWarId().isBlank();
        if (editing) {
            Optional<War> found = findWar(draft.editingWarId());
            if (found.isEmpty() || !isWaitingForAuthoritativeStart(found.get())
                    || System.currentTimeMillis() >= found.get().scheduledStartMillis) return false;
            war = found.get();
        } else {
            war = new War();
            war.id = UUID.randomUUID().toString();
        }
        String previousServer = war.server;
        String previousAttacker = war.attacker;
        String previousDefender = war.defender;
        int previousCountdown = war.countdownMinutes;
        long previousSetup = war.scheduledStartMillis;
        long previousWarStart = war.warStartMillis;
        long previousEnd = war.scheduledEndMillis;
        String previousAttackerClaim = war.attackerClaim.name;
        String previousDefenderClaim = war.defenderClaim.name;
        war.server = server;
        war.attacker = draft.attacker();
        war.defender = draft.defender();
        war.countdownMinutes = draft.countdownMinutes();
        war.scheduledStartMillis = setupMillis;
        war.warStartMillis = warStartMillis;
        war.scheduledEndMillis = endMillis;
        war.attackerClaim.name = draft.attacker();
        war.defenderClaim.name = draft.defender();
        war.status = Status.SCHEDULED;
        if (!editing) state.wars.add(war);
        if (!store.save(state)) {
            if (!editing) {
                state.wars.remove(war);
            } else {
                war.server = previousServer;
                war.attacker = previousAttacker;
                war.defender = previousDefender;
                war.countdownMinutes = previousCountdown;
                war.scheduledStartMillis = previousSetup;
                war.warStartMillis = previousWarStart;
                war.scheduledEndMillis = previousEnd;
                war.attackerClaim.name = previousAttackerClaim;
                war.defenderClaim.name = previousDefenderClaim;
            }
            return false;
        }

        return true;
    }

    public boolean isPending(String token) {
        return pending != null && pending.token().equals(token) && pending.isActive();
    }

    public void invalidatePending(String token) {
        if (pending != null && pending.token().equals(token)) invalidatePending();
    }

    public boolean retry(MinecraftClient client, String warId) {
        Optional<War> found = findWar(warId);
        if (found.isEmpty() || found.get().status != Status.PAUSED || coordinator.isBusy()) return false;
        War war = found.get();
        if (!sameCurrentServer(client, war.server)) return false;
        war.error = "";
        ManagedOperation operation;
        if (war.phase == Phase.CANCEL_WAR) {
            war.status = Status.CANCELLING;
            operation = new CancelOperation(war);
        } else if (isSetupPhase(war.phase)) {
            war.status = Status.SETUP;
            operation = new SetupOperation(war);
        } else {
            war.status = Status.RESTORING;
            war.phase = Phase.RESTORE_ATTACKER_INFO;
            operation = new RestoreOperation(war);
        }
        if (!store.save(state)) return false;
        return coordinator.start(client, OPERATION_ID, displayName().getString(), operation)
                == OperationStartResult.STARTED;
    }

    public boolean cancelScheduled(MinecraftClient client, String warId) {
        Optional<War> found = findWar(warId);
        if (found.isEmpty() || storageLocked() || !canRunPrivilegedOperation(client)) return false;
        War war = found.get();
        if (!sameCurrentServer(client, war.server)
                || !isWaitingForAuthoritativeStart(war)
                || System.currentTimeMillis() >= war.scheduledStartMillis) return false;
        return removeWar(war);
    }

    public boolean dismissCompleted(MinecraftClient client, String warId) {
        Optional<War> found = findWar(warId);
        if (found.isEmpty() || storageLocked() || !canRunPrivilegedOperation(client)) return false;
        War war = found.get();
        if (!sameCurrentServer(client, war.server) || war.status != Status.COMPLETED
                || sharesOriginalNationWithUnfinishedWar(war)) return false;
        return removeWar(war);
    }

    private boolean sharesOriginalNationWithUnfinishedWar(War completed) {
        List<String> nations = java.util.stream.Stream.of(
                        completed.attackerClaim.originalNation, completed.defenderClaim.originalNation)
                .filter(nation -> !nation.isBlank()).toList();
        return state.wars.stream().filter(war -> war != completed && war.unfinished())
                .flatMap(war -> java.util.stream.Stream.of(
                        war.attackerClaim.originalNation, war.defenderClaim.originalNation))
                .anyMatch(candidate -> nations.stream().anyMatch(candidate::equalsIgnoreCase));
    }

    private boolean removeWar(War war) {
        int index = state.wars.indexOf(war);
        if (index < 0) return false;
        state.wars.remove(index);
        if (store.save(state)) return true;
        state.wars.add(index, war);
        return false;
    }

    public CancelStageResult stageCancel(MinecraftClient client, String warId) {
        invalidatePending();
        Optional<War> found = findWar(warId);
        if (found.isEmpty()) return new CancelStageResult(CancelStageStatus.NOT_CANCELLABLE, "", null);
        War war = found.get();
        if (war.status != Status.ACTIVE && war.status != Status.WAITING_FOR_WAR_START) {
            return new CancelStageResult(CancelStageStatus.NOT_CANCELLABLE, "", warView(war));
        }
        if (storageLocked()) return new CancelStageResult(CancelStageStatus.STORAGE_ERROR, "", warView(war));
        if (!canRunPrivilegedOperation(client) || !sameCurrentServer(client, war.server)) {
            return new CancelStageResult(CancelStageStatus.BLOCKED, "", warView(war));
        }
        if (coordinator.isBusy()) return new CancelStageResult(CancelStageStatus.BUSY, "", warView(war));
        pendingCancel = new PendingConfirmation<>(war.id);
        return new CancelStageResult(CancelStageStatus.STAGED, pendingCancel.token(), warView(war));
    }

    public boolean confirmCancel(MinecraftClient client, String token) {
        PendingConfirmation<String> current = pendingCancel;
        if (current == null) return false;
        PendingConfirmation.ConsumeResult<String> consumed = current.consume(token);
        if (consumed.status() != PendingConfirmation.ConsumeStatus.CONFIRMED) return false;
        pendingCancel = null;
        Optional<War> found = findWar(consumed.request().orElseThrow());
        if (found.isEmpty()) return false;
        War war = found.get();
        if ((war.status != Status.ACTIVE && war.status != Status.WAITING_FOR_WAR_START)
                || !canRunPrivilegedOperation(client) || !sameCurrentServer(client, war.server)) return false;
        if (com.duperknight.client.utils.DMLSConfig.dryRun()) {
            ChatUtils.sendClientMessage(client, PREFIX + "Dry run: would send /"
                    + cancelCommand(war.attacker)
                    + ", shorten this war at dispatch time, and reconcile purge/restoration.");
            return true;
        }
        return coordinator.start(client, OPERATION_ID, displayName().getString(),
                new CancelOperation(war)) == OperationStartResult.STARTED;
    }

    public boolean isCancelPending(String token) {
        return pendingCancel != null && pendingCancel.token().equals(token) && pendingCancel.isActive();
    }

    public void invalidateCancelPending(String token) {
        if (pendingCancel != null && pendingCancel.token().equals(token)) {
            pendingCancel.invalidate();
            pendingCancel = null;
        }
    }

    public static WarDraft prepare(String attacker, String defender, int countdownMinutes,
                                   String timestampInput, long nowMillis) {
        return prepare(attacker, defender, countdownMinutes, timestampInput, nowMillis, "");
    }

    public static WarDraft prepare(String attacker, String defender, int countdownMinutes,
                                   String timestampInput, long nowMillis, String editingWarId) {
        String cleanAttacker = Objects.requireNonNullElse(attacker, "").trim();
        String cleanDefender = Objects.requireNonNullElse(defender, "").trim();
        String cleanTimestamp = Objects.requireNonNullElse(timestampInput, "").trim();
        boolean immediate = cleanTimestamp.isEmpty();
        long scheduledStartMillis = immediate
                ? nowMillis : WarTimestampParser.parseEpochMillis(cleanTimestamp).orElse(0L);
        Validation validation;
        if (!InputValidators.isServerIdentifier(cleanAttacker)
                || !InputValidators.isServerIdentifier(cleanDefender)) {
            validation = Validation.INVALID_CLAIM;
        } else if (cleanAttacker.equalsIgnoreCase(cleanDefender)) {
            validation = Validation.SAME_CLAIM;
        } else if (countdownMinutes < 0 || countdownMinutes > MAX_COUNTDOWN_MINUTES) {
            validation = Validation.INVALID_COUNTDOWN;
        } else if (!immediate && scheduledStartMillis == 0L) {
            validation = Validation.INVALID_TIMESTAMP;
        } else if (!immediate && scheduledStartMillis <= nowMillis) {
            validation = Validation.TIMESTAMP_NOT_FUTURE;
        } else {
            validation = Validation.VALID;
        }
        return new WarDraft(validation, cleanAttacker, cleanDefender, countdownMinutes,
                scheduledStartMillis, immediate, Objects.requireNonNullElse(editingWarId, ""));
    }

    public static long warDurationMillis() {
        return WAR_DURATION_MILLIS;
    }

    public static long projectedWarStartMillis(long setupMillis, int countdownMinutes) {
        return setupMillis + countdownMinutes * 60_000L;
    }

    private static boolean isSetupPhase(Phase phase) {
        return phase == Phase.SET_PEACEFUL_ATTACKER
                || phase == Phase.SET_PEACEFUL_DEFENDER
                || phase == Phase.EDIT_ATTACKER
                || phase == Phase.LEAVE_ATTACKER
                || phase == Phase.EDIT_DEFENDER
                || phase == Phase.LEAVE_DEFENDER
                || phase == Phase.CAPITAL_INFO
                || phase == Phase.TRANSFER_CAPITAL
                || phase == Phase.RETRY_LEAVE
                || phase == Phase.DELETE_NATION
                || phase == Phase.START_WAR;
    }

    private void tick(MinecraftClient client) {
        if (pending != null) pending.tick();
        if (pendingCancel != null) pendingCancel.tick();
        long now = System.currentTimeMillis();
        if (storageLocked() || state.home == null
                || com.duperknight.client.utils.DMLSConfig.dryRun()) return;
        if (!advanceWaitingWars(now)) return;
        if (coordinator.isBusy() || now - lastMaintenanceAttempt < 250L) return;
        if (!sameCurrentServer(client, activeServer())) return;
        lastMaintenanceAttempt = now;

        List<WarTimeline.Interval> intervals = liveIntervals();
        boolean purgeShouldBeActive = WarTimeline.activeAt(intervals, now);
        if (state.purgeTransition == PurgeTransition.STARTING) {
            startMaintenance(client, new PurgeOperation(true, activeServer()));
            return;
        }
        if (state.purgeTransition == PurgeTransition.ENDING) {
            startMaintenance(client, new PurgeOperation(false, activeServer()));
            return;
        }
        if (state.purgeApplied && !purgeShouldBeActive) {
            startMaintenance(client, new PurgeOperation(false, activeServer()));
            return;
        }
        if (startDueSetup(client, now)) return;
        if (!state.purgeApplied && purgeShouldBeActive) {
            startMaintenance(client, new PurgeOperation(true, activeServer()));
            return;
        }
        state.wars.stream()
                .filter(war -> war.scheduledEndMillis > 0 && now >= war.scheduledEndMillis
                        && war.status != Status.COMPLETED && war.status != Status.PAUSED
                        && war.status != Status.RESTORING)
                .findFirst()
                .ifPresent(war -> {
                    war.status = Status.RESTORING;
                    war.phase = Phase.RESTORE_ATTACKER_INFO;
                    if (store.save(state)) startMaintenance(client, new RestoreOperation(war));
                });
    }

    private boolean startDueSetup(MinecraftClient client, long now) {
        Optional<War> due = state.wars.stream()
                .filter(war -> isWaitingForAuthoritativeStart(war)
                        && now >= war.scheduledStartMillis)
                .min(Comparator.comparingLong(war -> war.scheduledStartMillis));
        if (due.isEmpty()) return false;
        War war = due.get();
        war.status = Status.SETUP;
        if (!store.save(state)) {
            war.status = Status.PAUSED;
            war.error = "Could not persist before starting scheduled setup.";
            return true;
        }
        OperationStartResult started = coordinator.start(client, OPERATION_ID,
                displayName().getString(), new SetupOperation(war));
        if (started != OperationStartResult.STARTED) {
            war.status = Status.SCHEDULED;
            store.save(state);
        }
        return true;
    }

    static boolean isWaitingForAuthoritativeStart(War war) {
        return war.status == Status.SCHEDULED && war.phase == Phase.SET_PEACEFUL_ATTACKER;
    }

    private boolean advanceWaitingWars(long now) {
        List<War> changed = new ArrayList<>();
        for (War war : state.wars) {
            if (war.status == Status.WAITING_FOR_WAR_START
                    && war.phase == Phase.SETUP_COMPLETE && now >= war.warStartMillis) {
                war.status = Status.ACTIVE;
                changed.add(war);
            }
        }
        if (changed.isEmpty() || store.save(state)) return true;
        changed.forEach(war -> war.status = Status.WAITING_FOR_WAR_START);
        return false;
    }

    private void startMaintenance(MinecraftClient client, ManagedOperation operation) {
        coordinator.start(client, OPERATION_ID, displayName().getString(), operation);
    }

    private List<WarTimeline.Interval> liveIntervals() {
        return state.wars.stream()
                .filter(war -> war.warStartMillis > 0 && war.scheduledEndMillis > war.warStartMillis
                        && war.status != Status.PAUSED && war.phase == Phase.SETUP_COMPLETE)
                .map(war -> new WarTimeline.Interval(war.warStartMillis, war.scheduledEndMillis))
                .toList();
    }

    private String activeServer() {
        if (state.purgeApplied && !state.purgeServer.isBlank()) return state.purgeServer;
        return state.wars.stream().filter(War::unfinished).map(war -> war.server)
                .filter(server -> !server.isBlank()).findFirst()
                .orElse(state.home == null ? "" : state.home.server);
    }

    private Optional<War> findWar(String id) {
        return state.wars.stream().filter(war -> war.id.equals(id)).findFirst();
    }

    private static WarView warView(War war) {
        return new WarView(war.id, war.attacker, war.defender, war.countdownMinutes,
                war.scheduledStartMillis, war.warStartMillis, war.scheduledEndMillis,
                war.cancelledAtMillis,
                war.status, war.error);
    }

    private boolean storageLocked() {
        return state.wars.stream().anyMatch(war -> war.id.equals("storage-error"));
    }

    private boolean claimReserved(String claim) {
        return claimReserved(claim, "");
    }

    private boolean claimReserved(String claim, String exceptWarId) {
        return state.wars.stream().filter(War::unfinished)
                .filter(war -> !war.id.equals(exceptWarId))
                .anyMatch(war -> war.attacker.equalsIgnoreCase(claim) || war.defender.equalsIgnoreCase(claim));
    }

    private boolean sameCurrentServer(MinecraftClient client, String server) {
        return !server.isBlank() && sameServer(server, ServerGuard.check(client).address())
                && ServerGuard.check(client).allowed();
    }

    private static boolean sameServer(String first, String second) {
        return ServerGuard.normalizeAddress(first).equals(ServerGuard.normalizeAddress(second));
    }

    private void invalidatePending() {
        if (pending != null) pending.invalidate();
        pending = null;
        if (pendingCancel != null) pendingCancel.invalidate();
        pendingCancel = null;
    }

    private void pause(War war, OperationHandle handle, MinecraftClient client, String error) {
        war.status = Status.PAUSED;
        war.error = error;
        store.save(state);
        ChatUtils.sendClientMessage(client, PREFIX + "Paused " + war.attacker + " vs "
                + war.defender + ": " + error);
        handle.cancel(client, OperationCancelReason.MODULE_REQUESTED);
    }

    private abstract class DurableOperation implements ManagedOperation {
        protected OperationHandle handle;

        @Override
        public void onStarted(OperationHandle handle, MinecraftClient client) {
            this.handle = handle;
        }

        protected boolean dispatch(MinecraftClient client, String command) {
            if (!handle.canDispatchAutomatedCommand()) return false;
            CommandDispatch result = handle.dispatchCommand(client, command);
            return result != CommandDispatch.BLOCKED;
        }
    }

    private final class SetupOperation extends DurableOperation {
        private final War war;
        private Side side;
        private boolean awaitingLeave;
        private int waitTicks;
        private int previousSyncId = -1;
        private boolean awaitingNationMenu;

        private SetupOperation(War war) {
            this.war = war;
            if (!war.attackerClaim.originalNation.isBlank()
                    && war.attackerClaim.membership == Membership.UNKNOWN) {
                side = Side.ATTACKER;
            } else if (!war.defenderClaim.originalNation.isBlank()
                    && war.defenderClaim.membership == Membership.UNKNOWN) {
                side = Side.DEFENDER;
            }
        }

        @Override
        public void onStarted(OperationHandle handle, MinecraftClient client) {
            super.onStarted(handle, client);
            drive(client);
        }

        @Override
        public void onTick(OperationHandle handle, MinecraftClient client) {
            if (awaitingLeave && ++waitTicks > RESPONSE_TIMEOUT_TICKS) {
                pause(war, handle, client, "Timed out waiting for the Lands leave response.");
                return;
            }
            if (awaitingNationMenu) {
                if (++waitTicks > RESPONSE_TIMEOUT_TICKS) {
                    pause(war, handle, client, "Timed out waiting for /n info.");
                    return;
                }
                Optional<NationSnapshot> snapshot = readNationMenu(client,
                        war.claim(side).originalNation, previousSyncId);
                if (snapshot.isPresent()) {
                    awaitingNationMenu = false;
                    applyNationSnapshot(client, snapshot.get());
                }
                return;
            }
            if (!awaitingLeave) drive(client);
        }

        @Override
        public void onServerMessage(OperationHandle handle, MinecraftClient client, ServerMessage message) {
            if (!awaitingLeave || message.origin() != MessageOrigin.SERVER_SYSTEM) return;
            Claim claim = war.claim(side);
            WarLeaveResponseParser.parse(message.cleanText(), claim.name).ifPresent(result -> {
                awaitingLeave = false;
                waitTicks = 0;
                switch (result.type()) {
                    case LEFT -> {
                        claim.membership = Membership.LEFT;
                        claim.originalNation = result.nation();
                        advanceAfterClaim();
                    }
                    case NO_NATION -> {
                        claim.membership = Membership.NONE;
                        advanceAfterClaim();
                    }
                    case CAPITAL -> {
                        claim.originalNation = result.nation();
                        war.phase = Phase.CAPITAL_INFO;
                        if (store.save(state)) drive(client);
                        else pause(war, handle, client, "Could not persist capital metadata.");
                    }
                }
            });
        }

        @Override
        public void onCancelled(OperationHandle handle, MinecraftClient client, OperationCancelReason reason) {
            if (war.status == Status.PAUSED || war.status == Status.COMPLETED
                    || war.phase == Phase.SETUP_COMPLETE) return;
            war.status = Status.PAUSED;
            war.error = "Setup was interrupted (" + reason.name().toLowerCase(Locale.ROOT)
                    + "); retry from checkpoint " + war.phase + ".";
            store.save(state);
        }

        private void drive(MinecraftClient client) {
            boolean again = true;
            while (again && handle.isActive() && !awaitingLeave && !awaitingNationMenu) {
                again = false;
                switch (war.phase) {
                    case SET_PEACEFUL_ATTACKER -> again = sendAdvance(client,
                            "la admin land " + war.attacker + " setflag peaceful false false",
                            Phase.SET_PEACEFUL_DEFENDER);
                    case SET_PEACEFUL_DEFENDER -> again = sendAdvance(client,
                            "la admin land " + war.defender + " setflag peaceful false false",
                            Phase.EDIT_ATTACKER);
                    case EDIT_ATTACKER -> {
                        side = Side.ATTACKER;
                        again = sendAdvance(client, "la edit " + war.attacker, Phase.LEAVE_ATTACKER);
                    }
                    case LEAVE_ATTACKER -> {
                        side = Side.ATTACKER;
                        sendLeave(client);
                    }
                    case EDIT_DEFENDER -> {
                        side = Side.DEFENDER;
                        again = sendAdvance(client, "la edit " + war.defender, Phase.LEAVE_DEFENDER);
                    }
                    case LEAVE_DEFENDER -> {
                        side = Side.DEFENDER;
                        sendLeave(client);
                    }
                    case CAPITAL_INFO -> startNationInfo(client);
                    case TRANSFER_CAPITAL -> {
                        Claim claim = war.claim(side);
                        again = sendAdvance(client,
                                "n setcapital " + claim.transferredCapital + " confirm", Phase.RETRY_LEAVE);
                    }
                    case RETRY_LEAVE -> sendLeave(client);
                    case DELETE_NATION -> {
                        Claim claim = war.claim(side);
                        claim.membership = Membership.DELETED;
                        again = sendAdvance(client, "n delete confirm",
                                side == Side.ATTACKER ? Phase.EDIT_DEFENDER : Phase.START_WAR);
                    }
                    case START_WAR -> startWar(client);
                    default -> pause(war, handle, client, "Unexpected setup checkpoint " + war.phase + ".");
                }
            }
        }

        private boolean sendAdvance(MinecraftClient client, String command, Phase next) {
            if (!handle.canDispatchAutomatedCommand()) return false;
            if (war.pendingCommand.isBlank()) {
                war.pendingCommand = command;
                war.pendingNextPhase = next;
            } else if (!war.pendingCommand.equals(command) || war.pendingNextPhase != next) {
                pause(war, handle, client, "Saved dispatch checkpoint does not match /" + command + ".");
                return false;
            }
            if (!store.save(state)) {
                pause(war, handle, client, "Could not persist before /" + command + ".");
                return false;
            }
            if (!dispatch(client, command)) {
                pause(war, handle, client, "Command dispatch was blocked: /" + command);
                return false;
            }
            war.phase = next;
            war.pendingCommand = "";
            war.pendingNextPhase = null;
            if (!store.save(state)) {
                pause(war, handle, client, "Command was sent, but its checkpoint could not be saved.");
                return false;
            }
            return com.duperknight.client.utils.DMLSConfig.staffRank() == StaffRank.ADMIN;
        }

        private void sendLeave(MinecraftClient client) {
            if (!handle.canDispatchAutomatedCommand()) return;
            if (!dispatch(client, "n leave confirm")) {
                pause(war, handle, client, "Could not send /n leave confirm.");
                return;
            }
            awaitingLeave = true;
            waitTicks = 0;
        }

        private void advanceAfterClaim() {
            war.phase = side == Side.ATTACKER ? Phase.EDIT_DEFENDER : Phase.START_WAR;
            if (!store.save(state)) {
                pause(war, handle, MinecraftClient.getInstance(), "Could not persist the Lands response.");
                return;
            }
            drive(MinecraftClient.getInstance());
        }

        private void startNationInfo(MinecraftClient client) {
            if (!handle.canDispatchAutomatedCommand()) return;
            previousSyncId = ScreenUtils.currentSyncId(client);
            if (!dispatch(client, "n info " + war.claim(side).originalNation)) {
                pause(war, handle, client, "Could not send /n info.");
                return;
            }
            awaitingNationMenu = true;
            waitTicks = 0;
        }

        private void applyNationSnapshot(MinecraftClient client, NationSnapshot snapshot) {
            Claim claim = war.claim(side);
            NationMenuParser.Result parsed = snapshot.result();
            if (!parsed.parsed()) {
                pause(war, handle, client, "The nation Lands item was malformed.");
                return;
            }
            if (parsed.soleCapital()) {
                claim.coloredNationName = NationMenuParser.coloredNationName(
                        snapshot.title(), claim.originalNation);
                war.phase = Phase.DELETE_NATION;
            } else if (!parsed.alternateCapital().isBlank()) {
                claim.transferredCapital = parsed.alternateCapital();
                war.phase = Phase.TRANSFER_CAPITAL;
            } else {
                pause(war, handle, client, "The nation has other lands, but no alternate capital was visible.");
                return;
            }
            ScreenUtils.closeHandledScreen(client);
            if (!store.save(state)) {
                pause(war, handle, client, "Could not persist nation metadata.");
                return;
            }
            drive(client);
        }

        private void startWar(MinecraftClient client) {
            if (!handle.canDispatchAutomatedCommand()) return;
            String command = "war admin start " + war.attacker + " " + war.defender
                    + " 0 " + CompactDurationFormatter.formatMinutes(war.countdownMinutes);
            if (war.pendingCommand.isBlank()) {
                long dispatchMillis = System.currentTimeMillis();
                war.warStartMillis = projectedWarStartMillis(
                        dispatchMillis, war.countdownMinutes);
                war.scheduledEndMillis = war.warStartMillis + WAR_DURATION_MILLIS;
                war.pendingCommand = command;
                war.pendingNextPhase = Phase.SETUP_COMPLETE;
            }
            if (!store.save(state)) {
                pause(war, handle, client, "Could not persist the war timer before dispatch.");
                return;
            }
            if (!dispatch(client, command)) {
                pause(war, handle, client, "Could not dispatch /" + command + ".");
                return;
            }
            war.pendingCommand = "";
            war.pendingNextPhase = null;
            war.phase = Phase.SETUP_COMPLETE;
            war.status = System.currentTimeMillis() < war.warStartMillis
                    ? Status.WAITING_FOR_WAR_START : Status.ACTIVE;
            store.save(state);
            handle.complete();
        }
    }

    private final class CancelOperation extends DurableOperation {
        private final War war;

        private CancelOperation(War war) {
            this.war = war;
        }

        @Override
        public void onStarted(OperationHandle handle, MinecraftClient client) {
            super.onStarted(handle, client);
            war.status = Status.CANCELLING;
            war.phase = Phase.CANCEL_WAR;
            war.error = "";
            if (!store.save(state)) {
                pause(war, handle, client, "Could not persist the war-end checkpoint.");
                return;
            }
            drive(client);
        }

        @Override
        public void onTick(OperationHandle handle, MinecraftClient client) {
            drive(client);
        }

        @Override
        public void onCancelled(OperationHandle handle, MinecraftClient client, OperationCancelReason reason) {
            if (war.status == Status.PAUSED || war.phase != Phase.CANCEL_WAR) return;
            war.status = Status.PAUSED;
            war.error = "War ending was interrupted (" + reason.name().toLowerCase(Locale.ROOT)
                    + "); verify the server war before retrying.";
            store.save(state);
        }

        private void drive(MinecraftClient client) {
            if (!handle.canDispatchAutomatedCommand()) return;
            String command = cancelCommand(war.attacker);
            if (war.pendingCommand.isBlank()) {
                war.pendingCommand = command;
                war.pendingNextPhase = Phase.RESTORE_ATTACKER_INFO;
            } else if (!war.pendingCommand.equals(command)) {
                pause(war, handle, client, "Saved cancel checkpoint does not match /" + command + ".");
                return;
            }
            if (!store.save(state)) {
                pause(war, handle, client, "Could not persist before /" + command + ".");
                return;
            }
            if (!dispatch(client, command)) {
                pause(war, handle, client, "Could not dispatch /" + command + ".");
                return;
            }

            long now = System.currentTimeMillis();
            war.cancelledAtMillis = now;
            war.scheduledEndMillis = now;
            war.pendingCommand = "";
            war.pendingNextPhase = null;
            war.phase = Phase.RESTORE_ATTACKER_INFO;
            war.status = Status.CANCELLING;
            if (!store.save(state)) {
                pause(war, handle, client,
                        "The end command was sent, but its checkpoint could not be saved.");
                return;
            }
            handle.complete();
        }
    }

    private final class PurgeOperation extends DurableOperation {
        private final boolean starting;
        private final String server;
        private int index;
        private final List<String> commands;

        private PurgeOperation(boolean starting, String server) {
            this.starting = starting;
            this.server = server;
            WarManagerState.Home home = state.home;
            commands = starting
                    ? List.of("gamerule keep_inventory false",
                    "broadcastraw public &a&l &c&lPURGE &a&lis starting, it will stay for &c&l1 hour &a&lyour items will be lost upon death.",
                    "tp " + home.coordinates(),
                    "mm mobs spawn PURGEBAR", "back")
                    : List.of("tp " + home.coordinates(), "gamerule keep_inventory true",
                    "mm mobs kill PURGEBAR");
            PurgeTransition expected = starting ? PurgeTransition.STARTING : PurgeTransition.ENDING;
            boolean resuming = state.purgeTransition == expected;
            if (resuming) {
                index = Math.min(state.purgeCommandIndex, commands.size());
            }
        }

        @Override
        public void onStarted(OperationHandle handle, MinecraftClient client) {
            super.onStarted(handle, client);
            if (state.purgeTransition == PurgeTransition.NONE) {
                state.purgeTransition = starting ? PurgeTransition.STARTING : PurgeTransition.ENDING;
                state.purgeCommandIndex = 0;
                if (starting) {
                    state.purgeApplied = true;
                    state.purgeServer = server;
                }
                if (!store.save(state)) {
                    handle.cancel(client, OperationCancelReason.INTERNAL_ERROR);
                    return;
                }
            }
            drive(client);
        }

        @Override
        public void onTick(OperationHandle handle, MinecraftClient client) {
            drive(client);
        }

        private void drive(MinecraftClient client) {
            while (index < commands.size() && handle.isActive()) {
                if (!handle.canDispatchAutomatedCommand()) return;
                String command = commands.get(index);
                if (!dispatch(client, command)) {
                    handle.cancel(client, OperationCancelReason.DISPATCH_BLOCKED);
                    return;
                }
                index++;
                state.purgeCommandIndex = index;
                if (!store.save(state)) {
                    handle.cancel(client, OperationCancelReason.INTERNAL_ERROR);
                    return;
                }
                if (com.duperknight.client.utils.DMLSConfig.staffRank() != StaffRank.ADMIN) return;
            }
            if (index >= commands.size()) {
                state.purgeApplied = starting;
                state.purgeServer = starting ? server : "";
                state.purgeTransition = PurgeTransition.NONE;
                state.purgeCommandIndex = 0;
                store.save(state);
                handle.complete();
            }
        }
    }

    private final class RestoreOperation extends DurableOperation {
        private final War war;
        private Side side = Side.ATTACKER;
        private int previousSyncId = -1;
        private int waitTicks;
        private boolean awaitingMenu;
        private List<String> commands = List.of();
        private int commandIndex;
        private boolean awaitingDialog;
        private boolean creatingNation;
        private boolean verifyingCreatedNation;
        private String dialogDiagnostics = "no dialog screen detected";

        private RestoreOperation(War war) {
            this.war = war;
        }

        @Override
        public void onStarted(OperationHandle handle, MinecraftClient client) {
            super.onStarted(handle, client);
            resumeSide(client);
        }

        @Override
        public void onTick(OperationHandle handle, MinecraftClient client) {
            if (awaitingMenu) {
                if (++waitTicks > RESPONSE_TIMEOUT_TICKS) {
                    pause(war, handle, client, "Timed out waiting for the nation restoration menu.");
                    return;
                }
                Optional<NationSnapshot> snapshot = readNationMenu(client,
                        war.claim(side).originalNation, previousSyncId);
                if (snapshot.isPresent()) {
                    awaitingMenu = false;
                    NationMenuParser.Result result = snapshot.get().result();
                    if (!result.parsed()) {
                        pause(war, handle, client, "The restoration nation menu was malformed.");
                        return;
                    }
                    ScreenUtils.closeHandledScreen(client);
                    if (verifyingCreatedNation) {
                        verifyingCreatedNation = false;
                        markSideRestored(client);
                    } else {
                        prepareJoinCommands(result.capital());
                        driveCommands(client);
                    }
                }
                return;
            }
            if (awaitingDialog) {
                if (++waitTicks > RESPONSE_TIMEOUT_TICKS) {
                    pause(war, handle, client, "Timed out waiting for Create a New Nation ("
                            + dialogDiagnostics + ").");
                    return;
                }
                DialogFillResult result = fillNationDialog(client, war.claim(side));
                dialogDiagnostics = result.diagnostics();
                if (result.filled()) {
                    awaitingDialog = false;
                    creatingNation = false;
                    verifyingCreatedNation = true;
                }
                return;
            }
            if (verifyingCreatedNation) {
                startRestorationInfo(client);
                return;
            }
            if (creatingNation) {
                driveCreateCommands(client);
                return;
            }
            driveCommands(client);
        }

        @Override
        public void onCancelled(OperationHandle handle, MinecraftClient client, OperationCancelReason reason) {
            if (war.status == Status.PAUSED || war.status == Status.COMPLETED) return;
            war.status = Status.PAUSED;
            war.error = "Restoration was interrupted (" + reason.name().toLowerCase(Locale.ROOT)
                    + "); retry from the saved claim checkpoint.";
            store.save(state);
        }

        private void resumeSide(MinecraftClient client) {
            Claim claim = war.claim(side);
            if (claim.restored || claim.membership == Membership.NONE) {
                claim.restored = true;
                markSideRestored(client);
                return;
            }
            if (claim.membership == Membership.DELETED && !nationAlreadyRecreated(claim.originalNation)) {
                commands = List.of("la edit " + claim.name, "n create");
                commandIndex = 0;
                creatingNation = true;
                driveCreateCommands(client);
                return;
            }
            startRestorationInfo(client);
        }

        private void startRestorationInfo(MinecraftClient client) {
            if (!handle.canDispatchAutomatedCommand()) return;
            previousSyncId = ScreenUtils.currentSyncId(client);
            String command = "n info " + war.claim(side).originalNation;
            if (!dispatch(client, command)) {
                pause(war, handle, client, "Could not send /" + command + ".");
                return;
            }
            awaitingMenu = true;
            waitTicks = 0;
        }

        private void prepareJoinCommands(String capital) {
            Claim claim = war.claim(side);
            commands = List.of("la edit " + capital, "n trust " + claim.name,
                    "la edit " + claim.name, "n accept");
            commandIndex = 0;
        }

        private void driveCommands(MinecraftClient client) {
            while (commandIndex < commands.size() && handle.isActive()) {
                if (!handle.canDispatchAutomatedCommand()) return;
                if (!dispatch(client, commands.get(commandIndex))) {
                    pause(war, handle, client, "Restoration command dispatch was blocked.");
                    return;
                }
                commandIndex++;
                if (com.duperknight.client.utils.DMLSConfig.staffRank() != StaffRank.ADMIN) return;
            }
            if (!commands.isEmpty() && commandIndex >= commands.size()) markSideRestored(client);
        }

        private void driveCreateCommands(MinecraftClient client) {
            while (commandIndex < commands.size() && handle.isActive()) {
                if (!handle.canDispatchAutomatedCommand()) return;
                String command = commands.get(commandIndex++);
                if (!dispatch(client, command)) {
                    pause(war, handle, client, "Nation recreation command dispatch was blocked.");
                    return;
                }
                if (command.equals("n create")) {
                    awaitingDialog = true;
                    waitTicks = 0;
                    return;
                }
                if (com.duperknight.client.utils.DMLSConfig.staffRank() != StaffRank.ADMIN) return;
            }
        }

        private void markSideRestored(MinecraftClient client) {
            war.claim(side).restored = true;
            if (side == Side.ATTACKER) {
                side = Side.DEFENDER;
                commands = List.of();
                commandIndex = 0;
                if (!store.save(state)) {
                    pause(war, handle, client, "Could not persist attacker restoration.");
                    return;
                }
                resumeSide(client);
            } else {
                war.status = Status.COMPLETED;
                war.phase = Phase.COMPLETE;
                war.error = "";
                store.save(state);
                handle.complete();
            }
        }
    }

    private boolean nationAlreadyRecreated(String nation) {
        return state.wars.stream().flatMap(war -> java.util.stream.Stream.of(war.attackerClaim, war.defenderClaim))
                .anyMatch(claim -> claim.restored && claim.originalNation.equalsIgnoreCase(nation));
    }

    private Optional<NationSnapshot> readNationMenu(MinecraftClient client, String nation, int previousSyncId) {
        if (!(client.currentScreen instanceof HandledScreen<?> handled)) return Optional.empty();
        ScreenHandler handler = handled.getScreenHandler();
        if (handler.syncId == previousSyncId || handler.slots.size() <= NATION_SLOT) return Optional.empty();
        String title = client.currentScreen.getTitle().getString();
        String normalizedTitle = title.replace("_", "").replace(" ", "").toLowerCase(Locale.ROOT);
        String normalizedNation = nation.replace("_", "").replace(" ", "").toLowerCase(Locale.ROOT);
        if (!normalizedTitle.contains(normalizedNation)) return Optional.empty();
        Slot slot = handler.getSlot(NATION_SLOT);
        if (!slot.hasStack() || slot.getStack().isEmpty()) return Optional.empty();
        List<TooltipUtils.TooltipLine> tooltip = Screen.getTooltipFromItem(client, slot.getStack())
                .stream().map(TooltipUtils::toTooltipLine).toList();
        return Optional.of(new NationSnapshot(client.currentScreen.getTitle(), NationMenuParser.parse(tooltip)));
    }

    private DialogFillResult fillNationDialog(MinecraftClient client, Claim claim) {
        if (!(client.currentScreen instanceof DialogScreen<?> dialog)) {
            String screen = client.currentScreen == null
                    ? "no screen" : client.currentScreen.getClass().getSimpleName();
            return new DialogFillResult(false, screen);
        }
        String title = dialog.getTitle().getString();
        if (!isCreateNationDialogTitle(title)) {
            return new DialogFillResult(false, "dialog title=\"" + title + "\"");
        }

        List<Element> elements = dialogElements(dialog);
        List<TextFieldWidget> fields = elements.stream()
                .filter(TextFieldWidget.class::isInstance).map(TextFieldWidget.class::cast)
                .sorted(Comparator.comparingInt(TextFieldWidget::getY)
                        .thenComparingInt(TextFieldWidget::getX)).toList();
        List<ButtonWidget> buttons = elements.stream()
                .filter(ButtonWidget.class::isInstance).map(ButtonWidget.class::cast)
                .toList();
        Optional<ButtonWidget> create = buttons.stream()
                .filter(button -> button.getMessage().getString().trim().equalsIgnoreCase("Create"))
                .findFirst();
        String diagnostics = "title=\"" + title + "\", textFields=" + fields.size()
                + ", buttons=" + buttons.stream().map(button -> button.getMessage().getString())
                .filter(label -> !label.isBlank()).toList();
        if (fields.size() < 2 || create.isEmpty()) return new DialogFillResult(false, diagnostics);

        TextFieldWidget nameField = findLabeledField(fields, "name").orElse(fields.get(0));
        TextFieldWidget tagField = findLabeledField(fields, "tag")
                .filter(field -> field != nameField)
                .orElseGet(() -> fields.stream().filter(field -> field != nameField).findFirst().orElseThrow());
        String coloredName = claim.coloredNationName.isBlank() ? claim.originalNation : claim.coloredNationName;
        String tag = tagFor(claim.originalNation);
        nameField.setText(coloredName);
        tagField.setText(tag);
        if (!nameField.getText().equals(coloredName) || !tagField.getText().equals(tag)) {
            return new DialogFillResult(false, diagnostics + ", input rejected");
        }
        create.get().onPress(new KeyInput(GLFW.GLFW_KEY_ENTER, 0, 0));
        return new DialogFillResult(true, diagnostics);
    }

    private static Optional<TextFieldWidget> findLabeledField(List<TextFieldWidget> fields, String label) {
        return fields.stream().filter(field -> field.getMessage().getString()
                .toLowerCase(Locale.ROOT).contains(label)).findFirst();
    }

    private static List<Element> dialogElements(DialogScreen<?> dialog) {
        List<Element> elements = new ArrayList<>();
        Set<Element> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Element child : dialog.children()) collectDialogElement(child, elements, visited);
        return elements;
    }

    private static void collectDialogElement(Element element, List<Element> elements, Set<Element> visited) {
        if (!visited.add(element)) return;
        elements.add(element);
        if (element instanceof ParentElement parent) {
            for (Element child : parent.children()) collectDialogElement(child, elements, visited);
        }
    }

    static boolean isCreateNationDialogTitle(String title) {
        String normalized = Objects.requireNonNullElse(title, "").toLowerCase(Locale.ROOT);
        return normalized.contains("create") && normalized.contains("nation");
    }

    static String tagFor(String nation) {
        String plain = Objects.requireNonNullElse(nation, "").replace("_", "").replace(" ", "");
        int end = plain.offsetByCodePoints(0, Math.min(4, plain.codePointCount(0, plain.length())));
        return plain.substring(0, end);
    }

    static String cancelCommand(String attacker) {
        return "war admin end " + attacker;
    }

    private record NationSnapshot(Text title, NationMenuParser.Result result) {
    }

    private record DialogFillResult(boolean filled, String diagnostics) {
    }

    public enum Validation {
        VALID, INVALID_CLAIM, SAME_CLAIM, INVALID_COUNTDOWN, INVALID_TIMESTAMP, TIMESTAMP_NOT_FUTURE
    }
    public enum StageStatus {
        STAGED, INVALID, INVALID_TIMESTAMP, TIMESTAMP_NOT_FUTURE, BLOCKED, NO_HOME, WRONG_SERVER,
        CLAIM_RESERVED, EDIT_UNAVAILABLE, BUSY, STORAGE_ERROR
    }
    public enum CancelStageStatus { STAGED, NOT_CANCELLABLE, BLOCKED, BUSY, STORAGE_ERROR }
    public enum SaveHomeResult { SAVED, RANK_BLOCKED, SERVER_BLOCKED, IO_ERROR }

    public record WarDraft(Validation validation, String attacker, String defender, int countdownMinutes,
                           long scheduledStartMillis, boolean immediate, String editingWarId) {
        public boolean valid() {
            return validation == Validation.VALID;
        }
    }

    public record StageResult(StageStatus status, String token, WarDraft draft) {
        public boolean staged() {
            return status == StageStatus.STAGED;
        }
    }

    public record CancelStageResult(CancelStageStatus status, String token, WarView war) {
        public boolean staged() {
            return status == CancelStageStatus.STAGED;
        }
    }

    public record HomeView(String server, int x, int y, int z) {
        public String coordinates() {
            return x + " " + y + " " + z;
        }
    }

    public record WarView(String id, String attacker, String defender, int countdownMinutes,
                          long setupMillis, long warStartMillis, long endMillis, long cancelledAtMillis,
                          Status status, String error) {
        public boolean scheduledCancelable(long nowMillis) {
            return status == Status.SCHEDULED && nowMillis < setupMillis;
        }

        public boolean endEarlyAvailable() {
            return status == Status.WAITING_FOR_WAR_START || status == Status.ACTIVE;
        }

        public boolean dismissible() {
            return status == Status.COMPLETED;
        }
    }
}
