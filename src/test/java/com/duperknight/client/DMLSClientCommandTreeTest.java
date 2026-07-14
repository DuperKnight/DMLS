package com.duperknight.client;

import com.duperknight.client.utils.CannedReplies;
import com.mojang.brigadier.tree.CommandNode;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class DMLSClientCommandTreeTest {
    @Test
    void allDmlsCommandsAreRegisteredAtTheRoot() {
        CommandNode<FabricClientCommandSource> root = new DMLSClient().buildDmlsCommand().build();
        Set<String> actual = root.getChildren().stream().map(CommandNode::getName).collect(Collectors.toSet());

        assertEquals(Set.of(
                "activity", "alerts", "alts", "brb", "cancel", "chatlog", "co", "containers", "demowave",
                "dnd", "donorpet", "dryrun", "greet", "greeter", "griefs", "help", "lands", "loc",
                "members", "prefix", "promowave", "punish", "rank", "say", "uuid", "xray"
        ), actual);
    }

    @Test
    void commandsFollowingPrefixAreNotNestedInsideIt() {
        CommandNode<FabricClientCommandSource> root = new DMLSClient().buildDmlsCommand().build();
        CommandNode<FabricClientCommandSource> prefix = root.getChild("prefix");

        assertEquals(Set.of("ign"),
                prefix.getChildren().stream().map(CommandNode::getName).collect(Collectors.toSet()));
    }

    @Test
    void greeterAndDndCanRunWithoutARequiredArgument() {
        CommandNode<FabricClientCommandSource> root = new DMLSClient().buildDmlsCommand().build();

        assertNotNull(root.getChild("greeter").getCommand());
        assertNotNull(root.getChild("dnd").getCommand());
    }

    @Test
    void dangerousAndLongRunningCommandsExposeConfirmationAndCancellationBranches() {
        CommandNode<FabricClientCommandSource> root = new DMLSClient().buildDmlsCommand().build();

        assertChildren(root.getChild("xray"), Set.of("confirm", "cancel", "ign"));
        assertChildren(root.getChild("promowave"), Set.of("confirm", "cancel", "rank"));
        assertChildren(root.getChild("demowave"), Set.of("confirm", "cancel", "rank"));
        assertNotNull(root.getChild("xray").getChild("confirm").getCommand());
        assertNotNull(root.getChild("promowave").getChild("confirm").getCommand());
        assertNotNull(root.getChild("demowave").getChild("confirm").getCommand());
        assertChildren(root.getChild("xray").getChild("confirm"), Set.of());
        assertChildren(root.getChild("promowave").getChild("confirm"), Set.of());
        assertChildren(root.getChild("demowave").getChild("confirm"), Set.of());
        assertChildren(root.getChild("activity"), Set.of("cancel", "igns"));
        assertChildren(root.getChild("containers"), Set.of("cancel", "ign"));
        assertChildren(root.getChild("griefs"), Set.of("cancel", "ign"));
        assertNotNull(root.getChild("cancel").getCommand());
    }

    @Test
    void stateAndUtilityCommandsExposeTheirDocumentedBranches() {
        CommandNode<FabricClientCommandSource> root = new DMLSClient().buildDmlsCommand().build();

        assertChildren(root.getChild("loc"), Set.of("list", "save", "tp", "del"));
        assertChildren(root.getChild("alerts"), Set.of("on", "off", "reload"));
        assertChildren(root.getChild("greeter"), Set.of("on", "off"));
        assertChildren(root.getChild("dryrun"), Set.of("on", "off"));
        assertChildren(root.getChild("brb"), Set.of("off", "duration"));
        assertChildren(root.getChild("dnd"), Set.of("on", "off"));
        assertChildren(root.getChild("say"), Set.copyOf(CannedReplies.names()));
        assertChildren(root.getChild("rank"), Set.of());
    }

    @Test
    void readmeDocumentsExactlyTheCanonicalRootSurface() throws Exception {
        CommandNode<FabricClientCommandSource> root = new DMLSClient().buildDmlsCommand().build();
        Set<String> registered = root.getChildren().stream()
                .map(CommandNode::getName).collect(Collectors.toSet());

        String readme = Files.readString(Path.of("README.md"));
        Matcher snippets = Pattern.compile("`/dmls(?:\\s+([a-z]+))?[^`]*`").matcher(readme);
        Set<String> documented = new HashSet<>();
        while (snippets.find()) {
            if (snippets.group(1) != null) documented.add(snippets.group(1));
        }

        assertEquals(registered, documented);
    }

    private static void assertChildren(CommandNode<FabricClientCommandSource> node, Set<String> expected) {
        assertNotNull(node);
        assertEquals(expected, node.getChildren().stream().map(CommandNode::getName).collect(Collectors.toSet()));
    }
}
