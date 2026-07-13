package com.duperknight.client.utils;

import com.duperknight.client.session.CommandDispatch;
import com.duperknight.client.session.ConnectionSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MenuCommandQueryTest {
    @Test
    void timesOutAWaitingMenuAndKeepsTheTerminalResult() {
        FakeEnvironment environment = new FakeEnvironment();
        MenuCommandQuery query = new MenuCommandQuery("la info Test", "Test", 1, environment, 5);
        query.start(null, (client, command) -> CommandDispatch.SENT);

        assertEquals(MenuCommandQuery.Status.WAITING, query.tick(null).status());
        assertEquals(MenuCommandQuery.Status.TIMED_OUT, query.tick(null).status());
        assertEquals(MenuCommandQuery.Status.TIMED_OUT, query.tick(null).status());
    }

    @Test
    void cancelsOnSameHostReconnectIdentityChange() {
        FakeEnvironment environment = new FakeEnvironment();
        MenuCommandQuery query = new MenuCommandQuery("la info Test", "Test", 20, environment, 5);
        query.start(null, (client, command) -> CommandDispatch.SENT);

        environment.connection = ConnectionSnapshot.connected("play.stoneworks.gg", new Object());
        assertEquals(MenuCommandQuery.Status.CANCELLED, query.tick(null).status());
    }

    @Test
    void reportsSimulationWithoutWaitingForAFabricatedMenu() {
        FakeEnvironment environment = new FakeEnvironment();
        MenuCommandQuery query = new MenuCommandQuery("la info Test", "Test", 20, environment, 5);
        query.start(null, (client, command) -> CommandDispatch.SIMULATED);

        assertEquals(MenuCommandQuery.Status.SIMULATED, query.tick(null).status());
    }

    @Test
    void returnsRequestedTooltipFixtureWhenReady() {
        FakeEnvironment environment = new FakeEnvironment();
        environment.snapshot = Optional.of(new ScreenUtils.ScreenSnapshot("Test",
                List.of(new TooltipUtils.TooltipLine("Players", List.of()))));
        MenuCommandQuery query = new MenuCommandQuery("la info Test", "Test", 20, environment, 5);
        query.start(null, (client, command) -> CommandDispatch.SENT);

        MenuCommandQuery.TickResult result = query.tick(null);
        assertEquals(MenuCommandQuery.Status.READY, result.status());
        assertTrue(result.result().orElseThrow().tooltip(5).isPresent());
    }

    @Test
    void waitsForAReplacementMenuInsteadOfReadingTheAlreadyOpenOne() {
        FakeEnvironment environment = new FakeEnvironment();
        environment.requireReplacement = true;
        environment.snapshot = Optional.of(new ScreenUtils.ScreenSnapshot("Test",
                List.of(new TooltipUtils.TooltipLine("Players", List.of()))));
        MenuCommandQuery query = new MenuCommandQuery("la info Test", "Test", 20, environment, 5);
        query.start(null, (client, command) -> CommandDispatch.SENT);

        assertEquals(MenuCommandQuery.Status.WAITING, query.tick(null).status());
        environment.syncId = 2;
        assertEquals(MenuCommandQuery.Status.READY, query.tick(null).status());
    }

    @Test
    void blockedDispatchBecomesATerminalCancellation() {
        FakeEnvironment environment = new FakeEnvironment();
        MenuCommandQuery query = new MenuCommandQuery("la info Test", "Test", 20, environment, 5);
        query.start(null, (client, command) -> CommandDispatch.BLOCKED);

        assertEquals(MenuCommandQuery.Status.CANCELLED, query.tick(null).status());
        assertEquals(MenuCommandQuery.Status.CANCELLED, query.tick(null).status());
    }

    private static final class FakeEnvironment implements MenuCommandQuery.Environment {
        private final Object initialHandler = new Object();
        private ConnectionSnapshot connection = ConnectionSnapshot.connected("play.stoneworks.gg", initialHandler);
        private Optional<ScreenUtils.ScreenSnapshot> snapshot = Optional.empty();
        private int syncId = 1;
        private boolean requireReplacement;

        @Override
        public int currentSyncId(net.minecraft.client.MinecraftClient client) {
            return syncId;
        }

        @Override
        public ConnectionSnapshot connection(net.minecraft.client.MinecraftClient client) {
            return connection;
        }

        @Override
        public Optional<ScreenUtils.ScreenSnapshot> readSlot(net.minecraft.client.MinecraftClient client,
                                                             int slotIndex, String expectedTitle,
                                                             int previousSyncId, int waitTicks) {
            return requireReplacement && syncId == previousSyncId ? Optional.empty() : snapshot;
        }
    }
}
