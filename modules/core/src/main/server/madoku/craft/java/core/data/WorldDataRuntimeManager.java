package madoku.craft.java.core.data;

import com.google.gson.JsonObject;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;

import java.util.LinkedHashMap;
import java.util.Map;

/** Internal runtime for indexed global world data backed by vanilla SavedData. */
final class WorldDataRuntimeManager {
	private static final String FIELD_SYSTEMS = "systems";
	private static final Map<String, JsonObject> SYSTEM_DATA = new LinkedHashMap<>();
	private static volatile MinecraftServer currentServer;
	private static volatile boolean initialized;

	private WorldDataRuntimeManager() { }

	public static void initialize() {
		SYSTEM_DATA.clear();
		currentServer = null;
		initialized = true;
	}

	public static void reset() {
		SYSTEM_DATA.clear();
		currentServer = null;
		initialized = false;
	}

	public static boolean isInitialized() { return initialized; }

	public static void loadPersistedData(MinecraftServer server) {
		if (server == null) return;
		currentServer = server;
		SYSTEM_DATA.clear();
		MadokuSavedData savedData = MadokuSavedDataManager.world(server);
		CompoundTag root = savedData.copyData();
		CompoundTag systems = root.getCompoundOrEmpty(FIELD_SYSTEMS);
		for (Map.Entry<String, net.minecraft.nbt.Tag> entry : systems.entrySet()) {
			if (entry.getValue() instanceof CompoundTag compound) {
				SYSTEM_DATA.put(normalizeSystemId(entry.getKey()), MadokuSavedDataManager.toJson(compound));
			}
		}
	}

	public static void onServerStarted(MinecraftServer server) {
		if (server != null) currentServer = server;
	}

	public static void autosavePersistedData(MinecraftServer server) {
		// SavedDataStorage owns autosave scheduling. Mutations are marked dirty immediately.
	}

	public static void savePersistedData(MinecraftServer server) {
		if (server != null) syncSavedData(server);
	}

	public static JsonObject getSystemData(String systemId) {
		JsonObject data = SYSTEM_DATA.get(normalizeSystemId(systemId));
		return data == null ? new JsonObject() : data.deepCopy();
	}

	public static void setSystemData(String systemId, JsonObject source) {
		String normalizedSystemId = normalizeSystemId(systemId);
		if (normalizedSystemId.isBlank()) return;
		JsonObject safeData = source == null ? new JsonObject() : source.deepCopy();
		JsonObject previous = SYSTEM_DATA.put(normalizedSystemId, safeData);
		if (previous == null || !previous.equals(safeData)) {
			if (currentServer != null) syncSavedData(currentServer);
		}
	}

	private static void syncSavedData(MinecraftServer server) {
		MadokuSavedData savedData = MadokuSavedDataManager.world(server);
		CompoundTag root = savedData.copyData();
		CompoundTag systems = new CompoundTag();
		for (Map.Entry<String, JsonObject> entry : SYSTEM_DATA.entrySet()) {
			if (entry.getKey() != null && entry.getValue() != null) {
				systems.put(entry.getKey(), MadokuSavedDataManager.toNbt(entry.getValue()));
			}
		}
		root.put(FIELD_SYSTEMS, systems);
		savedData.replaceData(root);
	}

	private static String normalizeSystemId(String systemId) {
		return systemId == null ? "" : systemId.trim().toLowerCase(java.util.Locale.ROOT);
	}
}
