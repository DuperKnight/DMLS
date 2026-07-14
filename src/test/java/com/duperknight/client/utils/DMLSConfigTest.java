package com.duperknight.client.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;

class DMLSConfigTest {
    @Test
    void sessionOnlyAwayStateIsNeverWrittenToProperties() {
        assertFalse(DMLSConfig.propertiesSnapshot().containsKey("awayDnd"));
    }

    @Test
    void staffAndDepartmentRanksArePersisted() {
        assertEquals("NONE", DMLSConfig.propertiesSnapshot().getProperty("staffRank"));
        assertEquals("NOT_ASSIGNED", DMLSConfig.propertiesSnapshot().getProperty("bansRank"));
        assertEquals("NOT_ASSIGNED", DMLSConfig.propertiesSnapshot().getProperty("eventsRank"));
        assertEquals("NOT_ASSIGNED", DMLSConfig.propertiesSnapshot().getProperty("warRank"));
    }
}
