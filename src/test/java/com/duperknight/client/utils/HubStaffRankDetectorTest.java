package com.duperknight.client.utils;

import com.duperknight.client.modules.StaffRank;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HubStaffRankDetectorTest {
    @Test
    void detectsRecognizedRankFromHubSidebar() {
        HubStaffRankDetector.Detection result = HubStaffRankDetector.detect("Hub", List.of(
                "Name: DuperKnight",
                "Rank: ADMIN",
                "Coordinates:",
                "-1, 83, -19",
                "Current Lobby:",
                "HUB"
        ));

        assertEquals(HubStaffRankDetector.Status.STAFF, result.status());
        assertEquals(StaffRank.ADMIN, result.rank().orElseThrow());
    }

    @Test
    void acceptsTwoLineLobbyMarkerWhenTitleIsDifferent() {
        HubStaffRankDetector.Detection result = HubStaffRankDetector.detect("Stoneworks", List.of(
                "Rank: SR MOD",
                "Current Lobby:",
                "HUB"
        ));

        assertEquals(HubStaffRankDetector.Status.STAFF, result.status());
        assertEquals(StaffRank.SENIOR_MODERATOR, result.rank().orElseThrow());
    }

    @Test
    void unknownHubRankIsExplicitlyNonStaff() {
        HubStaffRankDetector.Detection result = HubStaffRankDetector.detect("§bHub", List.of("Rank: KNIGHT"));

        assertEquals(HubStaffRankDetector.Status.NON_STAFF, result.status());
        assertTrue(result.rank().isEmpty());
        assertEquals("KNIGHT", result.rawRank());
    }

    @Test
    void backendScoreboardCannotOverwriteHubRank() {
        HubStaffRankDetector.Detection result = HubStaffRankDetector.detect("Abexilas", List.of("Rank: MEMBER"));

        assertEquals(HubStaffRankDetector.Status.NOT_HUB, result.status());
    }

    @Test
    void incompleteHubScoreboardWaitsForRank() {
        HubStaffRankDetector.Detection result = HubStaffRankDetector.detect("Hub", List.of("Current Lobby: HUB"));

        assertEquals(HubStaffRankDetector.Status.RANK_MISSING, result.status());
    }

    @Test
    void missingPreviousRankProducesWelcomeAnnouncement() {
        assertEquals(HubStaffRankDetector.AnnouncementKind.WELCOME,
                HubStaffRankDetector.announcementKind(false, StaffRank.NONE, StaffRank.HELPER));
        assertEquals(HubStaffRankDetector.AnnouncementKind.WELCOME,
                HubStaffRankDetector.announcementKind(true, StaffRank.NONE, StaffRank.ADMIN));
    }

    @Test
    void onlyAnUpwardRankChangeProducesPromotionAnnouncement() {
        assertEquals(HubStaffRankDetector.AnnouncementKind.PROMOTION,
                HubStaffRankDetector.announcementKind(true, StaffRank.MODERATOR, StaffRank.ADMIN));
        assertEquals(HubStaffRankDetector.AnnouncementKind.NONE,
                HubStaffRankDetector.announcementKind(true, StaffRank.ADMIN, StaffRank.ADMIN));
        assertEquals(HubStaffRankDetector.AnnouncementKind.NONE,
                HubStaffRankDetector.announcementKind(true, StaffRank.ADMIN, StaffRank.MODERATOR));
    }
}
