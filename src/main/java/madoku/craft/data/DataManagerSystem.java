package madoku.craft.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import madoku.craft.MadokuCraft;
import madoku.craft.clock.MadokuTicks;
import madoku.craft.config.JsonManagerSystem;
import madoku.craft.config.JsonStaticSystem;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class DataManagerSystem {
	private static final Logger LOGGER = LoggerFactory.getLogger(DataManagerSystem.class);
	private static final long DEFAULT_AUTO_SAVE_MINUTES = 5L;
	private static final String FIELD_AUTO_SAVE = "autoSave";
	private static final Map<Path, JsonObject> GENERAL_CACHE = new ConcurrentHashMap<>();

	private DataManagerSystem() {
	}

	public static JsonObject loadWorldData(MinecraftServer server, String folderName, String jsonName) {
		return loadWorldData(server, folderName, jsonName, new JsonObject());
	}

	public static JsonObject loadWorldData(MinecraftServer server, String folderName, String jsonName, JsonObject defaultData) {
		Path file = resolveJsonFile(resolveWorldDirectory(server, folderName, true), jsonName);
		JsonObject safeDefaultData = defaultData == null ? new JsonObject() : defaultData.deepCopy();
		boolean fileExists = Files.isRegularFile(file);

		try {
			JsonStaticSystem.ManagedStaticDocument document = JsonStaticSystem.readManagedDocument(file);
			JsonObject general = normalizeGeneral(document.general());
			JsonObject main = document.main();

			if (!fileExists || main.entrySet().isEmpty()) {
				main = safeDefaultData.deepCopy();
			}

			JsonStaticSystem.writeManagedDocument(file, main, general);
			cacheGeneral(file, general);
			return main.deepCopy();
		} catch (IOException | RuntimeException exception) {
			LOGGER.error("Failed to load world data file {}", file, exception);
			return safeDefaultData.deepCopy();
		}
	}

	public static void saveWorldData(MinecraftServer server, String folderName, String jsonName, JsonObject data) {
		Path file = resolveJsonFile(resolveWorldDirectory(server, folderName, true), jsonName);
		JsonObject main = data == null ? new JsonObject() : data.deepCopy();

		try {
			JsonObject general = normalizeGeneral(readGeneral(file));
			JsonStaticSystem.writeManagedDocument(file, main, general);
			cacheGeneral(file, general);
		} catch (IOException | RuntimeException exception) {
			LOGGER.error("Failed to save world data file {}", file, exception);
		}
	}

	public static long getAutoSaveIntervalTicks(MinecraftServer server, String folderName, String jsonName) {
		Path file = resolveJsonFile(resolveWorldDirectory(server, folderName, false), jsonName);
		JsonObject general = GENERAL_CACHE.get(file);
		if (general == null) {
			try {
				general = normalizeGeneral(readGeneral(file));
				cacheGeneral(file, general);
			} catch (IOException | RuntimeException exception) {
				LOGGER.error("Failed to read world data metadata {}", file, exception);
				return minutesToTicks(DEFAULT_AUTO_SAVE_MINUTES);
			}
		}
		return minutesToTicks(getLong(general, FIELD_AUTO_SAVE, DEFAULT_AUTO_SAVE_MINUTES));
	}

	public static void deleteWorldData(MinecraftServer server, String folderName, String jsonName) {
		Path file = resolveJsonFile(resolveWorldDirectory(server, folderName, false), jsonName);
		if (!Files.exists(file)) {
			return;
		}

		try {
			Files.delete(file);
			GENERAL_CACHE.remove(file);
		} catch (IOException exception) {
			LOGGER.error("Failed to delete world data file {}", file, exception);
		}
	}

	private static JsonObject readGeneral(Path file) throws IOException {
		JsonObject cached = GENERAL_CACHE.get(file);
		if (cached != null) {
			return cached.deepCopy();
		}
		if (!Files.isRegularFile(file)) {
			return new JsonObject();
		}
		return JsonStaticSystem.readManagedDocument(file).general();
	}

	private static JsonObject normalizeGeneral(JsonObject source) {
		JsonObject general = source == null ? new JsonObject() : source.deepCopy();
		general.remove("dataReset");
		general.remove("dataResetDay");
		long autoSave = Math.max(1L, getLong(general, FIELD_AUTO_SAVE, DEFAULT_AUTO_SAVE_MINUTES));
		general.addProperty(FIELD_AUTO_SAVE, autoSave);
		return general;
	}

	private static long minutesToTicks(long minutes) {
		long safeMinutes = Math.max(1L, minutes);
		try {
			return Math.multiplyExact(safeMinutes, MadokuTicks.SECONDS_PER_MINUTE * MadokuTicks.TICKS_PER_SECOND);
		} catch (ArithmeticException exception) {
			return DEFAULT_AUTO_SAVE_MINUTES * MadokuTicks.SECONDS_PER_MINUTE * MadokuTicks.TICKS_PER_SECOND;
		}
	}

	private static void cacheGeneral(Path file, JsonObject general) {
		if (file == null || general == null) {
			return;
		}
		GENERAL_CACHE.put(file, general.deepCopy());
	}

	private static long getLong(JsonObject object, String key, long fallback) {
		if (object == null || key == null) {
			return fallback;
		}

		JsonElement element = object.get(key);
		if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
			return fallback;
		}

		try {
			return element.getAsLong();
		} catch (RuntimeException exception) {
			return fallback;
		}
	}

	private static Path resolveJsonFile(Path directory, String jsonName) {
		String normalized = jsonName == null ? "" : jsonName.trim();
		if (normalized.isEmpty()) {
			throw new IllegalArgumentException("JSON file name must not be blank.");
		}
		if (!normalized.endsWith(".json")) {
			normalized = normalized + ".json";
		}
		return directory.resolve(normalized);
	}

	private static Path resolveWorldDirectory(MinecraftServer server, String folderName, boolean createDirectories) {
		String normalizedName = folderName == null ? "" : folderName.trim();
		if (normalizedName.isEmpty()) {
			throw new IllegalArgumentException("Folder name must not be blank.");
		}

		Path root = JsonManagerSystem.getWorldRootDirectory(server);
		boolean useRoot = MadokuCraft.MOD_ID.equals(normalizedName);
		Path directory = useRoot ? root : root.resolve(normalizedName);
		if (createDirectories) {
			try {
				Files.createDirectories(directory);
			} catch (IOException exception) {
				throw new IllegalStateException("Failed to create world data directory: " + directory, exception);
			}
		}
		return directory;
	}
}
