package com.duperknight.client.reimbursement;

import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReimbursementPlanningTest {
    @Test
    void rejectsMoneyOutsideDecimalLimits() {
        assertFalse(prepare(configuredDraft(
                new MoneyEntry(new BigDecimal("0.001"), Destination.ME, ""))).valid());
        assertFalse(prepare(configuredDraft(
                new MoneyEntry(new BigDecimal("1000000000.00"), Destination.ME, ""))).valid());
        assertTrue(prepare(configuredDraft(
                new MoneyEntry(new BigDecimal("999999999.99"), Destination.ME, ""))).valid());
    }

    @Test
    void staticGateRejectsMoreThanThirtySixSlots() {
        ReimbursementDraft draft = configuredDraft(item("stone", 36 * 64 + 1, Destination.ME, "", List.of()));

        ReimbursementPlan.Preparation result = prepare(draft);

        assertFalse(result.valid());
        assertTrue(result.error().contains("more than 36"));
    }

    @Test
    void capacityUsesDestinationThenSharedStaffFallbackWithoutDoubleCounting() {
        ItemEntry first = item("stone", 128, Destination.PLAYER, "FirstPlayer", List.of());
        ItemEntry second = item("stone", 64, Destination.PLAYER, "SecondPlayer", List.of());
        ReimbursementPlan plan = new ReimbursementPlan(
                configuredDraft(first, second).snapshot(), 3);

        ReimbursementCapacityPlanner.Result fits = capacity(
                plan, 1, Map.of("firstplayer", 1, "secondplayer", 1), Map.of());
        ReimbursementCapacityPlanner.Result missing = capacity(
                plan, 0, Map.of("firstplayer", 1, "secondplayer", 1), Map.of());

        assertTrue(fits.fits());
        assertFalse(missing.fits());
        assertEquals(1, missing.missingSlots());
    }

    @Test
    void selectedContainerPoolIsConsumedInSelectionOrder() {
        ContainerTarget first = new ContainerTarget("minecraft:overworld", new BlockPos(1, 2, 3));
        ContainerTarget second = new ContainerTarget("minecraft:overworld", new BlockPos(4, 5, 6));
        ItemEntry entry = item("stone", 192, Destination.CONTAINER, "", List.of(first, second));
        ReimbursementPlan plan = new ReimbursementPlan(configuredDraft(entry).snapshot(), 3);

        assertTrue(capacity(plan, 0, Map.of(), Map.of(first, 1, second, 2)).fits());
        assertFalse(capacity(plan, 0, Map.of(), Map.of(first, 1, second, 1)).fits());
    }

    @Test
    void plannerSplitsStacksAndAppendsFallbackLoreLinesInOrder() {
        List<String> lore = IntStream.rangeClosed(1, 40).mapToObj(value -> "line" + value).toList();
        ItemEntry entry = item("stone", 130, Destination.ME, "", List.of()).withLore(lore);
        ReimbursementPlan plan = new ReimbursementPlan(configuredDraft(entry).snapshot(), 3);

        ReimbursementCommandPlanner.BuildResult result = ReimbursementCommandPlanner.build(plan, ignored -> 64);

        assertTrue(result.valid());
        assertEquals(3, result.stacks().size());
        assertTrue(result.stacks().getFirst().fallback());
        assertTrue(result.stacks().getFirst().commands().contains("addll line1"));
        assertTrue(result.stacks().getFirst().commands().contains("addll line40"));
        assertFalse(result.stacks().getFirst().commands().stream()
                .anyMatch(command -> command.matches("addll .* \\d+")));
    }

    @Test
    void plannerRejectsAnOversizedFallbackCommand() {
        ItemEntry entry = item("diamond_sword", 1, Destination.ME, "", List.of())
                .withCustomName("x".repeat(300));
        ReimbursementPlan plan = new ReimbursementPlan(configuredDraft(entry).snapshot(), 1);

        assertFalse(ReimbursementCommandPlanner.build(plan, ignored -> 1).valid());
    }

    @Test
    void blankLoreLinesAreIgnoredByTheDraftPreviewAndCommands() {
        ItemEntry entry = item("stone", 1, Destination.ME, "", List.of())
                .withLore(List.of("First", "", "   ", "Second"));
        ReimbursementPlan plan = new ReimbursementPlan(configuredDraft(entry).snapshot(), 1);
        ReimbursementCommandPlanner.BuildResult result =
                ReimbursementCommandPlanner.build(plan, ignored -> 64);

        assertEquals(List.of("First", "Second"), entry.lore());
        assertTrue(result.stacks().getFirst().commands().contains("addll First"));
        assertTrue(result.stacks().getFirst().commands().contains("addll Second"));
        assertEquals(2, result.stacks().getFirst().commands().stream()
                .filter(command -> command.startsWith("addll ")).count());
    }

    @Test
    void plannerBatchesPlainFallbackStacksUpToTheAvailableStaffSlots() {
        ItemEntry entry = item("stone", 192, Destination.CONTAINER, "",
                List.of(new ContainerTarget("minecraft:overworld", new BlockPos(1, 2, 3))));
        ReimbursementPlan plan = new ReimbursementPlan(configuredDraft(entry).snapshot(), 3);
        ReimbursementCommandPlanner.BuildResult result =
                ReimbursementCommandPlanner.build(plan, ignored -> 64);

        ReimbursementCommandPlanner.BatchPlan first =
                ReimbursementCommandPlanner.batch(result.stacks(), 0, 2);
        ReimbursementCommandPlanner.BatchPlan last =
                ReimbursementCommandPlanner.batch(result.stacks(), 2, 2);

        assertEquals(2, first.consumedStacks());
        assertEquals(128, first.stack().count());
        assertEquals("minecraft:give @s minecraft:stone 128",
                first.stack().commands().getFirst());
        assertTrue(first.stack().fallback());
        assertEquals(1, last.consumedStacks());
        assertEquals(64, last.stack().count());
    }

    @Test
    void customizedItemsAlwaysUsePlainGiveThenFallbackCommands() {
        LinkedHashMap<Identifier, Integer> enchantments = new LinkedHashMap<>();
        enchantments.put(Identifier.ofVanilla("efficiency"), 3);
        ItemEntry entry = item("diamond_pickaxe", 1, Destination.ME, "", List.of())
                .withCustomName("&6Test Pickaxe")
                .withLore(List.of("First line", "Second line"))
                .withEnchantments(enchantments);
        ReimbursementPlan plan = new ReimbursementPlan(configuredDraft(entry).snapshot(), 1);

        List<String> commands = ReimbursementCommandPlanner.build(plan, ignored -> 1)
                .stacks().getFirst().commands();

        assertEquals(List.of(
                "minecraft:give @s minecraft:diamond_pickaxe 1",
                "rename &6Test Pickaxe",
                "addll First line",
                "addll Second line",
                "enchant efficiency 3"
        ), commands);
    }

    @Test
    void liveItemCommandsTargetTheStaffSessionInsteadOfTheLogIgn() {
        ItemEntry entry = item("stone", 64, Destination.CONTAINER, "",
                List.of(new ContainerTarget("minecraft:overworld", new BlockPos(1, 2, 3))));
        ReimbursementPlan plan = new ReimbursementPlan(configuredDraft(entry).snapshot(), 1);
        ReimbursementCommandPlanner.BuildResult result =
                ReimbursementCommandPlanner.build(plan, ignored -> 64);

        ReimbursementCommandPlanner.BatchPlan live =
                ReimbursementCommandPlanner.batch(result.stacks(), 0, 1, "StaffMember");

        assertEquals("minecraft:give StaffMember minecraft:stone 64",
                live.stack().commands().getFirst());
        assertFalse(live.stack().commands().getFirst().contains("PlanetKingH"));
    }

    @Test
    void plannerDoesNotBatchFallbackStacksThatNeedHeldItemCommands() {
        ItemEntry entry = item("stone", 128, Destination.CONTAINER, "",
                List.of(new ContainerTarget("minecraft:overworld", new BlockPos(1, 2, 3))))
                .withLore(IntStream.rangeClosed(1, 40).mapToObj(value -> "line" + value).toList());
        ReimbursementPlan plan = new ReimbursementPlan(configuredDraft(entry).snapshot(), 2);
        ReimbursementCommandPlanner.BuildResult result =
                ReimbursementCommandPlanner.build(plan, ignored -> 64);

        ReimbursementCommandPlanner.BatchPlan batch =
                ReimbursementCommandPlanner.batch(result.stacks(), 0, 2);

        assertEquals(1, batch.consumedStacks());
        assertEquals(64, batch.stack().count());
        assertTrue(batch.stack().fallback());
    }

    @Test
    void teleportCommandsUseRootLocaleWithoutTreatingItAsACoordinate() {
        assertEquals("tp @s 10.5 64 -2.5",
                ReimbursementOperation.formatTeleportCommand(new BlockPos(10, 64, -3)));
        assertEquals("tp @s 10.125 64.500 -2.750",
                ReimbursementOperation.formatReturnCommand(
                        new Vec3d(10.125, 64.5, -2.75), 90.0F, -15.5F));
    }

    @Test
    void playerNotFoundResponsesAreTerminalCommandRejections() {
        assertTrue(ReimbursementOperation.isServerRejection("Error: Player not found."));
        assertTrue(ReimbursementOperation.isServerRejection("Cannot find player Example"));
        assertFalse(ReimbursementOperation.isServerRejection("Example joined the game"));
    }

    @Test
    void plannerBuildsEconomyRecipientsAndEstimateIncludesNonAdminPacing() {
        MoneyEntry money = new MoneyEntry(new BigDecimal("1250.50"), Destination.PLAYER, "PlanetKingH");
        ReimbursementPlan.Preparation preparation = prepare(configuredDraft(money));
        assertTrue(preparation.valid());

        ReimbursementPlan plan = preparation.plan();
        ReimbursementCommandPlanner.BuildResult commands =
                ReimbursementCommandPlanner.build(plan, ignored -> 64);
        assertEquals("eco give PlanetKingH 1250.5", commands.moneyCommands().getFirst());
        assertTrue(ReimbursementEstimate.calculate(plan, false, 100, 40, commands).totalSeconds()
                > ReimbursementEstimate.calculate(plan, true, 100, 0, commands).totalSeconds());
    }

    @Test
    void logUsesPlainNamesRomanEnchantmentsAndOptionalDiscordId() {
        LinkedHashMap<Identifier, Integer> enchantments = new LinkedHashMap<>();
        enchantments.put(Identifier.ofVanilla("protection"), 4);
        ItemEntry item = item("diamond_helmet", 1, Destination.ME, "", List.of())
                .withCustomName("<gold>Elven Crown")
                .withEnchantments(enchantments);
        ReimbursementDraft draft = configuredDraft(item);
        draft.setDiscordId("");
        ReimbursementPlan plan = new ReimbursementPlan(draft.snapshot(), 1);

        String log = ReimbursementLogFormatter.format(plan, null);

        assertTrue(log.contains("1x Elven Crown"));
        assertTrue(log.contains("- Protection IV"));
        assertTrue(log.contains("**Discord:** planetkingh\n"));
        assertFalse(log.contains("planetkingh /"));
    }

    private static ItemEntry item(
            String id,
            int amount,
            Destination destination,
            String ign,
            List<ContainerTarget> containers
    ) {
        return new ItemEntry(Identifier.ofVanilla(id), "", List.of(), amount, Map.of(),
                destination, ign, containers);
    }

    private static ReimbursementPlan.Preparation prepare(ReimbursementDraft draft) {
        return ReimbursementPlan.prepare(draft.snapshot(), ignored -> true, ignored -> 64);
    }

    private static ReimbursementCapacityPlanner.Result capacity(
            ReimbursementPlan plan,
            int staffSlots,
            Map<String, Integer> players,
            Map<ContainerTarget, Integer> containers
    ) {
        return ReimbursementCapacityPlanner.simulate(
                plan, staffSlots, players, containers, item -> (item.amount() + 63) / 64);
    }

    private static ReimbursementDraft configuredDraft(ReimbursementEntry... entries) {
        ReimbursementDraft draft = new ReimbursementDraft();
        for (ReimbursementEntry entry : entries) draft.add(entry);
        if (draft.ign().isBlank()) draft.setIgn("PlanetKingH");
        draft.setDiscordUsername("planetkingh");
        draft.setDiscordId("1291511050797056044");
        draft.setReason("A reimbursement reason.");
        draft.setTicket("dev-23243");
        return draft;
    }
}
