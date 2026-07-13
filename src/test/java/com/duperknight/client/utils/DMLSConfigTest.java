package com.duperknight.client.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

class DMLSConfigTest {
    @Test
    void sessionOnlyAwayStateIsNeverWrittenToProperties() {
        assertFalse(DMLSConfig.propertiesSnapshot().containsKey("awayDnd"));
    }
}
