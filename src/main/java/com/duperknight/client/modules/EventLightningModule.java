package com.duperknight.client.modules;

import com.duperknight.client.gui.modules.EventLightningScreen;
import com.duperknight.client.session.CommandDispatch;
import com.duperknight.client.utils.ChatUtils;
import com.duperknight.client.utils.ClientUtils;
import com.duperknight.client.utils.ServerGuard;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class EventLightningModule extends DMLSModule {
    private static final String PREFIX = "§8[§6DMLS - Lightning§8] §7";

    // Bolts hurt and ignite within a few blocks, so keep everything well clear of the caller.
    public static final int MIN_RADIUS = 5;
    public static final int MAX_RADIUS = 18;
    /** Total reach of a line, plus arm, cross arm or spiral, measured from the caller. */
    public static final int MIN_LENGTH = 5;
    public static final int MAX_LENGTH = 64;
    /** The first bolt of a line, arm or spiral lands this many blocks from the caller. */
    public static final int LINE_START = 5;
    public static final int MIN_POINTS = 2;
    // Each bolt is a separate command, so cap the total to avoid command-spam kicks.
    public static final int MAX_POINTS = 256;
    private static final int ARM_COUNT = 4;
    /** How many full turns the spiral makes between its first and last bolt. */
    private static final int SPIRAL_TURNS = 2;

    public enum Shape {
        RING(MAX_POINTS),
        LINE(MAX_POINTS),
        /** A + made of four arms. Points are per arm, so the cap is a quarter of the total. */
        PLUS(MAX_POINTS / ARM_COUNT),
        /** A cross (X) made of four diagonal arms. Points are per arm, so the cap is a quarter of the total. */
        CROSS(MAX_POINTS / ARM_COUNT),
        /** A spiral winding outward from the caller. Points are the total bolts along the spiral. */
        SPIRAL(MAX_POINTS);

        private final int maxPoints;

        Shape(int maxPoints) {
            this.maxPoints = maxPoints;
        }

        public int maxPoints() {
            return maxPoints;
        }
    }

    public enum RunResult {
        SENT,
        SIMULATED,
        INVALID_RADIUS,
        INVALID_LENGTH,
        INVALID_POINTS,
        RANK_BLOCKED,
        SERVER_BLOCKED
    }

    public EventLightningModule() {
        super(StaffDepartment.EVENTS);
    }

    @Override
    public Component displayName() {
        return Component.translatable("dmls.module.event_lightning_ring.name");
    }

    @Override
    public ItemStack icon() {
        return new ItemStack(Items.LIGHTNING_ROD.waxed().unaffected());
    }

    @Override
    public List<Component> description() {
        return List.of(Component.translatable("dmls.module.event_lightning_ring.description"));
    }

    @Override
    public ModuleCategory category() {
        return ModuleCategory.EVENTS;
    }

    @Override
    public void openScreen(Minecraft client, Screen parent) {
        client.gui.setScreen(new EventLightningScreen(parent, this));
    }

    @Override
    public void register() {
        // No event listeners needed
    }

    /**
     * Runs the chosen shape. For {@link Shape#RING} the size is the radius; for every other shape it
     * is the total reach from the caller. Points are per arm for {@link Shape#PLUS} and {@link Shape#CROSS}.
     */
    public RunResult run(Minecraft client, Shape shape, String sizeInput, String pointsInput) {
        boolean ring = shape == Shape.RING;
        Optional<Integer> validatedSize = ring ? validateRadius(sizeInput) : validateLength(sizeInput);
        if (validatedSize.isEmpty()) {
            return ring ? RunResult.INVALID_RADIUS : RunResult.INVALID_LENGTH;
        }
        Optional<Integer> validatedPoints = validatePoints(pointsInput, shape);
        if (validatedPoints.isEmpty()) {
            return RunResult.INVALID_POINTS;
        }

        if (!hasRequiredRank(client)) {
            return RunResult.RANK_BLOCKED;
        }

        int size = validatedSize.get();
        int points = validatedPoints.get();
        List<String> commands = switch (shape) {
            case RING -> buildRingCommands(size, points);
            case LINE -> buildLineCommands(size, points);
            case PLUS -> buildPlusCommands(size, points);
            case CROSS -> buildCrossCommands(size, points);
            case SPIRAL -> buildSpiralCommands(size, points);
        };

        boolean simulated = false;
        for (String command : commands) {
            CommandDispatch dispatch = ClientUtils.dispatchCommand(client, command);
            if (dispatch == CommandDispatch.BLOCKED) {
                ServerGuard.GuardResult guard = ServerGuard.check(client);
                ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.server_guard.blocked",
                        guard.reason(), guard.address());
                return RunResult.SERVER_BLOCKED;
            }
            simulated |= dispatch == CommandDispatch.SIMULATED;
        }
        return simulated ? RunResult.SIMULATED : RunResult.SENT;
    }

    static List<String> buildRingCommands(int radius, int points) {
        List<String> commands = new ArrayList<>(points);
        for (int i = 0; i < points; i++) {
            double yaw = 360.0 * i / points;
            // Locale.ROOT so the yaw never gets a comma decimal separator.
            commands.add(String.format(Locale.ROOT,
                    "execute at @s rotated %.2f 0 positioned ^ ^ ^%d run summon lightning_bolt",
                    yaw, radius));
        }
        return commands;
    }

    /** Evenly spaced bolts from {@link #LINE_START} out to {@code length}, along the caller's facing. */
    static List<String> buildLineCommands(int length, int points) {
        List<String> commands = new ArrayList<>(points);
        addArm(commands, 0, length, points);
        return commands;
    }

    /**
     * A + sign: four arms (forward, right, back, left of where the caller is facing), each with
     * {@code pointsPerArm} bolts. The centre is left empty so the caller is never struck.
     */
    static List<String> buildPlusCommands(int length, int pointsPerArm) {
        return buildArms(0, length, pointsPerArm);
    }

    /**
     * A cross (X): four diagonal arms (45, 135, 225 and 315 degrees from where the caller is facing),
     * each with {@code pointsPerArm} bolts. The centre is left empty so the caller is never struck.
     */
    static List<String> buildCrossCommands(int length, int pointsPerArm) {
        return buildArms(45, length, pointsPerArm);
    }

    /**
     * A spiral winding out from the caller. Bolt {@code i} sits at a distance that grows evenly from
     * {@link #LINE_START} to {@code length}, while the angle turns {@link #SPIRAL_TURNS} full circles
     * from the caller's facing. The centre is left empty so the caller is never struck.
     */
    static List<String> buildSpiralCommands(int length, int points) {
        List<String> commands = new ArrayList<>(points);
        for (int i = 0; i < points; i++) {
            double progress = (double) i / (points - 1);
            double distance = LINE_START + (length - LINE_START) * progress;
            double yawOffset = 360.0 * SPIRAL_TURNS * progress;
            commands.add(String.format(Locale.ROOT,
                    "execute at @s rotated ~%.2f 0 positioned ^ ^ ^%.2f run summon lightning_bolt",
                    yawOffset, distance));
        }
        return commands;
    }

    private static List<String> buildArms(int firstYawOffset, int length, int pointsPerArm) {
        List<String> commands = new ArrayList<>(pointsPerArm * ARM_COUNT);
        for (int arm = 0; arm < ARM_COUNT; arm++) {
            addArm(commands, firstYawOffset + 90 * arm, length, pointsPerArm);
        }
        return commands;
    }

    private static void addArm(List<String> commands, int yawOffset, int length, int points) {
        double step = (double) (length - LINE_START) / (points - 1);
        for (int i = 0; i < points; i++) {
            double distance = LINE_START + step * i;
            // "rotated ~<offset> 0" keeps the caller's facing, turns it by the offset, and flattens pitch.
            commands.add(String.format(Locale.ROOT,
                    "execute at @s rotated ~%d 0 positioned ^ ^ ^%.2f run summon lightning_bolt",
                    yawOffset, distance));
        }
    }

    public static Optional<Integer> validateRadius(String radiusInput) {
        return parseBoundedInt(radiusInput, MIN_RADIUS, MAX_RADIUS);
    }

    public static Optional<Integer> validateLength(String lengthInput) {
        return parseBoundedInt(lengthInput, MIN_LENGTH, MAX_LENGTH);
    }

    public static Optional<Integer> validatePoints(String pointsInput, Shape shape) {
        return parseBoundedInt(pointsInput, MIN_POINTS, shape.maxPoints());
    }

    private static Optional<Integer> parseBoundedInt(String input, int minimum, int maximum) {
        if (input == null) {
            return Optional.empty();
        }
        try {
            int value = Integer.parseInt(input.strip());
            return value >= minimum && value <= maximum ? Optional.of(value) : Optional.empty();
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
    }
}