package com.duperknight.client.modules;

import com.duperknight.client.instaban.InstaBanLookupOutcome;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DoNotInstaBanModuleTest {
    @Test
    void onlyAuthenticationFailuresTriggerLinkRevalidation() {
        assertTrue(DoNotInstaBanModule.isAuthenticationFailure(InstaBanLookupOutcome.Type.NOT_LINKED));
        assertTrue(DoNotInstaBanModule.isAuthenticationFailure(InstaBanLookupOutcome.Type.INVALID_TOKEN));
        assertTrue(DoNotInstaBanModule.isAuthenticationFailure(InstaBanLookupOutcome.Type.AUTHORIZATION_STALE));

        assertFalse(DoNotInstaBanModule.isAuthenticationFailure(InstaBanLookupOutcome.Type.RATE_LIMITED));
        assertFalse(DoNotInstaBanModule.isAuthenticationFailure(InstaBanLookupOutcome.Type.TEMPORARY_ERROR));
        assertFalse(DoNotInstaBanModule.isAuthenticationFailure(InstaBanLookupOutcome.Type.SUCCESS));
    }
}
