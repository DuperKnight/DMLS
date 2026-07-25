package com.duperknight.client.reimbursement;

import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;

/** Builds exact bounded server commands before a live operation starts. */
public final class ReimbursementCommandPlanner {
    public static final int MAX_COMMAND_LENGTH = 255;

    private ReimbursementCommandPlanner() {
    }

    public static BuildResult build(ReimbursementPlan plan) {
        return build(plan, id -> Math.max(1,
                Registries.ITEM.get(id).getDefaultStack().getMaxCount()));
    }

    static BuildResult build(ReimbursementPlan plan, ToIntFunction<Identifier> maxStackSize) {
        List<StackPlan> stacks = new ArrayList<>();
        List<String> moneyCommands = new ArrayList<>();
        for (ReimbursementEntry entry : plan.draft().entries()) {
            if (entry instanceof MoneyEntry money) {
                moneyCommands.add("eco give %s %s".formatted(
                        money.destination() == Destination.ME ? "{self}" : money.playerIgn().trim(),
                        money.amount().stripTrailingZeros().toPlainString()));
                continue;
            }
            ItemEntry itemEntry = (ItemEntry) entry;
            int remaining = itemEntry.amount();
            int maxCount = Math.max(1, maxStackSize.applyAsInt(itemEntry.itemId()));
            while (remaining > 0) {
                int count = Math.min(remaining, maxCount);
                StackPlan stack = buildStack(itemEntry, count);
                if (!stack.valid()) return BuildResult.error(stack.error());
                stacks.add(stack);
                remaining -= count;
            }
        }
        return BuildResult.success(stacks, moneyCommands);
    }

    static BatchPlan batch(List<StackPlan> stacks, int startIndex, int freeSlots) {
        return batch(stacks, startIndex, freeSlots, "@s");
    }

    static BatchPlan batch(
            List<StackPlan> stacks,
            int startIndex,
            int freeSlots,
            String recipient
    ) {
        if (startIndex < 0 || startIndex >= stacks.size()) {
            throw new IndexOutOfBoundsException("No stack plan at index " + startIndex);
        }
        StackPlan source = stacks.get(startIndex);
        StackPlan first = buildStack(source.entry(), source.count(), recipient);
        int limit = Math.max(1, freeSlots);
        if (requiresHeldItemCommands(first) || limit == 1) return new BatchPlan(first, 1);

        int count = first.count();
        int consumed = 1;
        while (consumed < limit && startIndex + consumed < stacks.size()) {
            StackPlan nextSource = stacks.get(startIndex + consumed);
            StackPlan next = buildStack(nextSource.entry(), nextSource.count(), recipient);
            if (requiresHeldItemCommands(next) || !next.entry().equals(first.entry())) break;
            count += next.count();
            consumed++;
        }
        if (consumed == 1) return new BatchPlan(first, 1);

        StackPlan combined = buildStack(first.entry(), count, recipient);
        return combined.valid() && !requiresHeldItemCommands(combined)
                ? new BatchPlan(combined, consumed)
                : new BatchPlan(first, 1);
    }

    private static boolean requiresHeldItemCommands(StackPlan stack) {
        return stack.commands().size() > 1;
    }

    private static StackPlan buildStack(ItemEntry entry, int count) {
        return buildStack(entry, count, "@s");
    }

    private static StackPlan buildStack(ItemEntry entry, int count, String recipient) {
        List<String> commands = new ArrayList<>();
        commands.add("minecraft:give " + recipient + " " + entry.itemId() + " " + count);
        if (!entry.customName().isBlank()) commands.add("rename " + entry.customName());
        for (String line : entry.lore()) {
            commands.add("addll " + line);
        }
        for (Map.Entry<Identifier, Integer> enchantment : entry.enchantments().entrySet()) {
            commands.add("enchant " + enchantment.getKey().getPath() + " " + enchantment.getValue());
        }
        for (String command : commands) {
            if (command.length() > MAX_COMMAND_LENGTH) {
                return StackPlan.error("A generated command is longer than 255 characters: /"
                        + command.substring(0, Math.min(command.length(), 48)) + "…");
            }
        }
        return StackPlan.success(entry, count, commands, true);
    }

    public record StackPlan(
            ItemEntry entry,
            int count,
            List<String> commands,
            boolean fallback,
            String error
    ) {
        private static StackPlan success(ItemEntry entry, int count, List<String> commands, boolean fallback) {
            return new StackPlan(entry, count, List.copyOf(commands), fallback, "");
        }

        private static StackPlan error(String error) {
            return new StackPlan(null, 0, List.of(), false, error);
        }

        public boolean valid() {
            return error.isEmpty();
        }
    }

    public record BuildResult(List<StackPlan> stacks, List<String> moneyCommands, String error) {
        private static BuildResult success(List<StackPlan> stacks, List<String> moneyCommands) {
            return new BuildResult(List.copyOf(stacks), List.copyOf(moneyCommands), "");
        }

        private static BuildResult error(String error) {
            return new BuildResult(List.of(), List.of(), error);
        }

        public boolean valid() {
            return error.isEmpty();
        }

        public int commandCount() {
            return moneyCommands.size() + stacks.stream().mapToInt(stack -> stack.commands().size()).sum();
        }
    }

    record BatchPlan(StackPlan stack, int consumedStacks) {
    }
}
