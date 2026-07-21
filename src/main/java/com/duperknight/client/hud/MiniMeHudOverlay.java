package com.duperknight.client.hud;

import com.duperknight.DMLS;
import com.duperknight.client.modules.MiniMeHudPreferences;
import com.duperknight.client.utils.DMLSConfig;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.resource.ResourceType;
import net.minecraft.resource.SynchronousResourceReloader;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.EnumMap;
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
            Identifier.of("minecraft", "textures/particle/critical_hit.png");
    private static final long KNOCKBACK_NANOS = 240_000_000L;
    private static final float KNOCKBACK_DISTANCE_FACTOR = 0.26F;
    private static final long DEATH_ANIMATION_NANOS = 900_000_000L;
    private static final long RESPAWN_DELAY_NANOS = 5_000_000_000L;
    private static final int SMOKE_PARTICLE_COUNT = 28;
    private static final long SMOKE_LIFETIME_NANOS = 850_000_000L;
    private static final List<Identifier> SMOKE_TEXTURES = List.of(
            Identifier.of("minecraft", "textures/particle/big_smoke_0.png"),
            Identifier.of("minecraft", "textures/particle/big_smoke_1.png"),
            Identifier.of("minecraft", "textures/particle/big_smoke_2.png"),
            Identifier.of("minecraft", "textures/particle/big_smoke_3.png"),
            Identifier.of("minecraft", "textures/particle/big_smoke_4.png"),
            Identifier.of("minecraft", "textures/particle/big_smoke_5.png"),
            Identifier.of("minecraft", "textures/particle/big_smoke_6.png"),
            Identifier.of("minecraft", "textures/particle/big_smoke_7.png"),
            Identifier.of("minecraft", "textures/particle/big_smoke_8.png"),
            Identifier.of("minecraft", "textures/particle/big_smoke_9.png"),
            Identifier.of("minecraft", "textures/particle/big_smoke_10.png"),
            Identifier.of("minecraft", "textures/particle/big_smoke_11.png")
    );
    private static final Random RANDOM = new Random();
    private static final Map<MiniMeDefinition, WalkerState> STATES = new EnumMap<>(MiniMeDefinition.class);
    private static final List<SmokeParticle> SMOKE_PARTICLES = new ArrayList<>();
    private static final List<CriticalHitParticle> CRITICAL_HIT_PARTICLES = new ArrayList<>();

    private static int screenWidth = -1;
    private static int screenHeight = -1;
    private static ClientWorld activeWorld;
    private static Fight activeFight;
    private static long nextAggroCheckAtNanos;
    private static boolean chaosMode;

    static {
        for (MiniMeDefinition miniMe : MiniMeDefinition.values()) {
            STATES.put(miniMe, new WalkerState());
        }
    }

    private MiniMeHudOverlay() {
    }

    public static void register() {
        HudElementRegistry.addLast(Identifier.of(DMLS.MOD_ID.toLowerCase(), "walking_mini_mes"),
                (context, tickCounter) -> render(context));
        ResourceLoader.get(ResourceType.CLIENT_RESOURCES).registerReloader(
                Identifier.of(DMLS.MOD_ID.toLowerCase(), "mini_me_frames"),
                (SynchronousResourceReloader) resourceManager -> invalidateFrameCache());
    }

    private static void invalidateFrameCache() {
        for (MiniMeDefinition miniMe : MiniMeDefinition.values()) {
            miniMe.invalidateFrames();
        }
    }

    private static void render(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        long now = System.nanoTime();
        int width = context.getScaledWindowWidth();
        int height = context.getScaledWindowHeight();

        if (client.world != activeWorld) {
            activeWorld = client.world;
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

        boolean visibleUnderScreen = client.currentScreen == null
                || client.currentScreen instanceof ChatScreen
                || client.currentScreen instanceof HandledScreen<?>;
        if (client.world == null || client.player == null || !visibleUnderScreen
                || client.options.hudHidden || width <= 0 || height <= 0) {
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

        validateFight(enabledMiniMes, now);
        maybeStartFight(enabledMiniMes, now);
        updateFight(bounds, size, now);

        for (MiniMeDefinition miniMe : enabledMiniMes) {
            WalkerState state = STATES.get(miniMe);
            if (state.respawnAtNanos > 0L
                    && now >= state.deathStartedAtNanos + DEATH_ANIMATION_NANOS) continue;
            boolean dying = state.deathStartedAtNanos > 0L;
            if (!dying && (activeFight == null || !activeFight.contains(miniMe))) {
                update(state, bounds, now);
            }
            keepInBounds(state, bounds);
            draw(context, miniMe, state, size, now);
        }
        renderSmoke(context, now);
        renderCriticalHitParticles(context, now);
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
        if (activeFight != null || now < nextAggroCheckAtNanos) return;
        scheduleNextAggroCheck(now);
        List<MiniMeDefinition> candidates = enabledMiniMes.stream()
                .filter(miniMe -> STATES.get(miniMe).respawnAtNanos == 0L)
                .toList();
        float aggroChance = chaosMode ? CHAOS_AGGRO_CHANCE : NORMAL_AGGRO_CHANCE;
        if (candidates.size() < 2 || RANDOM.nextFloat() >= aggroChance) return;

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

    private static void renderSmoke(DrawContext context, long now) {
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
            context.drawTexturedQuad(SMOKE_TEXTURES.get(frame),
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

    private static void renderCriticalHitParticles(DrawContext context, long now) {
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
            context.drawTexturedQuad(CRITICAL_HIT_TEXTURE,
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
        long minimum = chaosMode ? CHAOS_AGGRO_CHECK_MIN_NANOS : NORMAL_AGGRO_CHECK_MIN_NANOS;
        long variation = chaosMode
                ? CHAOS_AGGRO_CHECK_VARIATION_NANOS : NORMAL_AGGRO_CHECK_VARIATION_NANOS;
        nextAggroCheckAtNanos = now + minimum + nextLong(variation + 1L);
    }

    private static void draw(DrawContext context, MiniMeDefinition miniMe, WalkerState state, int size, long now) {
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
                Math.max(0.0F, context.getScaledWindowWidth() - frameWidth));
        float top = Math.clamp(state.y - hop, 0.0F,
                Math.max(0.0F, context.getScaledWindowHeight() - frameHeight));
        float u1 = state.facingRight ? 0.0F : 1.0F;
        float u2 = state.facingRight ? 1.0F : 0.0F;
        Identifier texture = dying || now < state.damageFlashUntilNanos
                ? frame.damageTexture : frame.texture;
        context.getMatrices().pushMatrix();
        if (dying) {
            float deathProgress = Math.clamp((now - state.deathStartedAtNanos)
                    / (float) DEATH_ANIMATION_NANOS, 0.0F, 1.0F);
            float easedProgress = deathProgress * deathProgress * (3.0F - 2.0F * deathProgress);
            float rotation = (float) Math.toRadians(90.0F * easedProgress);
            context.getMatrices().translate(left + frameWidth / 2.0F, top + frameHeight);
            context.getMatrices().rotate(rotation);
            context.getMatrices().scale(scaleX, scaleY);
            context.drawTexturedQuad(texture, -frame.width / 2, -frame.height,
                    frame.width / 2, 0, u1, u2, 0.0F, 1.0F);
        } else {
            context.getMatrices().translate(left, top);
            context.getMatrices().scale(scaleX, scaleY);
            context.drawTexturedQuad(texture, 0, 0, frame.width, frame.height,
                    u1, u2, 0.0F, 1.0F);
        }
        context.getMatrices().popMatrix();
    }

    private static void reanchorForResize(int previousWidth, int previousHeight,
                                          int width, int height, long now) {
        endFightWithoutWinner(now);
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
        SMOKE_PARTICLES.clear();
        CRITICAL_HIT_PARTICLES.clear();
        scheduleNextAggroCheck(System.nanoTime());
    }

    private enum MiniMeDefinition {
        DUPEY("dupey"),
        SIAFFY("siaffy"),
        BEANY("beany"),
        MORVY("morvy"),
        BIGGY("biggy");

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
            };
        }

        private static Identifier walkingTextureId(String miniMe, String frame) {
            return Identifier.of(DMLS.MOD_ID.toLowerCase(),
                    "textures/gui/mini_me/%s/walking/%s_walking_%s.png"
                            .formatted(miniMe, miniMe, frame));
        }

        private static Identifier hittingTextureId(String miniMe) {
            return Identifier.of(DMLS.MOD_ID.toLowerCase(),
                    "textures/gui/mini_me/%s/hitting/%s_hitting.png"
                            .formatted(miniMe, miniMe));
        }

        private static MiniMeFrame loadFrame(Identifier texture) {
            var resource = MinecraftClient.getInstance().getResourceManager().getResource(texture);
            if (resource.isEmpty()) return null;
            try (InputStream stream = resource.get().getInputStream()) {
                try (NativeImage image = NativeImage.read(stream)) {
                    NativeImage damageImage = image.applyToCopy(MiniMeHudOverlay::damageTint);
                    Identifier damageTexture = Identifier.of(DMLS.MOD_ID.toLowerCase(),
                            "mini_me_damage/" + texture.getPath().replace('/', '_'));
                    NativeImageBackedTexture backedTexture = new NativeImageBackedTexture(
                            () -> "DMLS AdMinis damage texture " + texture, damageImage);
                    MinecraftClient.getInstance().getTextureManager().registerTexture(damageTexture, backedTexture);
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
}
