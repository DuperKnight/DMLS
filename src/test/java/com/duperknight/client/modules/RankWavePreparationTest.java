package com.duperknight.client.modules;

import org.junit.jupiter.api.Test;

import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class RankWavePreparationTest {
    @Test
    void promotionFreezesDedupedNamesAndAddBeforeRemoveCommands() {
        var request = PromoWaveModule.prepare(" MOD ", "Alice, bob alice invalid-name");

        assertTrue(request.valid());
        assertEquals("mod", request.rank());
        assertEquals(java.util.List.of("Alice", "bob"), request.usernames());
        assertEquals(java.util.List.of("invalid-name"), request.skipped());
        assertEquals(java.util.List.of(
                "lp user Alice parent add mod",
                "lp user Alice parent remove helper",
                "lp user bob parent add mod",
                "lp user bob parent remove helper"), request.commands());
    }

    @Test
    void demotionBuildsOneExactRemovalPerPlayer() {
        var request = DemoWaveModule.prepare("SR-MOD", "Alice Bob");

        assertTrue(request.valid());
        assertEquals(java.util.List.of(
                "lp user Alice parent remove sr-mod",
                "lp user Bob parent remove sr-mod"), request.commands());
    }

    @Test
    void rejectsUnknownEmptyAndOversizedWaves() {
        assertEquals(PromoWaveModule.PreparationStatus.UNKNOWN_RANK,
                PromoWaveModule.prepare("owner", "Alice").status());
        assertEquals(DemoWaveModule.PreparationStatus.EMPTY,
                DemoWaveModule.prepare("helper", "not-valid!").status());

        String names = IntStream.range(0, PromoWaveModule.MAX_PLAYERS + 1)
                .mapToObj(index -> "User" + index)
                .reduce((left, right) -> left + " " + right)
                .orElseThrow();
        assertEquals(PromoWaveModule.PreparationStatus.TOO_MANY,
                PromoWaveModule.prepare("helper", names).status());
        assertEquals(DemoWaveModule.PreparationStatus.TOO_MANY,
                DemoWaveModule.prepare("helper", names).status());
    }
}
