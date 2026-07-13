package com.duperknight.client.modules;

import org.junit.jupiter.api.Test;

import com.duperknight.client.utils.MojangProfileLookup;

import java.net.ConnectException;
import java.net.http.HttpTimeoutException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UuidLookupModuleTest {
    @Test void parsesUniqueCommaOrWhitespaceSeparatedUsernames() {
        UuidLookupModule.ParsedInput parsed = UuidLookupModule.parseInput("Alice, Bob alice");
        assertEquals(UuidLookupModule.InputStatus.VALID, parsed.status());
        assertEquals(java.util.List.of("Alice", "Bob"), parsed.usernames());
    }

    @Test void rejectsInvalidOrOversizedBatches() {
        assertEquals(UuidLookupModule.InputStatus.INVALID, UuidLookupModule.parseInput("Alice bad-name").status());
        assertEquals(UuidLookupModule.InputStatus.TOO_MANY,
                UuidLookupModule.parseInput("a01 a02 a03 a04 a05 a06 a07 a08 a09 a10 a11").status());
    }

    @Test void usesInjectedLookupAndClearsActiveStateAfterSuccess() {
        AtomicReference<List<String>> requested = new AtomicReference<>();
        UuidLookupModule module = new UuidLookupModule(usernames -> {
            requested.set(usernames);
            return CompletableFuture.completedFuture(MojangProfileLookup.BatchResult.success(List.of(
                    new MojangProfileLookup.Entry("Alice", "Alice", "00000000-0000-0000-0000-000000000000", true))));
        });
        AtomicReference<MojangProfileLookup.BatchResult> delivered = new AtomicReference<>();

        assertEquals(UuidLookupModule.InputStatus.VALID,
                module.submitToScreen(null, "Alice", delivered::set).status());
        assertEquals(List.of("Alice"), requested.get());
        assertEquals(MojangProfileLookup.Status.SUCCESS, delivered.get().status());
        assertFalse(module.isLookupActive());
    }

    @Test void distinguishesTimeoutAndNetworkFailuresWithoutLiveHttp() {
        UuidLookupModule timeout = new UuidLookupModule(usernames ->
                CompletableFuture.failedFuture(new HttpTimeoutException("slow")));
        AtomicReference<MojangProfileLookup.BatchResult> timeoutResult = new AtomicReference<>();
        timeout.submitToScreen(null, "Alice", timeoutResult::set);
        assertEquals(MojangProfileLookup.Status.TIMEOUT, timeoutResult.get().status());
        assertFalse(timeout.isLookupActive());

        UuidLookupModule network = new UuidLookupModule(usernames ->
                CompletableFuture.failedFuture(new ConnectException("offline")));
        AtomicReference<MojangProfileLookup.BatchResult> networkResult = new AtomicReference<>();
        network.submitToScreen(null, "Alice", networkResult::set);
        assertEquals(MojangProfileLookup.Status.NETWORK_ERROR, networkResult.get().status());
        assertFalse(network.isLookupActive());
    }

    @Test void rejectsOverlapAndCleansUpAfterExceptionalServicePaths() {
        CompletableFuture<MojangProfileLookup.BatchResult> pending = new CompletableFuture<>();
        UuidLookupModule module = new UuidLookupModule(usernames -> pending);

        assertEquals(UuidLookupModule.InputStatus.VALID,
                module.submitToScreen(null, "Alice", ignored -> { }).status());
        assertTrue(module.isLookupActive());
        assertEquals(UuidLookupModule.InputStatus.ACTIVE,
                module.submitToScreen(null, "Bob", ignored -> { }).status());

        pending.complete(MojangProfileLookup.BatchResult.rateLimited());
        assertFalse(module.isLookupActive());

        UuidLookupModule throwing = new UuidLookupModule(usernames -> {
            throw new IllegalStateException("boom");
        });
        AtomicReference<MojangProfileLookup.BatchResult> thrownResult = new AtomicReference<>();
        assertEquals(UuidLookupModule.InputStatus.VALID,
                throwing.submitToScreen(null, "Alice", thrownResult::set).status());
        assertEquals(MojangProfileLookup.Status.NETWORK_ERROR, thrownResult.get().status());
        assertFalse(throwing.isLookupActive());

        UuidLookupModule consumerFailure = new UuidLookupModule(usernames ->
                CompletableFuture.completedFuture(MojangProfileLookup.BatchResult.rateLimited()));
        consumerFailure.submitToScreen(null, "Alice", ignored -> { throw new IllegalStateException("consumer"); });
        assertFalse(consumerFailure.isLookupActive());
    }
}
