package com.duperknight.client.utils;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.net.ConnectException;
import java.net.http.HttpTimeoutException;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MojangProfileLookupTest {
    @Test void parsesBulkProfilesInRequestedOrderAndMarksMissingNames() {
        MojangProfileLookup.BatchResult result = MojangProfileLookup.parseSuccess("""
                [{"id":"f1a1f93b64bd4ea5b40ba71a335c064b","name":"DuperKnight"}]
                """, List.of("MissingUser", "duperknight"));

        assertEquals(MojangProfileLookup.Status.SUCCESS, result.status());
        assertEquals(2, result.entries().size());
        assertFalse(result.entries().get(0).found());
        assertEquals("DuperKnight", result.entries().get(1).username());
        assertEquals("f1a1f93b-64bd-4ea5-b40b-a71a335c064b", result.entries().get(1).uuid());
    }

    @Test void rejectsMalformedUnexpectedOrDuplicateProfiles() {
        assertEquals(MojangProfileLookup.Status.MALFORMED_RESPONSE,
                MojangProfileLookup.parseSuccess("{}", List.of("DuperKnight")).status());
        assertEquals(MojangProfileLookup.Status.MALFORMED_RESPONSE,
                MojangProfileLookup.parseSuccess("[{\"id\":\"bad\",\"name\":\"DuperKnight\"}]",
                        List.of("DuperKnight")).status());
        assertEquals(MojangProfileLookup.Status.MALFORMED_RESPONSE,
                MojangProfileLookup.parseSuccess("[{\"id\":\"f1a1f93b64bd4ea5b40ba71a335c064b\",\"name\":\"OtherUser\"}]",
                        List.of("DuperKnight")).status());
        assertThrows(IllegalArgumentException.class, () -> MojangProfileLookup.hyphenateUuid("bad"));
    }

    @Test void enforcesSmallValidBatchesBeforeNetworkUse() {
        assertTrue(MojangProfileLookup.MAX_USERNAMES > 1);
        assertEquals(10, MojangProfileLookup.MAX_USERNAMES);
    }

    @Test void classifiesExceptionalCompletionCauses() {
        assertEquals(MojangProfileLookup.Status.TIMEOUT,
                MojangProfileLookup.failureResult(new CompletionException(new HttpTimeoutException("slow"))).status());
        assertEquals(MojangProfileLookup.Status.NETWORK_ERROR,
                MojangProfileLookup.failureResult(new ConnectException("offline")).status());
    }
}
