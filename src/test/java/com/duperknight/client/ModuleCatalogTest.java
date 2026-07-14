package com.duperknight.client;

import com.duperknight.client.modules.StaffRank;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModuleCatalogTest {
    private static final Path MODULES = Path.of("src/main/java/com/duperknight/client/modules");

    @Test
    void menuCatalogContainsEveryReviewedModuleOnceInTheIntendedOrder() throws Exception {
        String clientSource = Files.readString(Path.of(
                "src/main/java/com/duperknight/client/DMLSClient.java"));
        Matcher registrations = Pattern.compile("new\\s+([A-Za-z0-9]+Module)\\s*\\(\\)")
                .matcher(clientSource);
        java.util.ArrayList<String> actual = new java.util.ArrayList<>();
        while (registrations.find()) actual.add(registrations.group(1));

        assertEquals(List.of(
                "CheckLandsModule",
                "CheckMembersModule",
                "CheckAltsModule",
                "XrayRollbackModule",
                "PrefixCreateModule",
                "DonorPetModule",
                "EventProtectModule",
                "PromoWaveModule",
                "DemoWaveModule",
                "UuidLookupModule",
                "ChatAlertsModule",
                "ChatSpamMuteModule",
                "AwayModule",
                "ActivityWaveModule",
                "ChatReplayModule",
                "GreeterModule",
                "LocationsModule",
                "CoreProtectBuilderModule",
                "ContainerScanModule",
                "GriefScanModule",
                "PunishmentHelperModule"
        ), actual);
        assertEquals(actual.size(), actual.stream().distinct().count());
    }

    @Test
    void menuCatalogKeepsTheReviewedRankMatrix() throws Exception {
        Map<String, StaffRank> expected = new LinkedHashMap<>();
        expected.put("CheckLandsModule", StaffRank.MODERATOR);
        expected.put("CheckMembersModule", StaffRank.MODERATOR);
        expected.put("CheckAltsModule", StaffRank.MODERATOR);
        expected.put("XrayRollbackModule", StaffRank.SENIOR_MODERATOR);
        expected.put("PrefixCreateModule", StaffRank.SUPPORT);
        expected.put("DonorPetModule", StaffRank.ADMIN);
        expected.put("EventProtectModule", StaffRank.MODERATOR);
        expected.put("PromoWaveModule", StaffRank.ADMIN);
        expected.put("DemoWaveModule", StaffRank.ADMIN);
        expected.put("UuidLookupModule", StaffRank.HELPER);
        expected.put("ChatAlertsModule", StaffRank.HELPER);
        expected.put("ChatSpamMuteModule", StaffRank.ADMIN);
        expected.put("AwayModule", StaffRank.HELPER);
        expected.put("ActivityWaveModule", StaffRank.ADMIN);
        expected.put("ChatReplayModule", StaffRank.HELPER);
        expected.put("GreeterModule", StaffRank.HELPER);
        expected.put("LocationsModule", StaffRank.HELPER);
        expected.put("CoreProtectBuilderModule", StaffRank.SENIOR_MODERATOR);
        expected.put("ContainerScanModule", StaffRank.MODERATOR);
        expected.put("GriefScanModule", StaffRank.MODERATOR);
        expected.put("PunishmentHelperModule", StaffRank.HELPER);

        for (Map.Entry<String, StaffRank> entry : expected.entrySet()) {
            String source = Files.readString(MODULES.resolve(entry.getKey() + ".java"));
            if (source.contains("extends AbstractCoreProtectScanModule")) {
                source = Files.readString(MODULES.resolve("session/AbstractCoreProtectScanModule.java"));
            }
            Matcher rank = Pattern.compile("super\\s*\\(\\s*StaffRank\\.([A-Z_]+)\\s*(?:,|\\))")
                    .matcher(source);
            assertTrue(rank.find(), entry.getKey() + " does not declare a minimum rank");
            assertEquals(entry.getValue().name(), rank.group(1), entry.getKey());
        }
    }
}
