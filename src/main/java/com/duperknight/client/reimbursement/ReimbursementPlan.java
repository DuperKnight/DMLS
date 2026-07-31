package com.duperknight.client.reimbursement;

import com.duperknight.client.utils.InputValidators;
import com.duperknight.client.utils.PrefixTextFormatter;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

/** Validated immutable execution snapshot. */
public record ReimbursementPlan(ReimbursementDraft.Snapshot draft, int requiredStacks) {
    public static final BigDecimal MAX_MONEY = new BigDecimal("999999999.99");
    public static final int MAX_ITEM_AMOUNT = 9999;
    public static final int MAX_ENCHANTMENT_LEVEL = 255;
    public static final int PLAYER_INVENTORY_SLOTS = 36;

    public ReimbursementPlan {
        Objects.requireNonNull(draft, "draft");
        if (requiredStacks < 0) throw new IllegalArgumentException("requiredStacks");
    }

    public static Preparation prepare(ReimbursementDraft draft) {
        return prepare(Objects.requireNonNull(draft, "draft").snapshot());
    }

    public static Preparation prepare(ReimbursementDraft.Snapshot draft) {
        return prepare(draft, Registries.ITEM::containsId,
                id -> Math.max(1, Registries.ITEM.get(id).getDefaultStack().getMaxCount()));
    }

    static Preparation prepare(
            ReimbursementDraft.Snapshot draft,
            Predicate<Identifier> itemExists,
            ToIntFunction<Identifier> maxStackSize
    ) {
        if (draft.entries().isEmpty()) return Preparation.error("Add at least one item or money entry.");
        if (!validIgnList(draft.ign())) return Preparation.error("Enter one or more valid Minecraft IGNs.");
        if (draft.discordUsername().isBlank()) return Preparation.error("Enter a Discord username.");
        if (!draft.discordId().isBlank() && !draft.discordId().matches("[0-9]{5,20}")) {
            return Preparation.error("Discord ID must contain only digits.");
        }
        if (draft.reason().isBlank()) return Preparation.error("Enter a reimbursement reason.");
        if (draft.ticket().isBlank()) return Preparation.error("Enter a ticket.");

        Map<String, Integer> nonContainerStacks = new LinkedHashMap<>();
        int totalStacks = 0;
        for (int index = 0; index < draft.entries().size(); index++) {
            ReimbursementEntry entry = draft.entries().get(index);
            String prefix = "Entry " + (index + 1) + ": ";
            if (entry.destination() == Destination.PLAYER && !InputValidators.isUsername(entry.playerIgn().trim())) {
                return Preparation.error(prefix + "enter a valid player IGN.");
            }
            if (entry instanceof MoneyEntry money) {
                if (money.amount().scale() > 2 || money.amount().compareTo(BigDecimal.ZERO) <= 0
                        || money.amount().compareTo(MAX_MONEY) > 0) {
                    return Preparation.error(prefix + "money must be between 0.01 and 999,999,999.99.");
                }
                continue;
            }

            ItemEntry itemEntry = (ItemEntry) entry;
            if (itemEntry.itemId() == null || !itemExists.test(itemEntry.itemId())) {
                return Preparation.error(prefix + "select a vanilla item.");
            }
            if (itemEntry.amount() < 1 || itemEntry.amount() > MAX_ITEM_AMOUNT) {
                return Preparation.error(prefix + "amount must be between 1 and 9999.");
            }
            if (!itemEntry.customName().isBlank()
                    && !PrefixTextFormatter.serializeJson(itemEntry.customName()).valid()) {
                return Preparation.error(prefix + "item name formatting is invalid.");
            }
            for (String loreLine : itemEntry.lore()) {
                if (!PrefixTextFormatter.serializeJson(loreLine).valid()) {
                    return Preparation.error(prefix + "lore formatting is invalid.");
                }
            }
            for (Map.Entry<Identifier, Integer> enchantment : itemEntry.enchantments().entrySet()) {
                if (enchantment.getKey() == null || enchantment.getValue() == null
                        || enchantment.getValue() < 1 || enchantment.getValue() > MAX_ENCHANTMENT_LEVEL) {
                    return Preparation.error(prefix + "enchantment levels must be between 1 and 255.");
                }
            }
            if (itemEntry.destination() == Destination.CONTAINER && itemEntry.containers().isEmpty()) {
                return Preparation.error(prefix + "select at least one container.");
            }

            int stacks = divideRoundUp(itemEntry.amount(),
                    Math.max(1, maxStackSize.applyAsInt(itemEntry.itemId())));
            totalStacks += stacks;
            if (itemEntry.destination() != Destination.CONTAINER) {
                String key = itemEntry.destination() == Destination.ME
                        ? "me"
                        : "player:" + itemEntry.playerIgn().trim().toLowerCase(Locale.ROOT);
                int required = nonContainerStacks.merge(key, stacks, Integer::sum);
                if (required > PLAYER_INVENTORY_SLOTS) {
                    return Preparation.error("A non-container destination requires more than 36 inventory slots. "
                            + "Reduce the items or use a container.");
                }
            }
        }

        ReimbursementPlan plan = new ReimbursementPlan(draft, totalStacks);
        ReimbursementCommandPlanner.BuildResult commands =
                ReimbursementCommandPlanner.build(plan, maxStackSize);
        if (!commands.valid()) return Preparation.error(commands.error());
        return Preparation.success(plan);
    }

    public List<String> playerTargets() {
        return draft.entries().stream()
                .filter(entry -> entry.destination() == Destination.PLAYER)
                .map(ReimbursementEntry::playerIgn)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(value -> value.toLowerCase(Locale.ROOT))
                .distinct()
                .toList();
    }

    public List<ContainerTarget> containerTargets() {
        List<ContainerTarget> targets = new ArrayList<>();
        for (ReimbursementEntry entry : draft.entries()) {
            if (entry instanceof ItemEntry item && item.destination() == Destination.CONTAINER) {
                for (ContainerTarget target : item.containers()) {
                    if (!targets.contains(target)) targets.add(target);
                }
            }
        }
        return List.copyOf(targets);
    }

    public static int stackCount(ItemEntry entry) {
        return stackCount(entry,
                id -> Math.max(1, Registries.ITEM.get(id).getDefaultStack().getMaxCount()));
    }

    static int stackCount(ItemEntry entry, ToIntFunction<Identifier> maxStackSize) {
        return divideRoundUp(entry.amount(), Math.max(1, maxStackSize.applyAsInt(entry.itemId())));
    }

    private static boolean validIgnList(String value) {
        if (value == null || value.isBlank()) return false;
        for (String ign : value.split("\\s*,\\s*")) {
            if (!InputValidators.isUsername(ign)) return false;
        }
        return true;
    }

    private static int divideRoundUp(int value, int divisor) {
        return (value + divisor - 1) / divisor;
    }

    public record Preparation(ReimbursementPlan plan, String error) {
        public static Preparation success(ReimbursementPlan plan) {
            return new Preparation(plan, "");
        }

        public static Preparation error(String error) {
            return new Preparation(null, error);
        }

        public boolean valid() {
            return plan != null;
        }
    }
}
