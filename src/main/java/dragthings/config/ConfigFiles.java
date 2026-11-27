package dragthings.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import dragthings.Dragthings;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Reads and writes the JSON files in config/dragthings/.
 *
 * - A missing file is created with the defaults.
 * - A field missing from a file takes its default (new options appear after an update).
 * - Values are clamped by each config's own sanitize(), then the file is
 *   written back, so it always shows the values that are really in use.
 * - A file that cannot be parsed is renamed to <name>.broken (never overwritten)
 *   and the defaults are used.
 */
public final class ConfigFiles {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private ConfigFiles() {}

    public static Path dir() {
        return FabricLoader.getInstance().getConfigDir().resolve("dragthings");
    }

    public static <T> T load(String fileName, Class<T> type, Supplier<T> defaults, Consumer<T> sanitize) {
        Path file = dir().resolve(fileName);
        T value = null;

        if (Files.exists(file)) {
            try (Reader reader = Files.newBufferedReader(file)) {
                value = GSON.fromJson(reader, type);
            } catch (IOException | JsonParseException e) {
                Dragthings.LOGGER.warn("Could not read {} ({}); keeping a copy as {}.broken and using defaults",
                        fileName, e.getMessage(), fileName);
                try {
                    Files.move(file, file.resolveSibling(fileName + ".broken"), StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException ignored) { }
            }
        }
        if (value == null) value = defaults.get();

        sanitize.accept(value);
        save(fileName, value);
        return value;
    }

    public static void save(String fileName, Object value) {
        try {
            Files.createDirectories(dir());
            try (Writer writer = Files.newBufferedWriter(dir().resolve(fileName))) {
                GSON.toJson(value, writer);
            }
        } catch (IOException e) {
            Dragthings.LOGGER.warn("Could not write {}: {}", fileName, e.getMessage());
        }
    }

    // ── Clamp helpers for sanitize() ──────────────────────────────────────
    public static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }
    public static float clamp(float v, float lo, float hi) { return Math.max(lo, Math.min(hi, v)); }
    public static double clamp(double v, double lo, double hi) { return Math.max(lo, Math.min(hi, v)); }
}
