package com.duperknight.client.gui.widgets;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

import java.util.concurrent.ThreadLocalRandom;

/** DVD-screensaver style bouncing logo that never lands in a corner. */
public final class BouncingLogoBackground {
    // assets/dmls/textures/logo.png
    private static final Identifier LOGO = Identifier.fromNamespaceAndPath("dmls", "logo.png");

    // REAL pixel size of logo.png (right-click the file > Properties > Details, or open it in IntelliJ).
    // These must match the file exactly or the logo will be stretched/cropped.
    private static final int TEX_W = 256;
    private static final int TEX_H = 128;

    private static final int LOGO_H = 48;                                            // drawn height in GUI pixels
    private static final int LOGO_W = Math.round(LOGO_H * (float) TEX_W / TEX_H);    // width follows the aspect ratio
    private static final double SPEED = 55.0;                                        // GUI pixels / second
    private static final double CORNER_MARGIN = Math.max(LOGO_W, LOGO_H) * 0.75;     // min distance from a corner at a bounce

    private static double x, y, vx, vy;
    private static long lastNanos;
    private static boolean initialized;

    private BouncingLogoBackground() {}

    public static void render(GuiGraphicsExtractor context, int width, int height) {
        double maxX = Math.max(1, width - LOGO_W);
        double maxY = Math.max(1, height - LOGO_H);

        long now = System.nanoTime();
        if (!initialized) {
            var rng = ThreadLocalRandom.current();
            x = rng.nextDouble() * maxX;
            y = rng.nextDouble() * maxY;
            vx = rng.nextBoolean() ? SPEED : -SPEED;
            vy = (rng.nextBoolean() ? 1 : -1) * SPEED * (0.7 + rng.nextDouble() * 0.2);
            lastNanos = now;
            initialized = true;
            avoidCorners(maxX, maxY);
        }

        double dt = Math.min((now - lastNanos) / 1_000_000_000.0, 0.05); // clamp lag spikes
        lastNanos = now;

        // Keep inside bounds if the window was resized
        x = Math.min(Math.max(x, 0), maxX);
        y = Math.min(Math.max(y, 0), maxY);

        x += vx * dt;
        y += vy * dt;

        boolean bounced = false;
        if (x < 0)         { x = -x;               vx =  Math.abs(vx); bounced = true; }
        else if (x > maxX) { x = 2 * maxX - x;     vx = -Math.abs(vx); bounced = true; }
        if (y < 0)         { y = -y;               vy =  Math.abs(vy); bounced = true; }
        else if (y > maxY) { y = 2 * maxY - y;     vy = -Math.abs(vy); bounced = true; }

        if (bounced) avoidCorners(maxX, maxY);

        // Draw the whole texture scaled to LOGO_W x LOGO_H (same aspect ratio as the source)
        context.blit(RenderPipelines.GUI_TEXTURED, LOGO,
                (int) x, (int) y, 0f, 0f,
                LOGO_W, LOGO_H,   // size drawn on screen
                TEX_W, TEX_H,     // region of the texture to sample (all of it)
                TEX_W, TEX_H);    // full texture dimensions
    }

    /** Re-roll vertical speed until the next X and Y wall hits are far enough apart in time. */
    private static void avoidCorners(double maxX, double maxY) {
        var rng = ThreadLocalRandom.current();
        for (int i = 0; i < 32; i++) {
            double tx = timeToWall(x, vx, maxX);
            double ty = timeToWall(y, vy, maxY);
            // distance the later axis still has to its wall when the earlier axis bounces
            double gapPx = Math.abs(tx - ty) * (tx < ty ? Math.abs(vy) : Math.abs(vx));
            if (gapPx >= CORNER_MARGIN) return;

            double factor = 0.85 + rng.nextDouble() * 0.30;
            double newSpeed = Math.min(Math.max(Math.abs(vy) * factor, SPEED * 0.6), SPEED * 1.2);
            vy = Math.copySign(newSpeed, vy);
        }
    }

    private static double timeToWall(double pos, double vel, double max) {
        return vel > 0 ? (max - pos) / vel : pos / -vel;
    }
}