package com.duperknight.client.modules;

import com.duperknight.client.gui.modules.ActivityWaveScreen;
import com.duperknight.client.session.OperationCancelReason;
import com.duperknight.client.session.OperationCancelResult;
import com.duperknight.client.session.OperationCoordinator;
import com.duperknight.client.session.OperationStartResult;
import com.duperknight.client.utils.ChatUtils;
import com.duperknight.client.utils.InputValidators;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Checks the recent activity of a whole staff wave with the server's /activity command. */
public final class ActivityWaveModule extends DMLSModule {
    public static final String OPERATION_ID = "activity-wave";
    public static final int MAX_PLAYERS = 60;
    private static final String PREFIX = "§8[§6DMLS - Activity§8] §7";

    public ActivityWaveModule() {
        super(StaffRank.ADMIN);
    }

    public enum PreparationStatus { VALID, EMPTY, TOO_MANY }

    public enum SubmitStatus { STARTED, INVALID, TOO_MANY, BLOCKED, BUSY, FAILED }

    public record ActivityRequest(PreparationStatus status, List<String> usernames, List<String> skipped) {
        public ActivityRequest {
            usernames = List.copyOf(usernames);
            skipped = List.copyOf(skipped);
        }

        public boolean valid() {
            return status == PreparationStatus.VALID;
        }
    }

    public static ActivityRequest prepare(String input) {
        List<String> skipped = new ArrayList<>();
        List<String> usernames = InputValidators.uniqueUsernames(input, skipped);
        if (usernames.isEmpty()) {
            return new ActivityRequest(PreparationStatus.EMPTY, List.of(), skipped);
        }
        if (usernames.size() > MAX_PLAYERS) {
            return new ActivityRequest(PreparationStatus.TOO_MANY, usernames, skipped);
        }
        return new ActivityRequest(PreparationStatus.VALID, usernames, skipped);
    }

    @Override
    public Text displayName() {
        return Text.translatable("dmls.module.activity.name");
    }

    @Override
    public ItemStack icon() {
        return new ItemStack(Items.COMPASS);
    }

    @Override
    public List<Text> description() {
        return List.of(Text.translatable("dmls.module.activity.description"));
    }

    @Override
    public void openScreen(MinecraftClient client, Screen parent) {
        client.setScreen(new ActivityWaveScreen(parent, this));
    }

    @Override
    public void register() {
        // Tick and server-message routing are owned by OperationCoordinator.
    }

    /** Starts an activity check for the given players. The command and GUI share this entrypoint. */
    public SubmitStatus submit(MinecraftClient client, String input) {
        ActivityRequest request = prepare(input);
        if (!request.skipped().isEmpty()) {
            ChatUtils.sendTranslatedMessage(client, PREFIX,
                    request.skipped().size() == 1
                            ? "dmls.chat.activity.skipping.one" : "dmls.chat.activity.skipping.many",
                    String.join(", ", request.skipped()));
        }
        if (!request.valid()) {
            if (request.status() == PreparationStatus.TOO_MANY) {
                ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.activity.too_many", MAX_PLAYERS);
                return SubmitStatus.TOO_MANY;
            }
            ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.common.invalid_igns");
            return SubmitStatus.INVALID;
        }
        if (!canRunPrivilegedOperation(client)) return SubmitStatus.BLOCKED;

        ActivityWaveOperation operation = new ActivityWaveOperation(request.usernames(), listener());
        OperationStartResult started = OperationCoordinator.global().start(
                client, OPERATION_ID, displayName().getString(), operation);
        return switch (started) {
            case STARTED -> operation.acceptedAtStart() ? SubmitStatus.STARTED : SubmitStatus.BLOCKED;
            case BUSY -> {
                String owner = OperationCoordinator.global().activeDescriptor()
                        .map(descriptor -> descriptor.displayName()).orElse("another operation");
                ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.operation.busy", owner);
                yield SubmitStatus.BUSY;
            }
            case SERVER_BLOCKED -> {
                ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.command.not_sent");
                yield SubmitStatus.BLOCKED;
            }
            case INVALID, FAILED_TO_START -> {
                ChatUtils.sendTranslatedMessage(client, PREFIX,
                        "dmls.chat.operation.not_started", started.name());
                yield SubmitStatus.FAILED;
            }
        };
    }

    /** Cancels only this module's active operation; another module's slot is left untouched. */
    public boolean cancel(MinecraftClient client) {
        OperationCancelResult result = OperationCoordinator.global().cancel(OPERATION_ID, client);
        if (result != OperationCancelResult.CANCELLED) {
            ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.activity.nothing");
        }
        return result == OperationCancelResult.CANCELLED;
    }

    private ActivityWaveOperation.Listener listener() {
        return new ActivityWaveOperation.Listener() {
            @Override
            public void started(MinecraftClient client, int playerCount) {
                ChatUtils.sendTranslatedMessage(client, PREFIX,
                        playerCount == 1
                                ? "dmls.chat.activity.start.one" : "dmls.chat.activity.start.many",
                        playerCount);
            }

            @Override
            public void progress(MinecraftClient client, String username, int playerIndex, int playerCount) {
                ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.activity.progress",
                        username, playerIndex, playerCount);
            }

            @Override
            public void finished(MinecraftClient client, ActivityWaveOperation.Summary summary) {
                report(client, summary);
            }

            @Override
            public void cancelled(MinecraftClient client, ActivityWaveOperation.Summary summary,
                                  OperationCancelReason reason) {
                report(client, summary);
                String key = switch (reason) {
                    case USER_REQUESTED, MODULE_REQUESTED -> "dmls.chat.activity.cancelled";
                    case DISPATCH_BLOCKED -> "dmls.chat.command.not_sent";
                    case CONNECTION_CHANGED, INTERNAL_ERROR -> "dmls.chat.session.cancelled";
                };
                ChatUtils.sendTranslatedMessage(client, PREFIX, key);
            }
        };
    }

    private void report(MinecraftClient client, ActivityWaveOperation.Summary summary) {
        String header = PREFIX;
        ChatUtils.sendClientMessage(client, header + ChatUtils.separatorForChatWidth(client, header));
        ChatUtils.sendTranslatedMessage(client, "", "dmls.chat.activity.header", summary.reportedDays());

        summary.results().entrySet().stream()
                .sorted(Comparator.comparingDouble((Map.Entry<String, ActivityWaveOperation.ActivityValue> entry) ->
                        entry.getValue().kind() == ActivityWaveOperation.ResultKind.HOURS
                                ? entry.getValue().hours() : -1).reversed())
                .forEach(entry -> {
                    String key = switch (entry.getValue().kind()) {
                        case HOURS -> "dmls.chat.activity.result";
                        case NO_RESPONSE -> "dmls.chat.activity.no_data";
                        case SIMULATED -> "dmls.chat.activity.simulated";
                    };
                    Object[] args = entry.getValue().kind() == ActivityWaveOperation.ResultKind.HOURS
                            ? new Object[]{entry.getKey(), String.format(Locale.ROOT, "%.1f", entry.getValue().hours())}
                            : new Object[]{entry.getKey()};
                    ChatUtils.sendClientMessage(client, Text.literal("§8• ").append(ChatUtils.translated(key, args)));
                });
        ChatUtils.sendClientMessage(client, "§7" + ChatUtils.separatorForChatWidth(client, ""));
    }
}
