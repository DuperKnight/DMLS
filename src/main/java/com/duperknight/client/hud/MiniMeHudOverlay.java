package com.duperknight.client.hud;

import com.duperknight.DMLS;
import com.duperknight.client.modules.MiniMeHudPreferences;
import com.duperknight.client.utils.DMLSConfig;
import com.mojang.blaze3d.platform.NativeImage;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3fc;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** Renders optional animated AdMinis that wander entirely inside the in-game HUD. */
public final class MiniMeHudOverlay {
    private static final int MINI_ME_SIZE = 44;
    private static final int SCREEN_MARGIN = 4;
    private static final float WALK_SPEED = 34.0F;
    private static final float MIN_TARGET_DISTANCE = 90.0F;
    private static final int MAX_WALK_FRAMES = 4;
    private static final long WALK_FRAME_NANOS = 190_000_000L;
    private static final float WALK_HOP_HEIGHT = 4.0F;
    private static final long RESIZE_PAUSE_NANOS = 850_000_000L;
    private static final long MIN_ARRIVAL_PAUSE_NANOS = 650_000_000L;
    private static final long ARRIVAL_PAUSE_VARIATION_NANOS = 950_000_000L;
    private static final int TARGET_ATTEMPTS = 48;
    private static final float AGGRO_APPROACH_SPEED = 42.0F;
    private static final float AGGRO_DISTANCE_FACTOR = 0.72F;
    private static final int AGGRO_HEALTH = 5;
    private static final long NORMAL_AGGRO_CHECK_MIN_NANOS = 10_000_000_000L;
    private static final long NORMAL_AGGRO_CHECK_VARIATION_NANOS = 18_000_000_000L;
    private static final float NORMAL_AGGRO_CHANCE = 0.22F;
    private static final long CHAOS_AGGRO_CHECK_MIN_NANOS = 2_000_000_000L;
    private static final long CHAOS_AGGRO_CHECK_VARIATION_NANOS = 3_000_000_000L;
    private static final float CHAOS_AGGRO_CHANCE = 1.0F;
    private static final long HIT_INTERVAL_NANOS = 650_000_000L;
    private static final long ATTACK_LUNGE_NANOS = 180_000_000L;
    private static final float ATTACK_LUNGE_DISTANCE = 4.0F;
    private static final long DAMAGE_FLASH_NANOS = 230_000_000L;
    private static final float CRITICAL_HIT_CHANCE = 0.25F;
    private static final int ATTACK_DAMAGE = 1;
    private static final int CRITICAL_HIT_DAMAGE_MULTIPLIER = 2;
    private static final int CRITICAL_HIT_PARTICLE_COUNT = 12;
    private static final long CRITICAL_HIT_PARTICLE_LIFETIME_NANOS = 450_000_000L;
    private static final Identifier CRITICAL_HIT_TEXTURE =
            Identifier.fromNamespaceAndPath("minecraft", "textures/particle/critical_hit.png");
    private static final float SNOW_MIN_RAIN_LEVEL = 0.25F;
    private static final long SNOW_AGGRO_CHECK_MIN_NANOS = 4_000_000_000L;
    private static final long SNOW_AGGRO_CHECK_VARIATION_NANOS = 6_000_000_000L;
    private static final float SNOW_FIGHT_CHANCE = 0.85F;
    private static final float SNOW_JOIN_CHANCE = 0.65F;
    private static final float CHAOS_SNOWBALL_CHANCE = 0.5F;
    private static final int SNOW_SCAN_COLUMNS = 9;
    private static final int SNOW_SCAN_ROWS = 5;
    private static final double SNOW_SCAN_RANGE = 48.0D;
    private static final long SNOW_SCAN_INTERVAL_NANOS = 500_000_000L;
    private static final int SNOW_SCAN_ENTER_HITS = 4;
    private static final int SNOW_SCAN_EXIT_HITS = 1;
    private static final int SNOWBALL_MIN_THROWS_PER_MINI = 4;
    private static final int SNOWBALL_MAX_THROWS_PER_MINI = 7;
    private static final long SNOWBALL_THROW_INTERVAL_NANOS = 900_000_000L;
    private static final long SNOWBALL_THROW_VARIATION_NANOS = 500_000_000L;
    private static final long SNOWBALL_FIRST_THROW_DELAY_NANOS = 500_000_000L;
    private static final long SNOWBALL_HOLD_WINDOW_NANOS = 450_000_000L;
    private static final long SNOW_FIGHT_MAX_NANOS = 40_000_000_000L;
    private static final float SNOWBALL_SPEED = 320.0F;
    private static final long SNOWBALL_MIN_FLIGHT_NANOS = 250_000_000L;
    private static final long SNOWBALL_MAX_FLIGHT_NANOS = 700_000_000L;
    private static final long SNOWBALL_FIGHT_END_DELAY_NANOS = SNOWBALL_MAX_FLIGHT_NANOS + 350_000_000L;
    private static final float SNOWBALL_ARC_FACTOR = 0.15F;
    private static final float SNOWBALL_MIN_ARC_HEIGHT = 6.0F;
    private static final float SNOWBALL_MAX_ARC_HEIGHT = 28.0F;
    private static final int SNOWBALL_SIZE = 8;
    private static final int SNOWBALL_OUTLINE_COLOR = 0xFFA9C4E4;
    private static final int SNOWBALL_BODY_COLOR = 0xFFFFFFFF;
    // Occasional "special" projectiles mixed into snowball fights.
    private static final float ICE_BLOCK_CHANCE = 0.12F;
    private static final float ANVIL_CHANCE = 0.05F;
    private static final int ICE_BLOCK_DAMAGE = 2;
    private static final long ANVIL_FALL_NANOS = 450_000_000L;
    private static final int ICE_OUTLINE_COLOR = 0xFF5FA8D8;
    private static final int ICE_BODY_COLOR = 0xFFBFE8FF;
    private static final int ICE_SHINE_COLOR = 0xFFFFFFFF;
    private static final int ANVIL_OUTLINE_COLOR = 0xFF1A1A1A;
    private static final int ANVIL_BODY_COLOR = 0xFF4A4A4A;
    private static final int ANVIL_SHINE_COLOR = 0xFF8C8C8C;
    private static final int ICE_SHATTER_PARTICLE_COUNT = 16;
    private static final int ANVIL_IMPACT_PARTICLE_COUNT = 20;
    private static final int SNOW_BURST_PARTICLE_COUNT = 10;
    private static final long SNOW_BURST_LIFETIME_NANOS = 400_000_000L;
    private static final long KNOCKBACK_NANOS = 240_000_000L;
    private static final float KNOCKBACK_DISTANCE_FACTOR = 0.26F;
    private static final long DEATH_ANIMATION_NANOS = 900_000_000L;
    private static final long RESPAWN_DELAY_NANOS = 5_000_000_000L;
    private static final int SMOKE_PARTICLE_COUNT = 28;
    private static final long SMOKE_LIFETIME_NANOS = 850_000_000L;
    private static final List<Identifier> SMOKE_TEXTURES = List.of(
            Identifier.fromNamespaceAndPath("minecraft", "textures/particle/big_smoke_0.png"),
            Identifier.fromNamespaceAndPath("minecraft", "textures/particle/big_smoke_1.png"),
            Identifier.fromNamespaceAndPath("minecraft", "textures/particle/big_smoke_2.png"),
            Identifier.fromNamespaceAndPath("minecraft", "textures/particle/big_smoke_3.png"),
            Identifier.fromNamespaceAndPath("minecraft", "textures/particle/big_smoke_4.png"),
            Identifier.fromNamespaceAndPath("minecraft", "textures/particle/big_smoke_5.png"),
            Identifier.fromNamespaceAndPath("minecraft", "textures/particle/big_smoke_6.png"),
            Identifier.fromNamespaceAndPath("minecraft", "textures/particle/big_smoke_7.png"),
            Identifier.fromNamespaceAndPath("minecraft", "textures/particle/big_smoke_8.png"),
            Identifier.fromNamespaceAndPath("minecraft", "textures/particle/big_smoke_9.png"),
            Identifier.fromNamespaceAndPath("minecraft", "textures/particle/big_smoke_10.png"),
            Identifier.fromNamespaceAndPath("minecraft", "textures/particle/big_smoke_11.png")
    );
    private static final Random RANDOM = new Random();
    private static final Map<MiniMeDefinition, WalkerState> STATES = new EnumMap<>(MiniMeDefinition.class);
    private static final List<SmokeParticle> SMOKE_PARTICLES = new ArrayList<>();
    private static final List<CriticalHitParticle> CRITICAL_HIT_PARTICLES = new ArrayList<>();
    private static final List<Snowball> SNOWBALLS = new ArrayList<>();
    private static final List<SnowBurstParticle> SNOW_BURST_PARTICLES = new ArrayList<>();

    private static int screenWidth = -1;
    private static int screenHeight = -1;
    private static ClientLevel activeWorld;
    private static Fight activeFight;
    private static SnowFight activeSnowFight;
    private static long nextAggroCheckAtNanos;
    private static boolean chaosMode;
    private static boolean snowing;
    private static boolean snowballFightsEnabled;
    private static boolean snowBlocksVisible;
    private static long nextSnowScanAtNanos;

    static {
        for (MiniMeDefinition miniMe : MiniMeDefinition.values()) {
            STATES.put(miniMe, new WalkerState());
        }
    }

    private MiniMeHudOverlay() {
    }

    public static void register() {
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(DMLS.MOD_ID.toLowerCase(), "walking_mini_mes"),
                (context, tickCounter) -> render(context));
        ResourceLoader.get(PackType.CLIENT_RESOURCES).registerReloadListener(
                Identifier.fromNamespaceAndPath(DMLS.MOD_ID.toLowerCase(), "mini_me_frames"),
                (ResourceManagerReloadListener) resourceManager -> invalidateFrameCache());
    }

    private static void invalidateFrameCache() {
        for (MiniMeDefinition miniMe : MiniMeDefinition.values()) {
            miniMe.invalidateFrames();
        }
    }

    private static void render(GuiGraphicsExtractor context) {
        Minecraft client = Minecraft.getInstance();
        long now = System.nanoTime();
        int width = context.guiWidth();
        int height = context.guiHeight();

        if (client.level != activeWorld) {
            activeWorld = client.level;
            resetAll();
        }

        if (width != screenWidth || height != screenHeight) {
            boolean firstSize = screenWidth < 0 || screenHeight < 0;
            int previousWidth = screenWidth;
            int previousHeight = screenHeight;
            screenWidth = width;
            screenHeight = height;
            if (!firstSize) reanchorForResize(previousWidth, previousHeight, width, height, now);
        }

        boolean visibleUnderScreen = client.gui.screen() == null
                || client.gui.screen() instanceof ChatScreen
                || client.gui.screen() instanceof AbstractContainerScreen<?>;
        if (client.level == null || client.player == null || !visibleUnderScreen
                || client.gui.hud.isHidden() || width <= 0 || height <= 0) {
            return;
        }

        int size = miniMeSize(width, height);
        Bounds bounds = bounds(width, height, size);
        MiniMeHudPreferences preferences = DMLSConfig.miniMeHudPreferences();
        if (chaosMode != preferences.chaosMode()) {
            chaosMode = preferences.chaosMode();
            scheduleNextAggroCheck(now);
        }
        List<MiniMeDefinition> enabledMiniMes = new ArrayList<>();

        for (MiniMeDefinition miniMe : MiniMeDefinition.values()) {
            WalkerState state = STATES.get(miniMe);
            if (!miniMe.enabled(preferences)) {
                state.initialized = false;
                state.resetTransientState();
                continue;
            }
            enabledMiniMes.add(miniMe);
            if (!state.initialized) spawn(state, bounds, now);
            if (state.respawnAtNanos > 0L) {
                if (now >= state.respawnAtNanos) {
                    spawn(state, bounds, now);
                    spawnSmoke(state.x, state.y, size, now);
                } else if (state.deathStartedAtNanos == 0L
                        || now >= state.deathStartedAtNanos + DEATH_ANIMATION_NANOS) {
                    continue;
                }
            }
            keepInBounds(state, bounds);
            updateKnockback(state, bounds, now);
        }

        snowballFightsEnabled = preferences.snowballFights();
        boolean snowingNow = false;
        if (preferences.snowballFights() && enabledMiniMes.size() >= 2) {
            snowingNow = isSnowingNearPlayer(client) || snowBlocksOnScreen(client, width, height, now);
        } else {
            snowBlocksVisible = false;
        }
        if (snowingNow != snowing) {
            snowing = snowingNow;
            scheduleNextAggroCheck(now);
        }
        if (!preferences.snowballFights()) endSnowFight(now);

        validateFight(enabledMiniMes, now);
        validateSnowFight(enabledMiniMes, now);
        maybeStartFight(enabledMiniMes, now);
        updateFight(bounds, size, now);
        updateSnowFight(size, now);

        for (MiniMeDefinition miniMe : enabledMiniMes) {
            WalkerState state = STATES.get(miniMe);
            if (state.respawnAtNanos > 0L
                    && now >= state.deathStartedAtNanos + DEATH_ANIMATION_NANOS) continue;
            boolean dying = state.deathStartedAtNanos > 0L;
            if (!dying && (activeFight == null || !activeFight.contains(miniMe))
                    && (activeSnowFight == null || !activeSnowFight.contains(miniMe))) {
                update(state, bounds, now);
            }
            keepInBounds(state, bounds);
            draw(context, miniMe, state, size, now);
        }
        renderSmoke(context, now);
        renderCriticalHitParticles(context, now);
        renderSnowballs(context, bounds, size, now);
        renderSnowBurstParticles(context, now);
    }

    /**
     * Casts a grid of rays through the camera view and reports whether enough of them land on snow
     * (snow layers, snow blocks, powder snow). Rescans twice a second; hysteresis stops it flickering.
     */
    private static boolean snowBlocksOnScreen(Minecraft client, int width, int height, long now) {
        if (now < nextSnowScanAtNanos) return snowBlocksVisible;
        nextSnowScanAtNanos = now + SNOW_SCAN_INTERVAL_NANOS;
        ClientLevel level = client.level;
        if (level == null || client.player == null) {
            snowBlocksVisible = false;
            return false;
        }

        Camera camera = client.gameRenderer.mainCamera();
        Vec3 origin = camera.position();
        Vector3fc forward = camera.forwardVector();
        Vector3fc left = camera.leftVector();
        Vector3fc up = camera.upVector();
        double tanVertical = Math.tan(Math.toRadians(client.options.fov().get() / 2.0D));
        double tanHorizontal = tanVertical * width / (double) Math.max(1, height);

        int hits = 0;
        for (int row = 0; row < SNOW_SCAN_ROWS; row++) {
            double screenY = 1.0D - (row + 0.5D) / SNOW_SCAN_ROWS * 2.0D;
            for (int column = 0; column < SNOW_SCAN_COLUMNS; column++) {
                double screenX = (column + 0.5D) / SNOW_SCAN_COLUMNS * 2.0D - 1.0D;
                double rayX = forward.x() - left.x() * screenX * tanHorizontal + up.x() * screenY * tanVertical;
                double rayY = forward.y() - left.y() * screenX * tanHorizontal + up.y() * screenY * tanVertical;
                double rayZ = forward.z() - left.z() * screenX * tanHorizontal + up.z() * screenY * tanVertical;
                double length = Math.sqrt(rayX * rayX + rayY * rayY + rayZ * rayZ);
                if (length < 1.0E-6D) continue;
                Vec3 end = origin.add(rayX / length * SNOW_SCAN_RANGE,
                        rayY / length * SNOW_SCAN_RANGE, rayZ / length * SNOW_SCAN_RANGE);
                BlockHitResult hit = level.clip(new ClipContext(origin, end,
                        ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, client.player));
                if (hit.getType() == HitResult.Type.BLOCK
                        && level.getBlockState(hit.getBlockPos()).is(BlockTags.SNOW)) {
                    hits++;
                }
            }
        }
        if (hits >= SNOW_SCAN_ENTER_HITS) snowBlocksVisible = true;
        else if (hits <= SNOW_SCAN_EXIT_HITS) snowBlocksVisible = false;
        return snowBlocksVisible;
    }

    /** True while snow is actually falling around the player (snowy biome, open sky, enough precipitation). */
    private static boolean isSnowingNearPlayer(Minecraft client) {
        ClientLevel level = client.level;
        if (level == null || client.player == null) return false;
        if (level.getRainLevel(1.0F) < SNOW_MIN_RAIN_LEVEL) return false;
        BlockPos position = client.player.blockPosition();
        if (!level.canSeeSky(position)) return false;
        return level.getBiome(position).value().getPrecipitationAt(position, level.getSeaLevel())
                == Biome.Precipitation.SNOW;
    }

    private static void spawn(WalkerState state, Bounds bounds, long now) {
        state.x = randomBetween(bounds.minX, bounds.maxX);
        state.y = randomBetween(bounds.minY, bounds.maxY);
        state.facingRight = RANDOM.nextBoolean();
        state.initialized = true;
        state.resetTransientState();
        chooseTarget(state, bounds, now);
    }

    private static void update(WalkerState state, Bounds bounds, long now) {
        if (!state.walking) {
            if (now >= state.nextTargetAtNanos) chooseTarget(state, bounds, now);
            return;
        }

        float progress = Math.clamp((now - state.walkStartedAtNanos)
                / (float) Math.max(1L, state.walkDurationNanos), 0.0F, 1.0F);
        state.x = state.walkStartX + (state.targetX - state.walkStartX) * progress;
        state.y = state.walkStartY + (state.targetY - state.walkStartY) * progress;
        if (progress >= 1.0F) {
            state.x = state.targetX;
            state.y = state.targetY;
            state.walking = false;
            state.nextTargetAtNanos = now + MIN_ARRIVAL_PAUSE_NANOS
                    + nextLong(ARRIVAL_PAUSE_VARIATION_NANOS + 1L);
        }
    }

    private static void chooseTarget(WalkerState state, Bounds bounds, long now) {
        Point fallback = farthestPoint(state.x, state.y, bounds);
        float maximumDistance = distance(state.x, state.y, fallback.x, fallback.y);
        if (maximumDistance < 0.5F) {
            state.targetX = state.x;
            state.targetY = state.y;
            state.walking = false;
            state.nextTargetAtNanos = now + MIN_ARRIVAL_PAUSE_NANOS;
            return;
        }

        float requiredDistance = Math.min(MIN_TARGET_DISTANCE, maximumDistance * 0.65F);
        Point target = null;
        for (int attempt = 0; attempt < TARGET_ATTEMPTS; attempt++) {
            Point candidate = new Point(
                    randomBetween(bounds.minX, bounds.maxX),
                    randomBetween(bounds.minY, bounds.maxY));
            if (distance(state.x, state.y, candidate.x, candidate.y) >= requiredDistance
                    && (bounds.maxX - bounds.minX < 1.0F || Math.abs(candidate.x - state.x) >= 1.0F)) {
                target = candidate;
                break;
            }
        }
        if (target == null) target = fallback;

        state.targetX = target.x;
        state.targetY = target.y;
        if (Math.abs(state.targetX - state.x) >= 0.5F) {
            state.facingRight = state.targetX > state.x;
        }
        state.walkStartX = state.x;
        state.walkStartY = state.y;
        float walkDistance = distance(state.x, state.y, state.targetX, state.targetY);
        state.walkDurationNanos = Math.max(1L,
                (long) (walkDistance / WALK_SPEED * 1_000_000_000.0F));
        state.walking = true;
        state.walkStartedAtNanos = now;
    }

    private static void validateFight(List<MiniMeDefinition> enabledMiniMes, long now) {
        if (activeFight == null) return;
        if (!enabledMiniMes.contains(activeFight.first) || !enabledMiniMes.contains(activeFight.second)
                || STATES.get(activeFight.first).respawnAtNanos > 0L
                || STATES.get(activeFight.second).respawnAtNanos > 0L) {
            endFightWithoutWinner(now);
        }
    }

    private static void maybeStartFight(List<MiniMeDefinition> enabledMiniMes, long now) {
        if (activeFight != null || activeSnowFight != null || now < nextAggroCheckAtNanos) return;
        scheduleNextAggroCheck(now);
        List<MiniMeDefinition> candidates = enabledMiniMes.stream()
                .filter(miniMe -> STATES.get(miniMe).respawnAtNanos == 0L)
                .toList();
        float aggroChance = chaosMode ? CHAOS_AGGRO_CHANCE : snowing ? SNOW_FIGHT_CHANCE : NORMAL_AGGRO_CHANCE;
        if (candidates.size() < 2 || RANDOM.nextFloat() >= aggroChance) return;
        // Real snow always means a snowball fight; Chaos Mode also throws them (about half the fights) without snow.
        if (snowballFightsEnabled && (snowing || (chaosMode && RANDOM.nextFloat() < CHAOS_SNOWBALL_CHANCE))) {
            startSnowFight(candidates, now);
            return;
        }

        int firstIndex = RANDOM.nextInt(candidates.size());
        int secondIndex = RANDOM.nextInt(candidates.size() - 1);
        if (secondIndex >= firstIndex) secondIndex++;
        MiniMeDefinition first = candidates.get(firstIndex);
        MiniMeDefinition second = candidates.get(secondIndex);
        WalkerState firstState = STATES.get(first);
        WalkerState secondState = STATES.get(second);
        firstState.health = AGGRO_HEALTH;
        secondState.health = AGGRO_HEALTH;
        beginCombatWalk(firstState, now);
        beginCombatWalk(secondState, now);
        activeFight = new Fight(first, second, RANDOM.nextBoolean() ? first : second,
                now, now + HIT_INTERVAL_NANOS);
    }

    private static void updateFight(Bounds bounds, int size, long now) {
        Fight fight = activeFight;
        if (fight == null) return;
        WalkerState first = STATES.get(fight.first);
        WalkerState second = STATES.get(fight.second);
        float deltaSeconds = Math.clamp((now - fight.lastUpdatedAtNanos) / 1_000_000_000.0F,
                0.0F, 0.05F);
        fight.lastUpdatedAtNanos = now;

        if (first.knockbackEndsAtNanos > now || second.knockbackEndsAtNanos > now) {
            first.walking = false;
            second.walking = false;
            first.facingRight = second.x >= first.x;
            second.facingRight = first.x >= second.x;
            return;
        }

        float dx = second.x - first.x;
        float dy = second.y - first.y;
        float separation = (float) Math.hypot(dx, dy);
        float attackDistance = Math.max(8.0F, size * AGGRO_DISTANCE_FACTOR);
        if (separation > attackDistance + 0.5F) {
            float step = Math.min(AGGRO_APPROACH_SPEED * deltaSeconds,
                    Math.max(0.0F, (separation - attackDistance) * 0.5F));
            if (separation > 0.001F) {
                float normalX = dx / separation;
                float normalY = dy / separation;
                first.x += normalX * step;
                first.y += normalY * step;
                second.x -= normalX * step;
                second.y -= normalY * step;
            }
            first.facingRight = second.x >= first.x;
            second.facingRight = first.x >= second.x;
            beginCombatWalk(first, fight.startedAtNanos);
            beginCombatWalk(second, fight.startedAtNanos);
            first.targetX = second.x;
            first.targetY = second.y;
            second.targetX = first.x;
            second.targetY = first.y;
            keepInBounds(first, bounds);
            keepInBounds(second, bounds);
            return;
        }

        first.walking = false;
        second.walking = false;
        first.facingRight = second.x >= first.x;
        second.facingRight = first.x >= second.x;
        if (now < fight.nextHitAtNanos) return;

        MiniMeDefinition attackerDefinition = fight.nextAttacker;
        MiniMeDefinition victimDefinition = fight.other(attackerDefinition);
        WalkerState attacker = STATES.get(attackerDefinition);
        WalkerState victim = STATES.get(victimDefinition);
        attacker.attackStartedAtNanos = now;
        boolean criticalHit = RANDOM.nextFloat() < CRITICAL_HIT_CHANCE;
        int damage = ATTACK_DAMAGE * (criticalHit ? CRITICAL_HIT_DAMAGE_MULTIPLIER : 1);
        victim.health -= damage;
        victim.damageFlashUntilNanos = now + DAMAGE_FLASH_NANOS;
        if (criticalHit) spawnCriticalHitParticles(victim.x, victim.y, size, now);
        applyKnockback(attacker, victim, bounds, size, now);
        if (victim.health <= 0) {
            kill(victim, size, now);
            WalkerState winner = STATES.get(attackerDefinition);
            winner.walking = false;
            winner.nextTargetAtNanos = now + MIN_ARRIVAL_PAUSE_NANOS;
            activeFight = null;
            scheduleNextAggroCheck(now);
            return;
        }

        fight.nextAttacker = victimDefinition;
        fight.nextHitAtNanos = now + HIT_INTERVAL_NANOS;
    }

    /**
     * Each available AdMini randomly decides whether to join the snowball fight (at least two always do);
     * the ones that sit out keep wandering. Joiners throw from wherever they are standing.
     */
    private static void startSnowFight(List<MiniMeDefinition> candidates, long now) {
        List<MiniMeDefinition> joiners = new ArrayList<>();
        List<MiniMeDefinition> sittingOut = new ArrayList<>();
        for (MiniMeDefinition miniMe : candidates) {
            (RANDOM.nextFloat() < SNOW_JOIN_CHANCE ? joiners : sittingOut).add(miniMe);
        }
        while (joiners.size() < 2 && !sittingOut.isEmpty()) {
            joiners.add(sittingOut.remove(RANDOM.nextInt(sittingOut.size())));
        }

        List<SnowParticipant> participants = new ArrayList<>();
        for (MiniMeDefinition miniMe : joiners) {
            WalkerState state = STATES.get(miniMe);
            state.walking = false;
            SnowParticipant participant = new SnowParticipant(miniMe);
            participant.throwsRemaining = SNOWBALL_MIN_THROWS_PER_MINI
                    + RANDOM.nextInt(SNOWBALL_MAX_THROWS_PER_MINI - SNOWBALL_MIN_THROWS_PER_MINI + 1);
            participant.nextProjectile = rollProjectile();
            participant.nextThrowAtNanos = now + SNOWBALL_FIRST_THROW_DELAY_NANOS
                    + nextLong(SNOWBALL_THROW_VARIATION_NANOS + 1L);
            participants.add(participant);
        }
        activeSnowFight = new SnowFight(participants, now + SNOW_FIGHT_MAX_NANOS);
    }

    private static void validateSnowFight(List<MiniMeDefinition> enabledMiniMes, long now) {
        SnowFight fight = activeSnowFight;
        if (fight == null) return;
        fight.participants.removeIf(participant -> !enabledMiniMes.contains(participant.miniMe)
                || STATES.get(participant.miniMe).respawnAtNanos > 0L);
        if (fight.participants.size() < 2) endSnowFight(now);
    }

    private static void updateSnowFight(int size, long now) {
        SnowFight fight = activeSnowFight;
        if (fight == null) return;
        if ((fight.finishAtNanos > 0L && now >= fight.finishAtNanos) || now >= fight.deadlineAtNanos) {
            endSnowFight(now);
            return;
        }

        if (fight.finishAtNanos == 0L && !fight.anyThrowsLeft()) {
            fight.finishAtNanos = now + SNOWBALL_FIGHT_END_DELAY_NANOS;
        }

        for (SnowParticipant participant : fight.participants) {
            WalkerState state = STATES.get(participant.miniMe);
            state.walking = false;
            if (state.knockbackEndsAtNanos > now) continue;

            if (participant.target != null && !fight.contains(participant.target)) participant.target = null;
            if (participant.target == null && participant.throwsRemaining > 0
                    && now >= participant.nextThrowAtNanos - SNOWBALL_HOLD_WINDOW_NANOS) {
                participant.target = pickSnowTarget(fight, participant);
            }
            if (participant.target != null) {
                state.facingRight = STATES.get(participant.target).x >= state.x;
            }
            if (participant.throwsRemaining > 0 && participant.target != null
                    && now >= participant.nextThrowAtNanos) {
                throwSnowball(fight, participant, size, now);
            }
        }
    }

    private static MiniMeDefinition pickSnowTarget(SnowFight fight, SnowParticipant thrower) {
        List<MiniMeDefinition> options = new ArrayList<>();
        for (SnowParticipant participant : fight.participants) {
            if (participant != thrower) options.add(participant.miniMe);
        }
        return options.isEmpty() ? null : options.get(RANDOM.nextInt(options.size()));
    }

    private static void throwSnowball(SnowFight fight, SnowParticipant participant, int size, long now) {
        WalkerState thrower = STATES.get(participant.miniMe);
        thrower.attackStartedAtNanos = now;
        WalkerState targetState = STATES.get(participant.target);
        Projectile type = participant.nextProjectile;
        float handX = thrower.x + size * (thrower.facingRight ? 0.8F : 0.2F);
        float handY = thrower.y + size * 0.45F;
        float distance = (float) Math.hypot(targetState.x + size * 0.5F - handX,
                targetState.y + size * 0.45F - handY);
        long flightNanos = Math.clamp((long) (distance / SNOWBALL_SPEED * 1_000_000_000.0F),
                SNOWBALL_MIN_FLIGHT_NANOS, SNOWBALL_MAX_FLIGHT_NANOS);
        float arcHeight = Math.clamp(distance * SNOWBALL_ARC_FACTOR,
                SNOWBALL_MIN_ARC_HEIGHT, SNOWBALL_MAX_ARC_HEIGHT);
        if (type == Projectile.ANVIL) {
            // Anvils drop straight down from above the screen onto the target.
            handX = targetState.x + size * 0.5F;
            handY = -type.height * 2.0F;
            flightNanos = ANVIL_FALL_NANOS;
            arcHeight = 0.0F;
        }
        SNOWBALLS.add(new Snowball(participant.miniMe, participant.target, type,
                handX, handY, now, flightNanos, arcHeight));
        participant.throwsRemaining--;
        participant.target = null;
        participant.nextProjectile = rollProjectile();
        participant.nextThrowAtNanos = now + SNOWBALL_THROW_INTERVAL_NANOS
                + nextLong(SNOWBALL_THROW_VARIATION_NANOS + 1L);
    }

    private static void endSnowFight(long now) {
        SnowFight fight = activeSnowFight;
        if (fight == null) return;
        for (SnowParticipant participant : fight.participants) {
            WalkerState state = STATES.get(participant.miniMe);
            if (state.respawnAtNanos == 0L) {
                state.walking = false;
                state.nextTargetAtNanos = now + MIN_ARRIVAL_PAUSE_NANOS;
            }
        }
        activeSnowFight = null;
        scheduleNextAggroCheck(now);
    }

    /**
     * Flies each projectile towards its target. Snowballs arc across and nudge the target, ice blocks
     * also deal damage, and anvils drop from above the screen and kill outright.
     */
    private static void renderSnowballs(GuiGraphicsExtractor context, Bounds bounds, int size, long now) {
        for (Iterator<Snowball> iterator = SNOWBALLS.iterator(); iterator.hasNext(); ) {
            Snowball snowball = iterator.next();
            WalkerState target = STATES.get(snowball.target);
            float progress = Math.clamp((now - snowball.spawnedAtNanos)
                    / (float) snowball.flightNanos, 0.0F, 1.0F);
            float endX = target.x + size * 0.5F;
            float endY = target.y + size * 0.45F;
            if (progress >= 1.0F) {
                iterator.remove();
                if (target.initialized && target.respawnAtNanos == 0L) {
                    landProjectile(snowball, target, endX, endY, bounds, size, now);
                }
                continue;
            }
            float x;
            float y;
            if (snowball.type == Projectile.ANVIL) {
                x = endX; // keeps tracking the target as it moves
                y = snowball.startY + (endY - snowball.startY) * progress * progress; // accelerates like gravity
            } else {
                x = snowball.startX + (endX - snowball.startX) * progress;
                y = snowball.startY + (endY - snowball.startY) * progress
                        - (float) Math.sin(Math.PI * progress) * snowball.arcHeight;
            }
            drawProjectile(context, snowball.type,
                    Math.round(x) - snowball.type.width / 2, Math.round(y) - snowball.type.height / 2);
        }
    }

    private static void landProjectile(Snowball snowball, WalkerState target, float x, float y,
                                       Bounds bounds, int size, long now) {
        WalkerState thrower = STATES.get(snowball.thrower);
        switch (snowball.type) {
            case SNOWBALL -> {
                spawnBurst(x, y, SNOWBALL_BODY_COLOR, SNOW_BURST_PARTICLE_COUNT, now);
                applyKnockback(thrower, target, bounds, size, now);
            }
            case ICE_BLOCK -> {
                spawnBurst(x, y, ICE_BODY_COLOR, ICE_SHATTER_PARTICLE_COUNT, now);
                target.health -= ICE_BLOCK_DAMAGE;
                target.damageFlashUntilNanos = now + DAMAGE_FLASH_NANOS;
                applyKnockback(thrower, target, bounds, size, now);
                if (target.health <= 0) kill(target, size, now);
            }
            case ANVIL -> {
                spawnBurst(x, y, ANVIL_SHINE_COLOR, ANVIL_IMPACT_PARTICLE_COUNT, now);
                kill(target, size, now);
            }
        }
    }

    private static Projectile rollProjectile() {
        float roll = RANDOM.nextFloat();
        if (roll < ANVIL_CHANCE) return Projectile.ANVIL;
        if (roll < ANVIL_CHANCE + ICE_BLOCK_CHANCE) return Projectile.ICE_BLOCK;
        return Projectile.SNOWBALL;
    }

    private static void drawProjectile(GuiGraphicsExtractor context, Projectile type, int x, int y) {
        switch (type) {
            case SNOWBALL -> drawSnowball(context, x, y);
            case ICE_BLOCK -> drawIceBlock(context, x, y);
            case ANVIL -> drawAnvil(context, x, y);
        }
    }

    private static void drawIceBlock(GuiGraphicsExtractor context, int x, int y) {
        context.fill(x, y, x + 10, y + 10, ICE_OUTLINE_COLOR);
        context.fill(x + 1, y + 1, x + 9, y + 9, ICE_BODY_COLOR);
        context.fill(x + 2, y + 2, x + 4, y + 3, ICE_SHINE_COLOR);
        context.fill(x + 2, y + 3, x + 3, y + 5, ICE_SHINE_COLOR);
    }

    private static void drawAnvil(GuiGraphicsExtractor context, int x, int y) {
        // 14x10: wide top face, narrow waist, wide base.
        context.fill(x, y, x + 14, y + 4, ANVIL_OUTLINE_COLOR);
        context.fill(x + 4, y + 4, x + 10, y + 7, ANVIL_OUTLINE_COLOR);
        context.fill(x + 2, y + 7, x + 12, y + 10, ANVIL_OUTLINE_COLOR);
        context.fill(x + 1, y + 1, x + 13, y + 3, ANVIL_BODY_COLOR);
        context.fill(x + 5, y + 4, x + 9, y + 7, ANVIL_BODY_COLOR);
        context.fill(x + 3, y + 8, x + 11, y + 9, ANVIL_BODY_COLOR);
        context.fill(x + 1, y + 1, x + 8, y + 2, ANVIL_SHINE_COLOR);
    }

    private static void drawSnowball(GuiGraphicsExtractor context, int x, int y) {
        context.fill(x + 2, y, x + 6, y + 1, SNOWBALL_OUTLINE_COLOR);
        context.fill(x + 1, y + 1, x + 7, y + 2, SNOWBALL_OUTLINE_COLOR);
        context.fill(x, y + 2, x + 8, y + 6, SNOWBALL_OUTLINE_COLOR);
        context.fill(x + 1, y + 6, x + 7, y + 7, SNOWBALL_OUTLINE_COLOR);
        context.fill(x + 2, y + 7, x + 6, y + 8, SNOWBALL_OUTLINE_COLOR);
        context.fill(x + 2, y + 1, x + 6, y + 2, SNOWBALL_BODY_COLOR);
        context.fill(x + 1, y + 2, x + 7, y + 5, SNOWBALL_BODY_COLOR);
        context.fill(x + 2, y + 5, x + 6, y + 6, SNOWBALL_BODY_COLOR);
    }

    private static void spawnBurst(float x, float y, int color, int count, long now) {
        for (int index = 0; index < count; index++) {
            float angle = RANDOM.nextFloat() * (float) (Math.PI * 2.0);
            float speed = 14.0F + RANDOM.nextFloat() * 16.0F;
            SNOW_BURST_PARTICLES.add(new SnowBurstParticle(x, y,
                    (float) Math.cos(angle) * speed, (float) Math.sin(angle) * speed - 8.0F,
                    2 + RANDOM.nextInt(2), color, now));
        }
    }

    private static void renderSnowBurstParticles(GuiGraphicsExtractor context, long now) {
        SNOW_BURST_PARTICLES.removeIf(particle ->
                now - particle.spawnedAtNanos >= SNOW_BURST_LIFETIME_NANOS);
        for (SnowBurstParticle particle : SNOW_BURST_PARTICLES) {
            float ageSeconds = (now - particle.spawnedAtNanos) / 1_000_000_000.0F;
            float progress = Math.clamp((now - particle.spawnedAtNanos)
                    / (float) SNOW_BURST_LIFETIME_NANOS, 0.0F, 1.0F);
            int x = Math.round(particle.x + particle.velocityX * ageSeconds);
            int y = Math.round(particle.y + particle.velocityY * ageSeconds
                    + 40.0F * ageSeconds * ageSeconds);
            int particleSize = Math.max(1, Math.round(particle.size * (1.0F - progress * 0.6F)));
            context.fill(x, y, x + particleSize, y + particleSize, particle.color);
        }
    }

    private static void beginCombatWalk(WalkerState state, long animationStartNanos) {
        if (!state.walking) state.walkStartedAtNanos = animationStartNanos;
        state.walking = true;
    }

    private static void applyKnockback(WalkerState attacker, WalkerState victim,
                                       Bounds bounds, int size, long now) {
        float dx = victim.x - attacker.x;
        float dy = victim.y - attacker.y;
        float length = (float) Math.hypot(dx, dy);
        if (length < 0.001F) {
            dx = victim.facingRight ? -1.0F : 1.0F;
            dy = 0.0F;
            length = 1.0F;
        }
        float knockbackDistance = Math.max(7.0F, size * KNOCKBACK_DISTANCE_FACTOR);
        victim.knockbackStartX = victim.x;
        victim.knockbackStartY = victim.y;
        victim.knockbackTargetX = Math.clamp(victim.x + dx / length * knockbackDistance,
                bounds.minX, bounds.maxX);
        victim.knockbackTargetY = Math.clamp(victim.y + dy / length * knockbackDistance,
                bounds.minY, bounds.maxY);
        victim.knockbackStartedAtNanos = now;
        victim.knockbackEndsAtNanos = now + KNOCKBACK_NANOS;
        victim.walking = false;
    }

    private static void updateKnockback(WalkerState state, Bounds bounds, long now) {
        if (state.knockbackEndsAtNanos == 0L) return;
        float progress = Math.clamp((now - state.knockbackStartedAtNanos)
                / (float) KNOCKBACK_NANOS, 0.0F, 1.0F);
        float inverse = 1.0F - progress;
        float easedProgress = 1.0F - inverse * inverse * inverse;
        state.x = state.knockbackStartX
                + (state.knockbackTargetX - state.knockbackStartX) * easedProgress;
        state.y = state.knockbackStartY
                + (state.knockbackTargetY - state.knockbackStartY) * easedProgress;
        keepInBounds(state, bounds);
        if (progress >= 1.0F) {
            state.x = state.knockbackTargetX;
            state.y = state.knockbackTargetY;
            state.knockbackStartedAtNanos = 0L;
            state.knockbackEndsAtNanos = 0L;
        }
    }

    private static void kill(WalkerState state, int size, long now) {
        state.walking = false;
        state.deathFrameIndex = 0;
        state.deathStartedAtNanos = now;
        state.respawnAtNanos = now + DEATH_ANIMATION_NANOS + RESPAWN_DELAY_NANOS;
        spawnSmoke(state.x, state.y, size, now);
    }

    private static void spawnSmoke(float left, float top, int bodySize, long now) {
        for (int index = 0; index < SMOKE_PARTICLE_COUNT; index++) {
            float x = left + bodySize * (0.06F + RANDOM.nextFloat() * 0.88F);
            float y = top + bodySize * (0.06F + RANDOM.nextFloat() * 0.88F);
            float angle = RANDOM.nextFloat() * (float) (Math.PI * 2.0);
            float speed = 7.0F + RANDOM.nextFloat() * 17.0F;
            float velocityX = (float) Math.cos(angle) * speed;
            float velocityY = (float) Math.sin(angle) * speed - 10.0F;
            int size = 4 + RANDOM.nextInt(4);
            long lifetime = SMOKE_LIFETIME_NANOS - 160_000_000L
                    + nextLong(320_000_001L);
            SMOKE_PARTICLES.add(new SmokeParticle(x, y, velocityX, velocityY, size, now, lifetime));
        }
    }

    private static void renderSmoke(GuiGraphicsExtractor context, long now) {
        SMOKE_PARTICLES.removeIf(particle -> now - particle.spawnedAtNanos >= particle.lifetimeNanos);
        for (SmokeParticle particle : SMOKE_PARTICLES) {
            float ageSeconds = (now - particle.spawnedAtNanos) / 1_000_000_000.0F;
            float progress = Math.clamp((now - particle.spawnedAtNanos)
                    / (float) particle.lifetimeNanos, 0.0F, 1.0F);
            float spread = 0.80F + progress * 0.85F;
            int x = Math.round(particle.x + particle.velocityX * ageSeconds);
            int y = Math.round(particle.y + particle.velocityY * ageSeconds
                    - 7.0F * ageSeconds * ageSeconds);
            int particleSize = Math.max(1, Math.round(particle.size * spread));
            int frame = Math.min(SMOKE_TEXTURES.size() - 1,
                    (int) (progress * SMOKE_TEXTURES.size()));
            context.blit(SMOKE_TEXTURES.get(frame),
                    x - particleSize, y - particleSize,
                    x + particleSize + 1, y + particleSize + 1,
                    0.0F, 1.0F, 0.0F, 1.0F);
        }
    }

    private static void spawnCriticalHitParticles(float left, float top, int bodySize, long now) {
        for (int index = 0; index < CRITICAL_HIT_PARTICLE_COUNT; index++) {
            float x = left + bodySize * (0.12F + RANDOM.nextFloat() * 0.76F);
            float y = top + bodySize * (0.12F + RANDOM.nextFloat() * 0.76F);
            float angle = RANDOM.nextFloat() * (float) (Math.PI * 2.0);
            float speed = 12.0F + RANDOM.nextFloat() * 18.0F;
            float velocityX = (float) Math.cos(angle) * speed;
            float velocityY = (float) Math.sin(angle) * speed - 6.0F;
            int particleSize = 3 + RANDOM.nextInt(3);
            CRITICAL_HIT_PARTICLES.add(new CriticalHitParticle(
                    x, y, velocityX, velocityY, particleSize, now));
        }
    }

    private static void renderCriticalHitParticles(GuiGraphicsExtractor context, long now) {
        CRITICAL_HIT_PARTICLES.removeIf(particle ->
                now - particle.spawnedAtNanos >= CRITICAL_HIT_PARTICLE_LIFETIME_NANOS);
        for (CriticalHitParticle particle : CRITICAL_HIT_PARTICLES) {
            float ageSeconds = (now - particle.spawnedAtNanos) / 1_000_000_000.0F;
            float progress = Math.clamp((now - particle.spawnedAtNanos)
                    / (float) CRITICAL_HIT_PARTICLE_LIFETIME_NANOS, 0.0F, 1.0F);
            float scale = 1.0F - progress * 0.65F;
            int x = Math.round(particle.x + particle.velocityX * ageSeconds);
            int y = Math.round(particle.y + particle.velocityY * ageSeconds
                    + 18.0F * ageSeconds * ageSeconds);
            int particleSize = Math.max(1, Math.round(particle.size * scale));
            context.blit(CRITICAL_HIT_TEXTURE,
                    x - particleSize, y - particleSize,
                    x + particleSize + 1, y + particleSize + 1,
                    0.0F, 1.0F, 0.0F, 1.0F);
        }
    }

    private static void endFightWithoutWinner(long now) {
        if (activeFight == null) return;
        for (MiniMeDefinition miniMe : List.of(activeFight.first, activeFight.second)) {
            WalkerState state = STATES.get(miniMe);
            if (state.respawnAtNanos == 0L) {
                state.walking = false;
                state.nextTargetAtNanos = now + MIN_ARRIVAL_PAUSE_NANOS;
            }
        }
        activeFight = null;
        scheduleNextAggroCheck(now);
    }

    private static void scheduleNextAggroCheck(long now) {
        boolean snowFights = snowing && !chaosMode;
        long minimum = chaosMode ? CHAOS_AGGRO_CHECK_MIN_NANOS
                : snowFights ? SNOW_AGGRO_CHECK_MIN_NANOS : NORMAL_AGGRO_CHECK_MIN_NANOS;
        long variation = chaosMode ? CHAOS_AGGRO_CHECK_VARIATION_NANOS
                : snowFights ? SNOW_AGGRO_CHECK_VARIATION_NANOS : NORMAL_AGGRO_CHECK_VARIATION_NANOS;
        nextAggroCheckAtNanos = now + minimum + nextLong(variation + 1L);
    }

    private static void draw(GuiGraphicsExtractor context, MiniMeDefinition miniMe, WalkerState state, int size, long now) {
        long walkElapsed = Math.max(0L, now - state.walkStartedAtNanos);
        boolean dying = state.deathStartedAtNanos > 0L;
        long attackElapsed = now - state.attackStartedAtNanos;
        boolean attacking = !dying && state.attackStartedAtNanos > 0L
                && attackElapsed < ATTACK_LUNGE_NANOS;
        int frameIndex = dying ? state.deathFrameIndex
                : state.walking ? (int) (walkElapsed / WALK_FRAME_NANOS % miniMe.walkingFrameCount()) : 0;
        MiniMeFrame frame = attacking ? miniMe.hittingFrame() : miniMe.walkingFrame(frameIndex);
        float scale = Math.min(size / (float) frame.width, size / (float) frame.height);
        float widthBounce = 1.0F;
        float heightBounce = 1.0F;
        float hop = 0.0F;
        if (state.walking && !dying) {
            long stepNanos = WALK_FRAME_NANOS * 2L;
            float stepProgress = walkElapsed % stepNanos / (float) stepNanos;
            float stepArc = (float) Math.sin(Math.PI * stepProgress);
            hop = stepArc * WALK_HOP_HEIGHT;
            widthBounce = 1.0F - stepArc * 0.015F;
            heightBounce = 1.0F + stepArc * 0.025F;
        }
        float scaleX = scale * widthBounce;
        float scaleY = scale * heightBounce;
        float frameWidth = frame.width * scaleX;
        float frameHeight = frame.height * scaleY;
        float left = state.facingRight ? state.x : state.x + miniMe.anchorWidth(size) - frameWidth;
        if (attacking) {
            float attackProgress = Math.clamp(attackElapsed / (float) ATTACK_LUNGE_NANOS, 0.0F, 1.0F);
            float lunge = (float) Math.sin(Math.PI * attackProgress) * ATTACK_LUNGE_DISTANCE;
            left += state.facingRight ? lunge : -lunge;
        }
        left = Math.clamp(left, 0.0F,
                Math.max(0.0F, context.guiWidth() - frameWidth));
        float top = Math.clamp(state.y - hop, 0.0F,
                Math.max(0.0F, context.guiHeight() - frameHeight));
        float u1 = state.facingRight ? 0.0F : 1.0F;
        float u2 = state.facingRight ? 1.0F : 0.0F;
        Identifier texture = dying || now < state.damageFlashUntilNanos
                ? frame.damageTexture : frame.texture;
        context.pose().pushMatrix();
        if (dying) {
            float deathProgress = Math.clamp((now - state.deathStartedAtNanos)
                    / (float) DEATH_ANIMATION_NANOS, 0.0F, 1.0F);
            float easedProgress = deathProgress * deathProgress * (3.0F - 2.0F * deathProgress);
            float rotation = (float) Math.toRadians(90.0F * easedProgress);
            context.pose().translate(left + frameWidth / 2.0F, top + frameHeight);
            context.pose().rotate(rotation);
            context.pose().scale(scaleX, scaleY);
            context.blit(texture, -frame.width / 2, -frame.height,
                    frame.width / 2, 0, u1, u2, 0.0F, 1.0F);
        } else {
            context.pose().translate(left, top);
            context.pose().scale(scaleX, scaleY);
            context.blit(texture, 0, 0, frame.width, frame.height,
                    u1, u2, 0.0F, 1.0F);
        }
        context.pose().popMatrix();
        SnowFight snowFight = activeSnowFight;
        Projectile held = !dying && snowFight != null ? snowFight.heldProjectile(miniMe, now) : null;
        if (held != null) {
            float handX = state.facingRight ? left + frameWidth * 0.8F
                    : left + frameWidth * 0.2F - held.width;
            drawProjectile(context, held, Math.round(handX), Math.round(top + frameHeight * 0.45F));
        }
    }

    private static void reanchorForResize(int previousWidth, int previousHeight,
                                          int width, int height, long now) {
        endFightWithoutWinner(now);
        endSnowFight(now);
        SNOWBALLS.clear();
        Bounds previousBounds = bounds(previousWidth, previousHeight, miniMeSize(previousWidth, previousHeight));
        Bounds bounds = bounds(width, height, miniMeSize(width, height));
        for (WalkerState state : STATES.values()) {
            if (!state.initialized) continue;
            state.x = remapAnchored(state.x, previousBounds.minX, previousBounds.maxX,
                    bounds.minX, bounds.maxX);
            state.y = remapAnchored(state.y, previousBounds.minY, previousBounds.maxY,
                    bounds.minY, bounds.maxY);
            state.targetX = state.x;
            state.targetY = state.y;
            state.walking = false;
            state.nextTargetAtNanos = now + RESIZE_PAUSE_NANOS;
            state.walkStartedAtNanos = now;
        }
    }

    private static float remapAnchored(float value, float previousMinimum, float previousMaximum,
                                       float minimum, float maximum) {
        float previousRange = previousMaximum - previousMinimum;
        if (previousRange <= 0.001F) return (minimum + maximum) * 0.5F;
        float anchor = Math.clamp((value - previousMinimum) / previousRange, 0.0F, 1.0F);
        return minimum + anchor * (maximum - minimum);
    }

    private static void keepInBounds(WalkerState state, Bounds bounds) {
        state.x = Math.clamp(state.x, bounds.minX, bounds.maxX);
        state.y = Math.clamp(state.y, bounds.minY, bounds.maxY);
        state.targetX = Math.clamp(state.targetX, bounds.minX, bounds.maxX);
        state.targetY = Math.clamp(state.targetY, bounds.minY, bounds.maxY);
    }

    private static Point farthestPoint(float x, float y, Bounds bounds) {
        Point[] corners = {
                new Point(bounds.minX, bounds.minY),
                new Point(bounds.maxX, bounds.minY),
                new Point(bounds.minX, bounds.maxY),
                new Point(bounds.maxX, bounds.maxY)
        };
        Point farthest = corners[0];
        float farthestDistance = -1.0F;
        for (Point corner : corners) {
            float candidateDistance = distance(x, y, corner.x, corner.y);
            if (candidateDistance > farthestDistance) {
                farthest = corner;
                farthestDistance = candidateDistance;
            }
        }
        return farthest;
    }

    private static int miniMeSize(int width, int height) {
        return Math.max(1, Math.min(MINI_ME_SIZE,
                Math.min(Math.max(1, width - SCREEN_MARGIN * 2), Math.max(1, height - SCREEN_MARGIN * 2))));
    }

    private static Bounds bounds(int width, int height, int size) {
        float availableX = Math.max(0, width - size);
        float availableY = Math.max(0, height - size);
        float minX = Math.min(SCREEN_MARGIN, availableX);
        float minY = Math.min(SCREEN_MARGIN, availableY);
        float maxX = Math.max(minX, availableX - SCREEN_MARGIN);
        float maxY = Math.max(minY, availableY - SCREEN_MARGIN);
        return new Bounds(minX, minY, maxX, maxY);
    }

    private static float randomBetween(float minimum, float maximum) {
        if (maximum <= minimum) return minimum;
        return minimum + RANDOM.nextFloat() * (maximum - minimum);
    }

    private static long nextLong(long bound) {
        return bound <= 1L ? 0L : RANDOM.nextLong(bound);
    }

    private static float distance(float x1, float y1, float x2, float y2) {
        return (float) Math.hypot(x2 - x1, y2 - y1);
    }

    private static void resetAll() {
        for (WalkerState state : STATES.values()) {
            state.initialized = false;
            state.resetTransientState();
        }
        activeFight = null;
        activeSnowFight = null;
        SMOKE_PARTICLES.clear();
        CRITICAL_HIT_PARTICLES.clear();
        SNOWBALLS.clear();
        SNOW_BURST_PARTICLES.clear();
        scheduleNextAggroCheck(System.nanoTime());
    }

    private enum MiniMeDefinition {
        DUPEY("dupey"),
        SIAFFY("siaffy"),
        BEANY("beany"),
        MORVY("morvy"),
        BIGGY("biggy"),
        SERPY("serpy"),
        DOGGY("doggy"),
        CONFY("confy");

        private final Identifier[] walkingTextures;
        private final MiniMeFrame[] resolvedWalkingFrames;
        private final boolean[] walkingResolutionAttempted;
        private final Identifier hittingTexture;
        private MiniMeFrame resolvedHittingFrame;
        private boolean hittingResolutionAttempted;

        MiniMeDefinition(String id) {
            walkingTextures = new Identifier[MAX_WALK_FRAMES];
            resolvedWalkingFrames = new MiniMeFrame[MAX_WALK_FRAMES];
            walkingResolutionAttempted = new boolean[MAX_WALK_FRAMES];
            for (int frame = 0; frame < MAX_WALK_FRAMES; frame++) {
                walkingTextures[frame] = walkingTextureId(id, "%02d".formatted(frame));
            }
            hittingTexture = hittingTextureId(id);
        }

        private int walkingFrameCount() {
            return walkingTextures.length;
        }

        private MiniMeFrame walkingFrame(int index) {
            if (!walkingResolutionAttempted[index]) {
                walkingResolutionAttempted[index] = true;
                resolvedWalkingFrames[index] = loadFrame(walkingTextures[index]);
            }
            if (resolvedWalkingFrames[index] != null) return resolvedWalkingFrames[index];
            return index == 0
                    ? new MiniMeFrame(walkingTextures[0], walkingTextures[0], 800, 800)
                    : walkingFrame(0);
        }

        private MiniMeFrame hittingFrame() {
            if (!hittingResolutionAttempted) {
                hittingResolutionAttempted = true;
                resolvedHittingFrame = loadFrame(hittingTexture);
            }
            return resolvedHittingFrame != null ? resolvedHittingFrame : walkingFrame(0);
        }

        private void invalidateFrames() {
            for (int index = 0; index < resolvedWalkingFrames.length; index++) {
                resolvedWalkingFrames[index] = null;
                walkingResolutionAttempted[index] = false;
            }
            resolvedHittingFrame = null;
            hittingResolutionAttempted = false;
        }

        private float anchorWidth(int size) {
            MiniMeFrame idleFrame = walkingFrame(0);
            float scale = Math.min(size / (float) idleFrame.width, size / (float) idleFrame.height);
            return idleFrame.width * scale;
        }

        private boolean enabled(MiniMeHudPreferences preferences) {
            return switch (this) {
                case DUPEY -> preferences.dupeyHud();
                case SIAFFY -> preferences.siaffyHud();
                case BEANY -> preferences.beanyHud();
                case MORVY -> preferences.morvyHud();
                case BIGGY -> preferences.biggyHud();
                case SERPY -> preferences.serpyHud();
                case DOGGY -> preferences.doggyHud();
                case CONFY -> preferences.confyHud();
            };
        }

        private static Identifier walkingTextureId(String miniMe, String frame) {
            return Identifier.fromNamespaceAndPath(DMLS.MOD_ID.toLowerCase(),
                    "textures/gui/mini_me/%s/walking/%s_walking_%s.png"
                            .formatted(miniMe, miniMe, frame));
        }

        private static Identifier hittingTextureId(String miniMe) {
            return Identifier.fromNamespaceAndPath(DMLS.MOD_ID.toLowerCase(),
                    "textures/gui/mini_me/%s/hitting/%s_hitting.png"
                            .formatted(miniMe, miniMe));
        }

        private static MiniMeFrame loadFrame(Identifier texture) {
            var resource = Minecraft.getInstance().getResourceManager().getResource(texture);
            if (resource.isEmpty()) return null;
            try (InputStream stream = resource.get().open()) {
                try (NativeImage image = NativeImage.read(stream)) {
                    NativeImage damageImage = image.mappedCopy(MiniMeHudOverlay::damageTint);
                    Identifier damageTexture = Identifier.fromNamespaceAndPath(DMLS.MOD_ID.toLowerCase(),
                            "mini_me_damage/" + texture.getPath().replace('/', '_'));
                    DynamicTexture backedTexture = new DynamicTexture(
                            () -> "DMLS AdMinis damage texture " + texture, damageImage);
                    Minecraft.getInstance().getTextureManager().register(damageTexture, backedTexture);
                    return new MiniMeFrame(texture, damageTexture, image.getWidth(), image.getHeight());
                }
            } catch (IOException exception) {
                DMLS.LOGGER.warn("Could not read HUD AdMinis frame dimensions for {}", texture, exception);
                return new MiniMeFrame(texture, texture, 800, 800);
            }
        }
    }

    private static int damageTint(int color) {
        int alpha = color >>> 24;
        int red = color >>> 16 & 0xFF;
        int green = color >>> 8 & 0xFF;
        int blue = color & 0xFF;
        int tintedRed = Math.min(255, (int) (red * 0.70F) + 100);
        int tintedGreen = (int) (green * 0.28F);
        int tintedBlue = (int) (blue * 0.28F);
        return alpha << 24 | tintedRed << 16 | tintedGreen << 8 | tintedBlue;
    }

    private record MiniMeFrame(Identifier texture, Identifier damageTexture, int width, int height) {
    }

    private static final class WalkerState {
        private float x;
        private float y;
        private float targetX;
        private float targetY;
        private float walkStartX;
        private float walkStartY;
        private boolean facingRight;
        private boolean walking;
        private boolean initialized;
        private long walkStartedAtNanos;
        private long walkDurationNanos;
        private long nextTargetAtNanos;
        private int health;
        private int deathFrameIndex;
        private long damageFlashUntilNanos;
        private long attackStartedAtNanos;
        private float knockbackStartX;
        private float knockbackStartY;
        private float knockbackTargetX;
        private float knockbackTargetY;
        private long knockbackStartedAtNanos;
        private long knockbackEndsAtNanos;
        private long deathStartedAtNanos;
        private long respawnAtNanos;

        private void resetTransientState() {
            health = AGGRO_HEALTH;
            deathFrameIndex = 0;
            damageFlashUntilNanos = 0L;
            attackStartedAtNanos = 0L;
            knockbackStartedAtNanos = 0L;
            knockbackEndsAtNanos = 0L;
            deathStartedAtNanos = 0L;
            respawnAtNanos = 0L;
        }
    }

    private static final class Fight {
        private final MiniMeDefinition first;
        private final MiniMeDefinition second;
        private final long startedAtNanos;
        private MiniMeDefinition nextAttacker;
        private long lastUpdatedAtNanos;
        private long nextHitAtNanos;

        private Fight(MiniMeDefinition first, MiniMeDefinition second,
                      MiniMeDefinition nextAttacker, long startedAtNanos, long nextHitAtNanos) {
            this.first = first;
            this.second = second;
            this.nextAttacker = nextAttacker;
            this.startedAtNanos = startedAtNanos;
            this.lastUpdatedAtNanos = startedAtNanos;
            this.nextHitAtNanos = nextHitAtNanos;
        }

        private boolean contains(MiniMeDefinition miniMe) {
            return miniMe == first || miniMe == second;
        }

        private MiniMeDefinition other(MiniMeDefinition miniMe) {
            return miniMe == first ? second : first;
        }
    }

    private static final class SnowFight {
        private final List<SnowParticipant> participants;
        private final long deadlineAtNanos;
        private long finishAtNanos;

        private SnowFight(List<SnowParticipant> participants, long deadlineAtNanos) {
            this.participants = participants;
            this.deadlineAtNanos = deadlineAtNanos;
        }

        private boolean anyThrowsLeft() {
            for (SnowParticipant participant : participants) {
                if (participant.throwsRemaining > 0) return true;
            }
            return false;
        }

        private boolean contains(MiniMeDefinition miniMe) {
            for (SnowParticipant participant : participants) {
                if (participant.miniMe == miniMe) return true;
            }
            return false;
        }

        /** A participant shows what it is about to throw for the last moments before each throw. */
        private Projectile heldProjectile(MiniMeDefinition miniMe, long now) {
            for (SnowParticipant participant : participants) {
                if (participant.miniMe == miniMe) {
                    if (participant.throwsRemaining <= 0) return null;
                    return now >= participant.nextThrowAtNanos - SNOWBALL_HOLD_WINDOW_NANOS
                            ? participant.nextProjectile : null;
                }
            }
            return null;
        }
    }

    private enum Projectile {
        SNOWBALL(SNOWBALL_SIZE, SNOWBALL_SIZE),
        ICE_BLOCK(10, 10),
        ANVIL(14, 10);

        private final int width;
        private final int height;

        Projectile(int width, int height) {
            this.width = width;
            this.height = height;
        }
    }

    private static final class SnowParticipant {
        private final MiniMeDefinition miniMe;
        private long nextThrowAtNanos;
        private int throwsRemaining;
        private MiniMeDefinition target;
        private Projectile nextProjectile = Projectile.SNOWBALL;

        private SnowParticipant(MiniMeDefinition miniMe) {
            this.miniMe = miniMe;
        }
    }

    private record Bounds(float minX, float minY, float maxX, float maxY) {
    }

    private record Point(float x, float y) {
    }

    private record SmokeParticle(float x, float y, float velocityX, float velocityY,
                                 int size, long spawnedAtNanos, long lifetimeNanos) {
    }

    private record CriticalHitParticle(float x, float y, float velocityX, float velocityY,
                                       int size, long spawnedAtNanos) {
    }

    private record Snowball(MiniMeDefinition thrower, MiniMeDefinition target, Projectile type,
                            float startX, float startY, long spawnedAtNanos,
                            long flightNanos, float arcHeight) {
    }

    private record SnowBurstParticle(float x, float y, float velocityX, float velocityY,
                                     int size, int color, long spawnedAtNanos) {
    }
}