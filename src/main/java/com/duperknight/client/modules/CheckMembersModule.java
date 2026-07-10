package com.duperknight.client.modules;

import com.duperknight.client.gui.CheckMembersScreen;
import com.duperknight.client.utils.ChatUtils;
import com.duperknight.client.utils.ClientUtils;
import com.duperknight.client.utils.MenuCommandQuery;
import com.duperknight.client.utils.ScreenUtils;
import com.duperknight.client.utils.TooltipUtils;
import com.duperknight.client.utils.TooltipUtils.TooltipLine;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

public final class CheckMembersModule extends DMLSModule {
    private static final String PREFIX = "§8[§6DMLS - CheckMembers§8] §7";
    private static final int PLAYER_LIST_SLOT = ScreenUtils.slotIndex(4, 2);
    private static final int MENU_TIMEOUT_TICKS = 20 * 30;
    private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9_]{1,16}");

    private CheckSession activeSession;

    public CheckMembersModule() {
        super(StaffRank.MODERATOR);
    }

    @Override
    public Text displayName() {
        return Text.literal("Check Members");
    }

    @Override
    public ItemStack icon() {
        return new ItemStack(Items.PLAYER_HEAD);
    }

    @Override
    public List<Text> description() {
        return List.of(
                Text.literal("List all members of a land grouped by rank."),
                Text.literal("Click a name in the result to check their lands.")
        );
    }

    @Override
    public void openScreen(MinecraftClient client, Screen parent) {
        client.setScreen(new CheckMembersScreen(parent, this));
    }

    @Override
    public void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
                ClientCommandManager.literal("checkmembers")
                        .then(ClientCommandManager.argument("land", StringArgumentType.greedyString())
                                .executes(context -> {
                                    submit(context.getSource().getClient(), StringArgumentType.getString(context, "land").trim());
                                    return 1;
                                }))
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (activeSession != null) {
                activeSession.tick(client);
            }
        });
    }

    /** Starts a member check for the given land. The command and GUI both call this method. */
    public void submit(MinecraftClient client, String land) {
        if (!hasRequiredRank(client)) {
            return;
        }

        if (land.isEmpty()) {
            ChatUtils.sendClientMessage(client, PREFIX + "No land name given.");
            return;
        }

        start(client, land);
    }

    private void start(MinecraftClient client, String land) {
        if (activeSession != null) {
            activeSession.cancel(client, "Started a new check for §6" + land + "§7.");
        }

        activeSession = new CheckSession(land);
        activeSession.start(client);
    }

    private static Optional<Scan> parseMembers(List<TooltipLine> tooltip) {
        boolean inPlayers = false;
        int playerPosition = 0;
        boolean truncated = false;
        List<Group> groups = new ArrayList<>();

        for (TooltipLine line : tooltip) {
            String stripped = TooltipUtils.stripListMarker(line.text());
            String lower = stripped.toLowerCase(Locale.ROOT);
            if (!inPlayers) {
                inPlayers = lower.startsWith("players");
                continue;
            }

            if (TooltipUtils.isTooltipFooter(stripped)) {
                continue;
            }

            if (stripped.equals("...")) {
                truncated = true;
                continue;
            }

            Optional<String> parsedPlayerName = line.grayUsername(USERNAME).or(() -> TooltipUtils.lastMatch(stripped, USERNAME));
            if (parsedPlayerName.isEmpty()) {
                continue;
            }

            playerPosition++;
            String playerName = parsedPlayerName.get();
            String role = stripped.substring(0, Math.max(0, stripped.lastIndexOf(playerName))).trim();
            String rankName = rankName(playerPosition, role);
            String formattedRankName = formattedRankName(playerPosition, line, playerName, rankName);
            groupFor(groups, rankName, formattedRankName).players().add(playerName);
        }

        if (!inPlayers || playerPosition == 0) {
            return Optional.empty();
        }

        return Optional.of(new Scan(groups, truncated));
    }

    private static String rankName(int playerPosition, String role) {
        if (playerPosition == 1) {
            return "Owner";
        }
        if (role.toLowerCase(Locale.ROOT).contains("admin")) {
            return "Admin";
        }
        return role.isEmpty() ? "Member/Unknown" : role;
    }

    private static String formattedRankName(int playerPosition, TooltipLine line, String playerName, String fallbackRank) {
        if (playerPosition == 1) {
            return "§4§lOwner";
        }
        if (fallbackRank.equals("Admin")) {
            return "§c§lAdmin";
        }
        if (fallbackRank.equals("Member/Unknown")) {
            return "§e§lMember/Unknown";
        }
        return line.formattedRoleBefore(playerName).orElse(fallbackRank);
    }

    private static Group groupFor(List<Group> groups, String rank, String formattedRank) {
        for (Group group : groups) {
            if (group.rank().equalsIgnoreCase(rank)) {
                return group;
            }
        }

        Group group = new Group(rank, formattedRank, new ArrayList<>());
        groups.add(group);
        return group;
    }

    private record Scan(List<Group> groups, boolean truncated) {
    }

    private record Group(String rank, String formattedRank, List<String> players) {
    }

    private final class CheckSession {
        private final String land;
        private MenuCommandQuery activeQuery;

        private CheckSession(String land) {
            this.land = land;
        }

        private void start(MinecraftClient client) {
            ChatUtils.sendClientMessage(client, PREFIX + "Checking members of §6" + land + "§7...");
            activeQuery = new MenuCommandQuery("la info " + land, land, MENU_TIMEOUT_TICKS, PLAYER_LIST_SLOT);
            activeQuery.start(client);
        }

        private void tick(MinecraftClient client) {
            if (ClientUtils.isNotConnected(client)) {
                activeSession = null;
                return;
            }

            MenuCommandQuery.TickResult tickResult = activeQuery.tick(client);
            if (tickResult.status() == MenuCommandQuery.Status.TIMED_OUT) {
                fail(client, "Timed out waiting for §6/la info " + land + "§7. Stopping.");
                return;
            }

            if (tickResult.status() == MenuCommandQuery.Status.WAITING || tickResult.result().isEmpty()) {
                return;
            }

            Optional<Scan> parsed = tickResult.result().get().tooltip(PLAYER_LIST_SLOT).flatMap(CheckMembersModule::parseMembers);
            if (parsed.isEmpty()) {
                return;
            }

            report(client, parsed.get());
            finish(client);
        }

        private void report(MinecraftClient client, Scan scan) {
            String header = PREFIX + "Land §6" + land + "§7 ";
            ChatUtils.sendClientMessage(client, header + ChatUtils.separatorForChatWidth(client, header));
            for (Group group : scan.groups()) {
                MutableText line = Text.literal(group.formattedRank() + "§r§7 (" + group.players().size() + "): ");
                for (int i = 0; i < group.players().size(); i++) {
                    if (i > 0) {
                        line.append("§7, ");
                    }
                    line.append(clickablePlayer(group.players().get(i)));
                }
                ChatUtils.sendClientMessage(client, line);
            }
            if (scan.truncated()) {
                ChatUtils.sendClientMessage(client, "§8(list truncated by the menu, some members are not shown)");
            }
            ChatUtils.sendClientMessage(client, "§7" + ChatUtils.separatorForChatWidth(client, ""));
        }

        private MutableText clickablePlayer(String playerName) {
            return Text.literal("§6" + playerName).styled(style -> style
                    .withClickEvent(new ClickEvent.RunCommand("/checklands " + playerName))
                    .withHoverEvent(new HoverEvent.ShowText(Text.literal("§7Click to run §6/checklands " + playerName))));
        }

        private void fail(MinecraftClient client, String reason) {
            ChatUtils.sendClientMessage(client, PREFIX + reason);
            finish(client);
        }

        private void cancel(MinecraftClient client, String reason) {
            ChatUtils.sendClientMessage(client, PREFIX + reason);
            finish(client);
        }

        private void finish(MinecraftClient client) {
            ScreenUtils.closeHandledScreen(client);
            if (activeSession == this) {
                activeSession = null;
            }
        }
    }
}
