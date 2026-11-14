package com.duperknight.client.gui.widgets;

import com.duperknight.client.utils.DMLSConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Drifting dots joined by faint lines. Dots with more close neighbours turn from pale blue to lime,
 * grow slightly, and move faster. Call {@link #render} from a screen's background pass.
 *
 * <p>The simulation runs in GUI coordinates, so it resizes with the window. Node count and link
 * distance are derived from the screen area so the look stays the same at any GUI scale.
 */
public final class NodeNetworkBackground {
    private static final float TARGET_NEIGHBOURS = 4.0F;   // average links per node, sets link distance
    private static final float AREA_PER_NODE = 2500.0F;    // GUI px² per node
    private static final int MIN_NODES = 20;
    private static final int MAX_NODES = 140;

    private static final float HEAT_SENSITIVITY = 0.5F;    // higher = dots turn lime with fewer neighbours
    private static final float HEAT_SMOOTHING = 0.08F;     // per 1/60 s; lower = slower colour changes
    private static final float HEAT_SPEED_BOOST = 2.5F;    // hot dots move up to 3.5x faster (1 + 2.5)

    private static final float LINE_ALPHA = 0.22F;
    private static final float DOT_BASE_ALPHA = 0.5F;
    private static final float DOT_HEAT_ALPHA = 0.4F;
    private static final float DOT_BASE_RADIUS = 1.8F;     // in real screen pixels
    private static final float DOT_HEAT_RADIUS = 1.4F;
    // Max drift per axis as a fraction of screen width per second (about 9 px/s on a 1920px screen).
    private static final float DRIFT = 0.3F * 60.0F / 1920.0F;

    // Cool = pale icy blue, hot = lime. Each is { hue (degrees), saturation (0-1), lightness (0-1) }.
    private static final float[] COOL = {200.0F, 0.75F, 0.88F};
    private static final float[] HOT = {85.0F, 0.60F, 0.72F};

    private static final int MAX_LINKS = 4096;

    private static final Random RANDOM = new Random();
    private static final List<Node> NODES = new ArrayList<>();
    private static final int[] LINK_A = new int[MAX_LINKS];
    private static final int[] LINK_B = new int[MAX_LINKS];
    private static final float[] LINK_C = new float[MAX_LINKS];
    private static float[] target = new float[0];

    private static int width = -1;
    private static int height = -1;
    private static float linkDistance;
    private static long lastNanos;

    private NodeNetworkBackground() {
    }

    public static void render(GuiGraphicsExtractor context) {
        if (!DMLSConfig.nodeNetworkBackground()) return;
        int w = context.guiWidth();
        int h = context.guiHeight();
        if (w <= 0 || h <= 0) return;

        long now = System.nanoTime();
        if (w != width || h != height) {
            init(w, h);
            lastNanos = now;
        }
        float deltaSeconds = Math.clamp((now - lastNanos) / 1_000_000_000.0F, 0.0F, 0.1F);
        lastNanos = now;

        // Real screen pixels per GUI pixel, so lines and dots stay hairline-thin at any GUI scale.
        float ratio = Math.max(1.0F, Minecraft.getInstance().getWindow().getWidth() / (float) w);
        int count = NODES.size();

        for (Node node : NODES) {
            float speed = 1.0F + HEAT_SPEED_BOOST * node.heat;
            node.x = wrap(node.x + node.vx * speed * w * deltaSeconds, w);
            node.y = wrap(node.y + node.vy * speed * w * deltaSeconds, h);
        }

        // Pass 1: find nearby pairs and total up how "crowded" each node is.
        java.util.Arrays.fill(target, 0.0F);
        int links = 0;
        for (int i = 0; i < count; i++) {
            Node a = NODES.get(i);
            for (int j = i + 1; j < count; j++) {
                Node b = NODES.get(j);
                float dx = a.x - b.x;
                float dy = a.y - b.y;
                if (Math.abs(dx) >= linkDistance || Math.abs(dy) >= linkDistance) continue;
                float distance = (float) Math.sqrt(dx * dx + dy * dy);
                if (distance >= linkDistance) continue;
                float closeness = 1.0F - distance / linkDistance; // 0 = far, 1 = touching
                target[i] += closeness;
                target[j] += closeness;
                if (links < MAX_LINKS) {
                    LINK_A[links] = i;
                    LINK_B[links] = j;
                    LINK_C[links] = closeness;
                    links++;
                }
            }
        }

        // Ease each node's heat toward its target so colours don't flicker.
        float smoothing = 1.0F - (float) Math.pow(1.0F - HEAT_SMOOTHING, deltaSeconds * 60.0F);
        for (int i = 0; i < count; i++) {
            Node node = NODES.get(i);
            float wanted = Math.min(1.0F, target[i] * HEAT_SENSITIVITY);
            node.heat += (wanted - node.heat) * smoothing;
        }

        float inverse = 1.0F / ratio;

        // Pass 2: lines, coloured by the average heat of their two ends.
        for (int index = 0; index < links; index++) {
            Node a = NODES.get(LINK_A[index]);
            Node b = NODES.get(LINK_B[index]);
            float dx = b.x - a.x;
            float dy = b.y - a.y;
            int length = Math.max(1, Math.round((float) Math.sqrt(dx * dx + dy * dy) * ratio));
            int color = color((a.heat + b.heat) * 0.5F, LINE_ALPHA * LINK_C[index]);
            context.pose().pushMatrix();
            context.pose().translate(a.x, a.y);
            context.pose().rotate((float) Math.atan2(dy, dx));
            context.pose().scale(inverse, inverse);
            context.fill(0, 0, length, 1, color);
            context.pose().popMatrix();
        }

        // Pass 3: dots (hotter ones are slightly bigger and brighter).
        for (Node node : NODES) {
            int half = Math.max(2, Math.round(DOT_BASE_RADIUS + DOT_HEAT_RADIUS * node.heat));
            int color = color(node.heat, DOT_BASE_ALPHA + DOT_HEAT_ALPHA * node.heat);
            context.pose().pushMatrix();
            context.pose().translate(node.x, node.y);
            context.pose().scale(inverse, inverse);
            // A plus-shaped pair of rectangles gives a rounded-looking dot at small sizes.
            context.fill(-half + 1, -half, half - 1, half, color);
            context.fill(-half, -half + 1, half, half - 1, color);
            context.pose().popMatrix();
        }
    }

    private static void init(int w, int h) {
        width = w;
        height = h;
        float area = (float) w * h;
        int count = Math.clamp(Math.round(area / AREA_PER_NODE), MIN_NODES, MAX_NODES);
        linkDistance = (float) Math.sqrt(TARGET_NEIGHBOURS * area / (Math.PI * count));
        target = new float[count];
        NODES.clear();
        for (int index = 0; index < count; index++) {
            Node node = new Node();
            node.x = RANDOM.nextFloat() * w;
            node.y = RANDOM.nextFloat() * h;
            node.vx = (RANDOM.nextFloat() - 0.5F) * DRIFT;
            node.vy = (RANDOM.nextFloat() - 0.5F) * DRIFT;
            NODES.add(node);
        }
    }

    private static float wrap(float value, float size) {
        if (value < 0.0F) return value + size;
        if (value >= size) return value - size;
        return value;
    }

    /** Blends COOL to HOT by {@code heat} and returns an ARGB colour. */
    private static int color(float heat, float alpha) {
        float hue = COOL[0] + (HOT[0] - COOL[0]) * heat;
        float saturation = COOL[1] + (HOT[1] - COOL[1]) * heat;
        float lightness = COOL[2] + (HOT[2] - COOL[2]) * heat;
        int rgb = hslToRgb(hue, saturation, lightness);
        int a = Math.clamp(Math.round(alpha * 255.0F), 0, 255);
        return a << 24 | rgb;
    }

    private static int hslToRgb(float hue, float saturation, float lightness) {
        float chroma = (1.0F - Math.abs(2.0F * lightness - 1.0F)) * saturation;
        float sector = (hue % 360.0F) / 60.0F;
        float x = chroma * (1.0F - Math.abs(sector % 2.0F - 1.0F));
        float r;
        float g;
        float b;
        if (sector < 1.0F) { r = chroma; g = x; b = 0.0F; }
        else if (sector < 2.0F) { r = x; g = chroma; b = 0.0F; }
        else if (sector < 3.0F) { r = 0.0F; g = chroma; b = x; }
        else if (sector < 4.0F) { r = 0.0F; g = x; b = chroma; }
        else if (sector < 5.0F) { r = x; g = 0.0F; b = chroma; }
        else { r = chroma; g = 0.0F; b = x; }
        float match = lightness - chroma / 2.0F;
        int red = Math.round((r + match) * 255.0F);
        int green = Math.round((g + match) * 255.0F);
        int blue = Math.round((b + match) * 255.0F);
        return red << 16 | green << 8 | blue;
    }

    private static final class Node {
        private float x;
        private float y;
        private float vx;
        private float vy;
        private float heat;
    }
}