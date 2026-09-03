package madoku.craft.attributes;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import madoku.craft.core.json.JSONFormatAPIManager;
import madoku.craft.core.sync.SyncConfigAPIManager;

public final class MadokuAttributesManager {
	private static volatile AttributesConfigManager.Settings settings = AttributesConfigManager.Settings.defaults();
	private static volatile Boolean clientSynchronizedEnabled;

	private MadokuAttributesManager() {
	}

	public static void initialize() {
		loadStaticConfig();
		MadokuExperienceManager.initialize();
		MadokuArmorManager.initialize();
		MadokuHealthManager.initialize();
		MadokuHungerManager.initialize();
		MadokuOxygenManager.initialize();
		MadokuLuckManager.initialize();
		SyncConfigAPIManager.register(
			"attributes",
			MadokuAttributesManager::createClientSyncSnapshot,
			MadokuAttributesManager::applyClientSyncSnapshot,
			MadokuAttributesManager::resetClientSyncState
		);
	}

	public static boolean isEnabled() {
		Boolean synchronizedEnabled = clientSynchronizedEnabled;
		return synchronizedEnabled == null ? settings.enabled : synchronizedEnabled;
	}

	public static String createClientSyncSnapshot() {
		return JSONFormatAPIManager.object()
			.put("enabled", settings.enabled)
			.object("hunger", hunger -> hunger
				.put("enabled", MadokuHungerManager.isEnabled())
				.put("max", MadokuHungerManager.getConfiguredMaximumHungerPoints()))
			.object("oxygen", oxygen -> oxygen
				.put("enabled", MadokuOxygenManager.isEnabled())
				.put("max", MadokuOxygenManager.getMaximumOxygenTicksForEntity(null)))
			.put("luck-enabled", MadokuLuckManager.isEnabled())
			.build()
			.toString();
	}

	public static void applyClientSyncSnapshot(String snapshot) {
		JsonObject root = JsonParser.parseString(snapshot == null ? "" : snapshot).getAsJsonObject();
		boolean enabled = readBoolean(root, "enabled", settings.enabled);
		JsonObject hunger = readObject(root, "hunger");
		JsonObject oxygen = readObject(root, "oxygen");
		clientSynchronizedEnabled = enabled;
		MadokuHungerManager.applyClientSynchronizedSettings(
			readBoolean(hunger, "enabled", MadokuHungerManager.isEnabled()),
			readInt(hunger, "max", MadokuHungerManager.getConfiguredMaximumHungerPoints())
		);
		MadokuOxygenManager.applyClientSynchronizedSettings(
			readBoolean(oxygen, "enabled", MadokuOxygenManager.isEnabled()),
			readInt(oxygen, "max", MadokuOxygenManager.getMaximumOxygenTicksForEntity(null))
		);
		MadokuLuckManager.applyClientSynchronizedEnabled(
			readBoolean(root, "luck-enabled", MadokuLuckManager.isEnabled())
		);
	}

	public static void resetClientSyncState() {
		clientSynchronizedEnabled = null;
		MadokuHungerManager.resetClientSynchronizedSettings();
		MadokuOxygenManager.resetClientSynchronizedSettings();
		MadokuLuckManager.resetClientSynchronizedSettings();
	}

	private static JsonObject readObject(JsonObject source, String key) {
		if (source == null || !source.has(key) || !source.get(key).isJsonObject()) return new JsonObject();
		return source.getAsJsonObject(key);
	}

	private static boolean readBoolean(JsonObject source, String key, boolean fallback) {
		try { return source != null && source.has(key) ? source.get(key).getAsBoolean() : fallback; }
		catch (RuntimeException ignored) { return fallback; }
	}

	private static int readInt(JsonObject source, String key, int fallback) {
		try { return source != null && source.has(key) ? Math.max(1, source.get(key).getAsInt()) : fallback; }
		catch (RuntimeException ignored) { return fallback; }
	}

	private static void loadStaticConfig() {
		settings = AttributesConfigManager.loadSettings();
	}
}


