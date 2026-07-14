package com.duperknight.client.modules;

import com.duperknight.client.gui.modules.PrefixCreateScreen;
import com.duperknight.client.message.MessageOrigin;
import com.duperknight.client.message.ServerMessage;
import com.duperknight.client.parser.PrefixResponseParser;
import com.duperknight.client.session.CommandDispatch;
import com.duperknight.client.session.ManagedOperation;
import com.duperknight.client.session.OperationCancelReason;
import com.duperknight.client.session.OperationCoordinator;
import com.duperknight.client.session.OperationHandle;
import com.duperknight.client.session.OperationStartResult;
import com.duperknight.client.session.PacedCommandSequence;
import com.duperknight.client.session.ResponseStatus;
import com.duperknight.client.utils.ChatUtils;
import com.duperknight.client.utils.InputValidators;
import com.duperknight.client.utils.PrefixTextFormatter;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

public final class PrefixCreateModule extends DMLSModule {
    public static final int MAX_COMMAND_LENGTH = 256;
    public static final String CUSTOM_LIMIT = "Custom";
    public static final List<String> LIMITS = List.of("10", "30", Integer.toString(Integer.MAX_VALUE), CUSTOM_LIMIT);

    private static final String PREFIX = "§8[§6DMLS - Prefix§8] §7";
    private static final String OPERATION_ID = "prefix_create";
    private static final int RESPONSE_TIMEOUT_TICKS = 20 * 10;

    public PrefixCreateModule() {
        super(StaffRank.SUPPORT);
    }

    @Override
    public Text displayName() {
        return Text.translatable("dmls.module.prefix.name");
    }

    @Override
    public ItemStack icon() {
        return new ItemStack(Items.OAK_SIGN);
    }

    @Override
    public List<Text> description() {
        return List.of(
                Text.translatable("dmls.module.prefix.description.1"),
                Text.translatable("dmls.module.prefix.description.2")
        );
    }

    @Override
    public void openScreen(MinecraftClient client, Screen parent) {
        client.setScreen(new PrefixCreateScreen(parent, this));
    }

    @Override
    public void register() {
        // Canonical command is registered under /dmls by DMLSClient.
    }

    /** Starts the prefix creation. The command and GUI both call this method. */
    public ValidationResult submit(
            MinecraftClient client,
            String ign,
            String limit,
            String prefixId,
            String prefixText
    ) {
        if (!canRunPrivilegedOperation(client)) {
            return ValidationResult.error("dmls.validation.required_rank");
        }

        ValidationResult validation = validate(ign, limit, prefixId, prefixText);
        if (!validation.valid()) {
            ChatUtils.sendClientMessage(client, Text.literal(PREFIX).append(validation.message()));
            return validation;
        }

        CreateRequest request = createRequest(ign, validation.limit(), prefixId, prefixText);
        CreateOperation operation = new CreateOperation(request);
        OperationStartResult started = OperationCoordinator.global().start(
                client, OPERATION_ID, "Prefix Creation", operation);
        if (started != OperationStartResult.STARTED || !operation.acceptedAtStart()) {
            String errorKey = started == OperationStartResult.BUSY
                    ? "dmls.validation.prefix.active"
                    : "dmls.chat.command.not_sent";
            ValidationResult startError = ValidationResult.error(errorKey);
            ChatUtils.sendClientMessage(client, Text.literal(PREFIX).append(startError.message()));
            return startError;
        }

        return ValidationResult.success(validation.limit());
    }

    public static ValidationResult validate(String ign, String limit, String prefixId, String prefixText) {
        if (!InputValidators.isUsername(ign)) {
            return ValidationResult.error("dmls.validation.prefix.ign");
        }

        Optional<String> resolvedLimit = resolveLimit(limit);
        if (resolvedLimit.isEmpty()) {
            return ValidationResult.error("dmls.validation.prefix.limit");
        }

        if (!InputValidators.isPrefixId(prefixId)) {
            return ValidationResult.error("dmls.validation.prefix.id");
        }

        if (prefixText.isEmpty()) {
            return ValidationResult.error("dmls.validation.prefix.text");
        }

        PrefixTextFormatter.ParseResult formattedPrefix = PrefixTextFormatter.parse(prefixText);
        if (!formattedPrefix.valid()) {
            return ValidationResult.error("dmls.validation.prefix.format", formattedPrefix.error());
        }

        for (String command : commands(ign, resolvedLimit.get(), prefixId, prefixText)) {
            if (command.length() > MAX_COMMAND_LENGTH) {
                return ValidationResult.error(
                        "dmls.validation.prefix.command_length", command.length(), MAX_COMMAND_LENGTH);
            }
        }

        return ValidationResult.success(resolvedLimit.get());
    }

    public static Optional<String> resolveLimit(String limit) {
        try {
            int parsed = Integer.parseInt(limit);
            return parsed > 0 ? Optional.of(Integer.toString(parsed)) : Optional.empty();
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
    }

    public static String createCommand(String prefixId, String prefixText) {
        return "prefix create %s %s".formatted(prefixId, prefixText);
    }

    public static List<String> commands(String ign, String limit, String prefixId, String prefixText) {
        return List.of(
                createCommand(prefixId, prefixText),
                "prefix x setlimit %s %s".formatted(prefixId, limit),
                "prefix x setmanager %s %s".formatted(prefixId, ign),
                "prefix x info %s".formatted(prefixId)
        );
    }

    static CreateRequest createRequest(String ign, String limit, String prefixId, String prefixText) {
        return new CreateRequest(ign, limit, prefixId, prefixText,
                commands(ign, limit, prefixId, prefixText));
    }

    static PacedCommandSequence<PrefixResponseParser.Command> createSequence(
            CreateRequest request,
            Function<String, CommandDispatch> dispatcher
    ) {
        return new PacedCommandSequence<>(
                List.of(PrefixResponseParser.Command.values()),
                0,
                RESPONSE_TIMEOUT_TICKS + 1,
                command -> dispatcher.apply(request.commands().get(command.ordinal())),
                (command, message) -> switch (PrefixResponseParser.parse(
                        command, request.prefixId(), request.ign(), request.limit(), message)) {
                    case CONFIRMED -> ResponseStatus.CONFIRMED;
                    case REJECTED -> ResponseStatus.REJECTED;
                    case UNRELATED -> ResponseStatus.UNRELATED;
                }
        );
    }

    record CreateRequest(
            String ign,
            String limit,
            String prefixId,
            String prefixText,
            List<String> commands
    ) {
        CreateRequest {
            commands = List.copyOf(commands);
        }
    }

    public record ValidationResult(String limit, Text message) {
        public static ValidationResult success(String limit) {
            return new ValidationResult(limit, Text.empty());
        }

        public static ValidationResult error(String translationKey, Object... args) {
            return new ValidationResult("", Text.translatable(translationKey, args));
        }

        public boolean valid() {
            return message.getString().isEmpty();
        }
    }

    private final class CreateOperation implements ManagedOperation {
        private final CreateRequest request;
        private PacedCommandSequence<PrefixResponseParser.Command> sequence;
        private boolean terminalReported;

        private CreateOperation(CreateRequest request) {
            this.request = request;
        }

        private boolean acceptedAtStart() {
            return sequence != null && sequence.state() != PacedCommandSequence.State.BLOCKED
                    && sequence.state() != PacedCommandSequence.State.FAILED;
        }

        @Override
        public void onStarted(OperationHandle handle, MinecraftClient client) {
            ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.prefix.start",
                    request.prefixId(), request.ign());
            sequence = createSequence(request, command -> handle.dispatchCommand(client, command));
            handleState(handle, client, sequence.start());
        }

        @Override
        public void onTick(OperationHandle handle, MinecraftClient client) {
            handleState(handle, client, sequence.tick());
        }

        @Override
        public void onServerMessage(OperationHandle handle, MinecraftClient client, ServerMessage message) {
            if (message.origin() != MessageOrigin.SERVER_SYSTEM) return;
            sequence.accept(message.cleanText());
            handleState(handle, client, sequence.state());
        }

        @Override
        public void onCancelled(
                OperationHandle handle,
                MinecraftClient client,
                OperationCancelReason reason
        ) {
            if (terminalReported) return;
            if (sequence != null) sequence.cancel();
            terminalReported = true;
            ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.prefix.cancelled");
        }

        private void handleState(
                OperationHandle handle,
                MinecraftClient client,
                PacedCommandSequence.State state
        ) {
            if (terminalReported) return;
            switch (state) {
                case COMPLETED -> finish(handle, client,
                        sequence.simulatedCount() > 0
                                ? "dmls.chat.prefix.dispatched.before"
                                : "dmls.chat.prefix.confirmed.before",
                        sequence.simulatedCount() > 0
                                ? "dmls.chat.prefix.dispatched.after"
                                : "dmls.chat.prefix.confirmed.after");
                case REJECTED, FAILED -> finish(handle, client,
                        "dmls.chat.prefix.rejected.before", "dmls.chat.prefix.rejected.after");
                case TIMED_OUT -> finish(handle, client,
                        "dmls.chat.prefix.timed_out.before", "dmls.chat.prefix.timed_out.after");
                case BLOCKED, CANCELLED -> cancel(handle, client);
                case NEW, AWAITING_RESPONSE, PACING -> {
                }
            }
        }

        private void finish(
                OperationHandle handle,
                MinecraftClient client,
                String beforeKey,
                String afterKey
        ) {
            terminalReported = true;
            handle.complete();
            PrefixTextFormatter.ParseResult formattedPrefix = PrefixTextFormatter.parse(request.prefixText());
            Text displayedPrefix = formattedPrefix.valid()
                    ? formattedPrefix.preview()
                    : Text.literal(request.prefixText());
            ChatUtils.sendClientMessage(client, Text.literal(PREFIX)
                    .append(ChatUtils.translated(beforeKey, request.prefixId()))
                    .append(displayedPrefix)
                    .append(ChatUtils.translated(afterKey, request.limit(), request.ign())));
        }

        private void cancel(OperationHandle handle, MinecraftClient client) {
            terminalReported = true;
            handle.complete();
            ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.prefix.cancelled");
        }
    }
}
