package madoku.craft.data;

import com.google.gson.JsonObject;
import madoku.craft.MadokuCraft;
import madoku.craft.config.StaticJsonSystem;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class MadokuData {
	private static final Logger LOGGER = LoggerFactory.getLogger(MadokuData.class);

	private MadokuData() {
	}

	public static Path createWorldData(MinecraftServer server, String folderName, String jsonName, JsonObject initialData) {
		Path directory = resolveWorldDirectory(server, folderName, true);
		Path file = resolveJsonFile(directory, jsonName);
		if (Files.isRegularFile(file)) {
			return file;
		}

		try {
			StaticJsonSystem.writeJsonFile(file, initialData == null ? new JsonObject() : initialData);
		} catch (IOException exception) {
			LOGGER.error("Failed to create world data file {}", file, exception);
		}

		return file;
	}

	public static JsonObject loadWorldData(MinecraftServer server, String folderName, String jsonName) {
		Path file = resolveJsonFile(resolveWorldDirectory(server, folderName, false), jsonName);
		if (!Files.isRegularFile(file)) {
			return null;
		}

		try {
			return StaticJsonSystem.readJsonFile(file);
		} catch (IOException exception) {
			LOGGER.error("Failed to load world data file {}", file, exception);
			return null;
		}
	}

	public static void saveWorldData(MinecraftServer server, String folderName, String jsonName, JsonObject data) {
		Path directory = resolveWorldDirectory(server, folderName, true);
		Path file = resolveJsonFile(directory, jsonName);

		try {
			StaticJsonSystem.writeJsonFile(file, data == null ? new JsonObject() : data);
		} catch (IOException exception) {
			LOGGER.error("Failed to save world data file {}", file, exception);
		}
	}

	public static void deleteWorldData(MinecraftServer server, String folderName, String jsonName) {
		Path file = resolveJsonFile(resolveWorldDirectory(server, folderName, false), jsonName);
		if (!Files.exists(file)) {
			return;
		}

		try {
			Files.delete(file);
		} catch (IOException exception) {
			LOGGER.error("Failed to delete world data file {}", file, exception);
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

		Path root = StaticJsonSystem.getWorldRootDirectory(server);
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
