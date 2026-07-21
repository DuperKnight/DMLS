package com.duperknight.client.accountlink;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DiscordLinkServiceTest {
    private static final String TOKEN = "dmls_abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQ";
    private static final UUID MINECRAFT_UUID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

    @Test
    void parsesSuccessfulFunctionExecution() {
        JsonObject functionResponse = new JsonObject();
        functionResponse.addProperty("code", "ABCD-EFGH");
        functionResponse.addProperty("clientToken", TOKEN);
        functionResponse.addProperty("expiresAt", "2026-07-21T22:30:00Z");
        functionResponse.addProperty("pollAfterSeconds", 5);
        String response = executionResponse(201, functionResponse);

        DiscordLinkService.Result result = DiscordLinkService.parseExecutionResponse(201, response);

        assertTrue(result.succeeded());
        assertEquals("ABCD-EFGH", result.code());
        assertEquals(TOKEN, result.clientToken());
        assertEquals(5, result.pollAfterSeconds());
    }

    @Test
    void exposesFunctionRateLimit() {
        JsonObject functionResponse = new JsonObject();
        functionResponse.addProperty("error", "rate_limited");
        functionResponse.addProperty("message", "Too many requests.");
        String response = executionResponse(429, functionResponse);

        DiscordLinkService.Result result = DiscordLinkService.parseExecutionResponse(201, response);

        assertEquals(DiscordLinkService.Status.RATE_LIMITED, result.status());
        assertEquals("Too many requests.", result.message());
    }

    @Test
    void rejectsMalformedSecrets() {
        JsonObject functionResponse = new JsonObject();
        functionResponse.addProperty("code", "IIII-0000");
        functionResponse.addProperty("clientToken", "not-a-token");
        functionResponse.addProperty("expiresAt", "now");
        functionResponse.addProperty("pollAfterSeconds", 5);
        String response = executionResponse(201, functionResponse);

        DiscordLinkService.Result result = DiscordLinkService.parseExecutionResponse(201, response);

        assertEquals(DiscordLinkService.Status.MALFORMED_RESPONSE, result.status());
    }

    @Test
    void parsesLinkedStatus() {
        JsonObject functionResponse = new JsonObject();
        functionResponse.addProperty("status", "linked");
        functionResponse.addProperty("minecraftUuid", MINECRAFT_UUID.toString());

        DiscordLinkService.LinkStatusResult result = DiscordLinkService.parseStatusExecutionResponse(
                201, executionResponse(200, functionResponse), MINECRAFT_UUID);

        assertEquals(DiscordLinkService.LinkStatus.LINKED, result.status());
    }

    @Test
    void parsesPendingStatusAndPollDelay() {
        JsonObject functionResponse = new JsonObject();
        functionResponse.addProperty("status", "pending");
        functionResponse.addProperty("minecraftUuid", MINECRAFT_UUID.toString());
        functionResponse.addProperty("expiresAt", "2026-07-21T22:30:00Z");
        functionResponse.addProperty("pollAfterSeconds", 5);

        DiscordLinkService.LinkStatusResult result = DiscordLinkService.parseStatusExecutionResponse(
                201, executionResponse(200, functionResponse), MINECRAFT_UUID);

        assertEquals(DiscordLinkService.LinkStatus.PENDING, result.status());
        assertEquals(5, result.pollAfterSeconds());
    }

    @Test
    void mapsRevokedTokenStatus() {
        JsonObject functionResponse = new JsonObject();
        functionResponse.addProperty("error", "invalid_token");
        functionResponse.addProperty("message", "The client token is invalid or revoked.");

        DiscordLinkService.LinkStatusResult result = DiscordLinkService.parseStatusExecutionResponse(
                201, executionResponse(401, functionResponse), MINECRAFT_UUID);

        assertEquals(DiscordLinkService.LinkStatus.INVALID_TOKEN, result.status());
    }

    private static String executionResponse(int status, JsonObject functionResponse) {
        JsonObject execution = new JsonObject();
        execution.addProperty("status", "completed");
        execution.addProperty("responseStatusCode", status);
        execution.addProperty("responseBody", functionResponse.toString());
        return execution.toString();
    }
}
