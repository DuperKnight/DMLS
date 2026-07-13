package com.duperknight.client.modules;

import com.duperknight.client.gui.modules.DonorPetScreen;
import com.duperknight.client.session.OperationCancelReason;
import com.duperknight.client.session.OperationCoordinator;
import com.duperknight.client.session.OperationStartResult;
import com.duperknight.client.utils.ChatUtils;
import com.duperknight.client.utils.InputValidators;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class DonorPetModule extends DMLSModule {
    public static final String OPERATION_ID = "donor-pet";
    private static final String PREFIX = "§8[§6DMLS - DonorPet§8] §7";
    private static final Map<String, String> PET_PERMISSIONS = new LinkedHashMap<>();

    static {
        PET_PERMISSIONS.put("blackcrow", "mcpets.elitemountvol6blackcrow");
        PET_PERMISSIONS.put("griffon", "mcpets.elitemountvol6whitegriffon");
        PET_PERMISSIONS.put("axolotl", "mcpets.elitemountvol6pinkaxolottle");
        PET_PERMISSIONS.put("yak", "mcpets.elitemountvol6whiteyak");
        PET_PERMISSIONS.put("dog", "mcpets.elitemountvol6huskydog");
    }

    public DonorPetModule() {
        super(StaffRank.ADMIN);
    }

    public enum PreparationStatus { VALID, INVALID_USERNAME, UNKNOWN_PET }

    public enum SubmitStatus { STARTED, INVALID, BLOCKED, BUSY, FAILED }

    public record DonorPetRequest(
            PreparationStatus status,
            String username,
            String pet,
            String permission,
            String command
    ) {
        public boolean valid() {
            return status == PreparationStatus.VALID;
        }
    }

    /** Returns the names of all available pets. */
    public static List<String> pets() {
        return List.copyOf(PET_PERMISSIONS.keySet());
    }

    public static DonorPetRequest prepare(String username, String pet) {
        String cleanUsername = username == null ? "" : username.trim();
        String cleanPet = pet == null ? "" : pet.trim().toLowerCase(Locale.ROOT);
        if (!InputValidators.isUsername(cleanUsername)) {
            return new DonorPetRequest(PreparationStatus.INVALID_USERNAME,
                    cleanUsername, cleanPet, "", "");
        }
        String permission = PET_PERMISSIONS.get(cleanPet);
        if (permission == null) {
            return new DonorPetRequest(PreparationStatus.UNKNOWN_PET,
                    cleanUsername, cleanPet, "", "");
        }
        String command = "lp user %s permission set %s true".formatted(cleanUsername, permission);
        return new DonorPetRequest(PreparationStatus.VALID,
                cleanUsername, cleanPet, permission, command);
    }

    @Override
    public Text displayName() {
        return Text.translatable("dmls.module.donor_pet.name");
    }

    @Override
    public ItemStack icon() {
        return new ItemStack(Items.BONE);
    }

    @Override
    public List<Text> description() {
        return List.of(Text.translatable("dmls.module.donor_pet.description"));
    }

    @Override
    public void openScreen(MinecraftClient client, Screen parent) {
        client.setScreen(new DonorPetScreen(parent, this));
    }

    @Override
    public void register() {
        // Canonical command is registered under /dmls by DMLSClient.
    }

    /** Gives the pet permission to the given player. The command and GUI share this entrypoint. */
    public SubmitStatus submit(MinecraftClient client, String username, String pet) {
        DonorPetRequest request = prepare(username, pet);
        if (!request.valid()) {
            if (request.status() == PreparationStatus.INVALID_USERNAME) {
                ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.common.invalid_ign");
            } else {
                ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.donor_pet.unknown",
                        request.pet(), String.join(", ", pets()));
            }
            return SubmitStatus.INVALID;
        }
        if (!canRunPrivilegedOperation(client)) return SubmitStatus.BLOCKED;

        DonorPetOperation operation = new DonorPetOperation(request, listener());
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

    private DonorPetOperation.Listener listener() {
        return new DonorPetOperation.Listener() {
            @Override
            public void waiting(MinecraftClient client, DonorPetRequest request) {
                ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.donor_pet.waiting",
                        request.username(), request.pet());
            }

            @Override
            public void finished(MinecraftClient client, DonorPetOperation.Summary summary) {
                DonorPetRequest request = summary.request();
                switch (summary.outcome()) {
                    case CONFIRMED -> ChatUtils.sendTranslatedMessage(client, PREFIX,
                            "dmls.chat.donor_pet.confirmed",
                            request.username(), request.pet(), request.permission());
                    case REJECTED -> ChatUtils.sendTranslatedMessage(client, PREFIX,
                            "dmls.chat.donor_pet.rejected",
                            request.username(), request.pet(), request.permission());
                    case SIMULATED -> ChatUtils.sendTranslatedMessage(client, PREFIX,
                            "dmls.chat.donor_pet.simulated",
                            request.username(), request.pet(), request.permission());
                    case SENT_UNVERIFIED -> ChatUtils.sendTranslatedMessage(client, PREFIX,
                            "dmls.chat.donor_pet.sent",
                            request.username(), request.pet(), request.permission());
                    case BLOCKED -> ChatUtils.sendTranslatedMessage(client, PREFIX,
                            "dmls.chat.command.not_sent");
                    case CANCELLED -> throw new IllegalStateException("Cancellation uses the cancellation callback");
                }
            }

            @Override
            public void cancelled(MinecraftClient client, DonorPetOperation.Summary summary,
                                  OperationCancelReason reason) {
                ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.donor_pet.cancelled",
                        summary.request().username(), summary.request().pet());
            }
        };
    }
}
