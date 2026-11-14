package com.duperknight.client.modules;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * User-made minis, stored in {@code config/dmls/custom_minis/<id>/}:
 * <pre>
 *   mini.properties      name=My Mini
 *   idle.png
 *   walking_00.png ...   walking_NN.png
 *   hitting_00.png ...   hitting_NN.png
 * </pre>
 * Every edit is written to disk straight away and then the whole set is reloaded. Call from the client thread only.
 */
public final class CustomMinis {
    /** TODO: set to your mod id / texture namespace. */
    private static final String MOD_ID = "dmls";
    private static final Logger LOGGER = LoggerFactory.getLogger("dmls");

    public static final int MAX_FRAMES = 12;
    public static final int MAX_DIMENSION = 2048;

    private static final String META_FILE = "mini.properties";
    private static final Pattern ID_PATTERN = Pattern.compile("[a-z0-9_-]+");

    /** The three image groups every custom mini has. */
    public enum Group {
        IDLE, WALKING, HITTING;

        String prefix() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** One image on disk plus the runtime texture it was registered as. */
    public record Sprite(Path file, Identifier texture, int width, int height) {
    }

    public record CustomMini(String id, String name, Sprite idle, List<Sprite> walking, List<Sprite> hitting) {
        /** A mini needs at least one idle or walking image before it can appear on the HUD. */
        public boolean usable() {
            return idleSprite() != null;
        }

        /** The standing image; falls back to the first walking frame. */
        public Sprite idleSprite() {
            if (idle != null) return idle;
            return walking.isEmpty() ? null : walking.get(0);
        }

        /** The walking frames; falls back to the idle image so the mini still shows. */
        public List<Sprite> walkingSprites() {
            if (!walking.isEmpty()) return walking;
            return idle == null ? List.of() : List.of(idle);
        }
    }

    private static final List<Identifier> REGISTERED = new ArrayList<>();
    private static final Map<Identifier, int[]> SIZES = new HashMap<>();
    private static List<CustomMini> minis = List.of();
    private static boolean loaded;
    private static int version;

    private CustomMinis() {
    }

    public static List<CustomMini> all() {
        ensureLoaded();
        return minis;
    }

    /** Increases every time the set of minis or their images change. */
    public static int version() {
        ensureLoaded();
        return version;
    }

    public static CustomMini find(String id) {
        for (CustomMini mini : all()) {
            if (mini.id().equals(id)) return mini;
        }
        return null;
    }

    /** Pixel size of a custom texture, or null if the texture is not a custom one. */
    public static int[] sizeOf(Identifier texture) {
        ensureLoaded();
        return SIZES.get(texture);
    }

    // ---------------------------------------------------------------- editing

    /** Creates an empty mini and returns its id, or null on failure. */
    public static String create(String name) {
        Path root = root();
        long stamp = System.currentTimeMillis();
        String id;
        do {
            id = "m" + Long.toString(stamp++, 36);
        } while (Files.exists(root.resolve(id)));

        Path dir = root.resolve(id);
        try {
            Files.createDirectories(dir);
        } catch (IOException exception) {
            LOGGER.warn("Could not create {}", dir, exception);
            return null;
        }
        if (!writeName(dir, name)) return null;
        reload();
        return id;
    }

    public static boolean rename(String id, String name) {
        Path dir = existingDir(id);
        if (dir == null || !writeName(dir, name)) return false;
        reload();
        return true;
    }

    public static boolean delete(String id) {
        Path dir = existingDir(id);
        if (dir == null) return false;
        try (Stream<Path> walk = Files.walk(dir)) {
            List<Path> paths = walk.sorted(Comparator.reverseOrder()).toList();
            for (Path path : paths) {
                Files.delete(path);
            }
        } catch (IOException exception) {
            LOGGER.warn("Could not delete {}", dir, exception);
            reload();
            return false;
        }
        reload();
        return true;
    }

    public static boolean setIdle(String id, Path source) {
        Path dir = existingDir(id);
        if (dir == null || !validImage(source)) return false;
        try {
            Files.copy(source, dir.resolve("idle.png"), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            LOGGER.warn("Could not copy {}", source, exception);
            return false;
        }
        reload();
        return true;
    }

    public static boolean clearIdle(String id) {
        Path dir = existingDir(id);
        if (dir == null) return false;
        try {
            Files.deleteIfExists(dir.resolve("idle.png"));
        } catch (IOException exception) {
            LOGGER.warn("Could not remove the idle image of {}", id, exception);
            return false;
        }
        reload();
        return true;
    }

    /** Appends each file as a new frame (in the order given). Returns how many were added. */
    public static int addFrames(String id, Group group, List<Path> sources) {
        Path dir = existingDir(id);
        if (dir == null || group == Group.IDLE) return 0;
        int count = frameCount(dir, group);
        int added = 0;
        for (Path source : sources) {
            if (count >= MAX_FRAMES) break;
            if (!validImage(source)) continue;
            try {
                Files.copy(source, frameFile(dir, group, count), StandardCopyOption.REPLACE_EXISTING);
                count++;
                added++;
            } catch (IOException exception) {
                LOGGER.warn("Could not copy {}", source, exception);
            }
        }
        reload();
        return added;
    }

    public static boolean replaceFrame(String id, Group group, int index, Path source) {
        Path dir = existingDir(id);
        if (dir == null || group == Group.IDLE || index < 0 || index >= frameCount(dir, group)
                || !validImage(source)) {
            return false;
        }
        try {
            Files.copy(source, frameFile(dir, group, index), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            LOGGER.warn("Could not copy {}", source, exception);
            return false;
        }
        reload();
        return true;
    }

    /** Removes one frame and shifts the later ones down so numbering stays continuous. */
    public static boolean removeFrame(String id, Group group, int index) {
        Path dir = existingDir(id);
        if (dir == null || group == Group.IDLE) return false;
        int count = frameCount(dir, group);
        if (index < 0 || index >= count) return false;
        try {
            Files.deleteIfExists(frameFile(dir, group, index));
            for (int i = index + 1; i < count; i++) {
                Files.move(frameFile(dir, group, i), frameFile(dir, group, i - 1),
                        StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            LOGGER.warn("Could not remove frame {} of {}", index, id, exception);
            reload();
            return false;
        }
        reload();
        return true;
    }

    // ---------------------------------------------------------------- loading

    private static void ensureLoaded() {
        if (!loaded) reload();
    }

    /** Re-reads everything from disk and re-registers the textures. */
    public static void reload() {
        loaded = true;
        var textures = Minecraft.getInstance().getTextureManager();
        for (Identifier texture : REGISTERED) {
            textures.release(texture);
        }
        REGISTERED.clear();
        SIZES.clear();

        List<CustomMini> found = new ArrayList<>();
        Path root = root();
        if (Files.isDirectory(root)) {
            try (Stream<Path> children = Files.list(root)) {
                List<Path> dirs = children.filter(Files::isDirectory).sorted().toList();
                for (Path dir : dirs) {
                    CustomMini mini = readMini(dir);
                    if (mini != null) found.add(mini);
                }
            } catch (IOException exception) {
                LOGGER.warn("Could not list {}", root, exception);
            }
        }
        minis = List.copyOf(found);
        version++;
    }

    private static CustomMini readMini(Path dir) {
        String id = dir.getFileName().toString();
        if (!ID_PATTERN.matcher(id).matches()) return null;

        String name = "Custom Mini";
        Path meta = dir.resolve(META_FILE);
        if (Files.isRegularFile(meta)) {
            Properties properties = new Properties();
            try (InputStream in = Files.newInputStream(meta)) {
                properties.load(in);
                name = properties.getProperty("name", name);
            } catch (IOException exception) {
                LOGGER.warn("Could not read {}", meta, exception);
            }
        }

        Sprite idle = loadSprite(dir.resolve("idle.png"), id, "idle");
        List<Sprite> walking = loadSeries(dir, id, Group.WALKING);
        List<Sprite> hitting = loadSeries(dir, id, Group.HITTING);
        return new CustomMini(id, name, idle, List.copyOf(walking), List.copyOf(hitting));
    }

    private static List<Sprite> loadSeries(Path dir, String id, Group group) {
        List<Sprite> frames = new ArrayList<>();
        for (int i = 0; i < MAX_FRAMES; i++) {
            Path file = frameFile(dir, group, i);
            if (!Files.isRegularFile(file)) break;
            Sprite sprite = loadSprite(file, id, "%s_%02d".formatted(group.prefix(), i));
            if (sprite == null) break;
            frames.add(sprite);
        }
        return frames;
    }

    private static Sprite loadSprite(Path file, String id, String label) {
        if (!Files.isRegularFile(file)) return null;
        try (InputStream in = Files.newInputStream(file)) {
            NativeImage image = NativeImage.read(in);
            int width = image.getWidth();
            int height = image.getHeight();
            Identifier texture = Identifier.fromNamespaceAndPath(MOD_ID, "custom/" + id + "/" + label);
            DynamicTexture backed = new DynamicTexture(() -> "DMLS custom mini " + texture, image);
            Minecraft.getInstance().getTextureManager().register(texture, backed);
            REGISTERED.add(texture);
            SIZES.put(texture, new int[] {width, height});
            return new Sprite(file, texture, width, height);
        } catch (IOException | RuntimeException exception) {
            LOGGER.warn("Could not load custom mini image {}", file, exception);
            return null;
        }
    }

    // ---------------------------------------------------------------- helpers

    private static boolean validImage(Path file) {
        try (InputStream in = Files.newInputStream(file); NativeImage image = NativeImage.read(in)) {
            return image.getWidth() > 0 && image.getHeight() > 0
                    && image.getWidth() <= MAX_DIMENSION && image.getHeight() <= MAX_DIMENSION;
        } catch (IOException | RuntimeException exception) {
            LOGGER.warn("Rejected image {}", file, exception);
            return false;
        }
    }

    private static boolean writeName(Path dir, String name) {
        Properties properties = new Properties();
        properties.setProperty("name", name);
        try (OutputStream out = Files.newOutputStream(dir.resolve(META_FILE))) {
            properties.store(out, "DMLS custom mini");
            return true;
        } catch (IOException exception) {
            LOGGER.warn("Could not write the name for {}", dir, exception);
            return false;
        }
    }

    private static int frameCount(Path dir, Group group) {
        int count = 0;
        while (count < MAX_FRAMES && Files.isRegularFile(frameFile(dir, group, count))) count++;
        return count;
    }

    private static Path frameFile(Path dir, Group group, int index) {
        return dir.resolve("%s_%02d.png".formatted(group.prefix(), index));
    }

    private static Path existingDir(String id) {
        if (id == null || !ID_PATTERN.matcher(id).matches()) return null;
        Path dir = root().resolve(id);
        return Files.isDirectory(dir) ? dir : null;
    }

    private static Path root() {
        return FabricLoader.getInstance().getConfigDir().resolve("dmls").resolve("custom_minis");
    }
}