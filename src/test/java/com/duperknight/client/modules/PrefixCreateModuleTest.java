package com.duperknight.client.modules;

import com.duperknight.client.session.CommandDispatch;
import com.duperknight.client.session.PacedCommandSequence;
import com.duperknight.client.session.ResponseStatus;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PrefixCreateModuleTest {
    private static final String IGN = "DuperKnight";
    private static final String LIMIT = "10";
    private static final String PREFIX_ID = "staff";

    @Test
    void requestFreezesTheExactReviewedCommandPlan() {
        PrefixCreateModule.CreateRequest request = PrefixCreateModule.createRequest(
                IGN, LIMIT, PREFIX_ID, "&aStaff");

        assertEquals(List.of(
                "prefix create staff &aStaff",
                "prefix x setlimit staff 10",
                "prefix x setmanager staff DuperKnight",
                "prefix x info staff"
        ), request.commands());
        assertThrows(UnsupportedOperationException.class, () -> request.commands().add("prefix delete staff"));
    }

    @Test
    void unrelatedMessagesNeverAdvanceAndEachConfirmedResponseDispatchesTheNextStep() {
        List<String> dispatched = new ArrayList<>();
        var sequence = sequence(dispatched, CommandDispatch.SENT);

        assertEquals(PacedCommandSequence.State.AWAITING_RESPONSE, sequence.start());
        assertEquals(List.of("prefix create staff &aStaff"), dispatched);
        assertEquals(ResponseStatus.UNRELATED, sequence.accept("Someone joined the server"));
        assertEquals(0, sequence.currentIndex());

        assertEquals(ResponseStatus.CONFIRMED,
                sequence.accept("Successfully created prefix Staff (ID: staff)."));
        assertEquals(ResponseStatus.CONFIRMED,
                sequence.accept("Set the limit of staff to 10."));
        assertEquals(ResponseStatus.CONFIRMED,
                sequence.accept("Successfully set the manager of staff to DuperKnight."));
        assertEquals(ResponseStatus.CONFIRMED,
                sequence.accept("Manager: DuperKnight (f1a1f93b-64bd-4ea5-b40b-a71a335c064b)"));

        assertEquals(PacedCommandSequence.State.COMPLETED, sequence.state());
        assertEquals(List.of(
                "prefix create staff &aStaff",
                "prefix x setlimit staff 10",
                "prefix x setmanager staff DuperKnight",
                "prefix x info staff"
        ), dispatched);
        assertEquals(4, sequence.confirmedCount());
        assertEquals(0, sequence.simulatedCount());
    }

    @Test
    void knownManagerRejectionStopsBeforeTheVerificationCommand() {
        List<String> dispatched = new ArrayList<>();
        var sequence = sequence(dispatched, CommandDispatch.SENT);

        sequence.start();
        sequence.accept("Successfully created prefix Staff (ID: staff).");
        sequence.accept("Set the limit of staff to 10.");
        assertEquals(ResponseStatus.REJECTED,
                sequence.accept("Unable to get that player's profile."));

        assertEquals(PacedCommandSequence.State.REJECTED, sequence.state());
        assertEquals(List.of(
                "prefix create staff &aStaff",
                "prefix x setlimit staff 10",
                "prefix x setmanager staff DuperKnight"
        ), dispatched);
    }

    @Test
    void dryRunProcessesEveryCommandWithoutClaimingServerConfirmation() {
        List<String> dispatched = new ArrayList<>();
        var sequence = sequence(dispatched, CommandDispatch.SIMULATED);

        assertEquals(PacedCommandSequence.State.COMPLETED, sequence.start());
        assertEquals(4, dispatched.size());
        assertEquals(4, sequence.processedCount());
        assertEquals(4, sequence.simulatedCount());
        assertEquals(0, sequence.confirmedCount());
    }

    @Test
    void responseTimeoutPreservesTheReviewedTwoHundredTickWait() {
        var sequence = sequence(new ArrayList<>(), CommandDispatch.SENT);
        sequence.start();

        for (int tick = 0; tick < 200; tick++) {
            assertEquals(PacedCommandSequence.State.AWAITING_RESPONSE, sequence.tick());
        }
        assertEquals(PacedCommandSequence.State.TIMED_OUT, sequence.tick());
    }

    private static PacedCommandSequence<?> sequence(
            List<String> dispatched,
            CommandDispatch result
    ) {
        PrefixCreateModule.CreateRequest request = PrefixCreateModule.createRequest(
                IGN, LIMIT, PREFIX_ID, "&aStaff");
        return PrefixCreateModule.createSequence(request, command -> {
            dispatched.add(command);
            return result;
        });
    }
}
