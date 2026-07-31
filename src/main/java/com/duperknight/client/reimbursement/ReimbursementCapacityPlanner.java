package com.duperknight.client.reimbursement;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.ToIntFunction;

/** Conservative empty-slot reservation used before any reimbursement item is created. */
public final class ReimbursementCapacityPlanner {
    private ReimbursementCapacityPlanner() {
    }

    public static Result simulate(
            ReimbursementPlan plan,
            int staffSlots,
            Map<String, Integer> playerSlots,
            Map<ContainerTarget, Integer> containerSlots
    ) {
        return simulate(plan, staffSlots, playerSlots, containerSlots, ReimbursementPlan::stackCount);
    }

    static Result simulate(
            ReimbursementPlan plan,
            int staffSlots,
            Map<String, Integer> playerSlots,
            Map<ContainerTarget, Integer> containerSlots,
            ToIntFunction<ItemEntry> stackCounter
    ) {
        int staffRemaining = Math.max(0, staffSlots);
        Map<String, Integer> players = new HashMap<>();
        playerSlots.forEach((key, value) -> players.put(key.toLowerCase(Locale.ROOT), Math.max(0, value)));
        Map<ContainerTarget, Integer> containers = new HashMap<>();
        containerSlots.forEach((key, value) -> containers.put(key, Math.max(0, value)));

        for (ReimbursementEntry entry : plan.draft().entries()) {
            if (!(entry instanceof ItemEntry item)) continue;
            int stacks = stackCounter.applyAsInt(item);
            if (item.destination() == Destination.ME) {
                if (staffRemaining < stacks) return Result.insufficient(staffRemaining, stacks - staffRemaining);
                staffRemaining -= stacks;
                continue;
            }
            if (item.destination() == Destination.PLAYER) {
                String key = item.playerIgn().trim().toLowerCase(Locale.ROOT);
                int available = players.getOrDefault(key, 0);
                int used = Math.min(available, stacks);
                players.put(key, available - used);
                stacks -= used;
            } else {
                for (ContainerTarget target : item.containers()) {
                    int available = containers.getOrDefault(target, 0);
                    int used = Math.min(available, stacks);
                    containers.put(target, available - used);
                    stacks -= used;
                    if (stacks == 0) break;
                }
            }
            if (stacks > staffRemaining) return Result.insufficient(staffRemaining, stacks - staffRemaining);
            staffRemaining -= stacks;
        }
        return Result.fits(staffRemaining);
    }

    public record Result(boolean fits, int staffSlotsRemaining, int missingSlots) {
        private static Result fits(int staffSlotsRemaining) {
            return new Result(true, staffSlotsRemaining, 0);
        }

        private static Result insufficient(int staffSlotsRemaining, int missingSlots) {
            return new Result(false, staffSlotsRemaining, missingSlots);
        }
    }
}
