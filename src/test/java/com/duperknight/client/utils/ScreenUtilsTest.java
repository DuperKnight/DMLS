package com.duperknight.client.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScreenUtilsTest {
    @Test
    void onlyANewSyncIdCanSatisfyAMenuCommandQuery() {
        assertFalse(ScreenUtils.isReplacementSyncId(7, 7));
        assertTrue(ScreenUtils.isReplacementSyncId(7, 8));
        assertTrue(ScreenUtils.isReplacementSyncId(-1, 1));
    }
}
