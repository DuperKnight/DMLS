package com.duperknight.client.reimbursement;

import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReimbursementDraftTest {
    @Test
    void automaticIgnsAreUniqueAndKeepEntryOrder() {
        ReimbursementDraft draft = new ReimbursementDraft();
        draft.add(playerMoney("PlanetKingH"));
        draft.add(playerItem("MagglyClaw", 1));
        draft.add(playerMoney("PlanetKingH"));

        assertEquals("PlanetKingH, MagglyClaw", draft.ign());
    }

    @Test
    void manualIgnEditIsNotOverwrittenByLaterEntryChanges() {
        ReimbursementDraft draft = new ReimbursementDraft();
        draft.add(playerMoney("PlanetKingH"));
        draft.setIgn("ManualName");
        draft.add(playerItem("MagglyClaw", 1));

        assertEquals("ManualName", draft.ign());
    }

    @Test
    void brokenContainerTargetsAreRemovedFromEveryEntry() {
        ContainerTarget kept = new ContainerTarget("minecraft:overworld", new BlockPos(1, 2, 3));
        ContainerTarget broken = new ContainerTarget("minecraft:overworld", new BlockPos(4, 5, 6));
        ReimbursementDraft draft = new ReimbursementDraft();
        draft.add(ItemEntry.empty().withContainers(List.of(kept, broken)));

        assertEquals(1, draft.removeContainersIf(target -> target.equals(broken)));
        assertEquals(List.of(kept), ((ItemEntry) draft.entries().getFirst()).containers());
    }

    private static MoneyEntry playerMoney(String ign) {
        return new MoneyEntry(new BigDecimal("10.00"), Destination.PLAYER, ign);
    }

    private static ItemEntry playerItem(String ign, int amount) {
        return new ItemEntry(Identifier.ofVanilla("stone"), "", List.of(), amount, Map.of(),
                Destination.PLAYER, ign, List.of());
    }
}
