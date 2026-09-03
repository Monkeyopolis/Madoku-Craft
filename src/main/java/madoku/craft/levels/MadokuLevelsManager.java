package madoku.craft.levels;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Orchestrates the Madoku Levels runtime and its managed subsystems. */
public final class MadokuLevelsManager {
	private static volatile boolean initialized;

	private MadokuLevelsManager() { }

	public static void initialize() {
		if (initialized) return;
		LevelsConfigAPIManager.registerProvider(new MadokuLevelsConfigProvider());
		LevelsAttributesAPIManager.registerProvider(new MadokuLevelsAttributesProvider());
		LevelsPlayerAPIManager.registerProvider(new MadokuLevelsPlayerProvider());
		LevelsPayloadAPIManager.registerProvider(new MadokuLevelsPayloadProvider());
		LevelsConfigAPIManager.initialize();
		LevelsAttributesAPIManager.initialize();
		LevelsPlayerAPIManager.initialize();
		LevelsPayloadAPIManager.initialize();
		initialized = true;
	}

	public static void reset() {
		LevelsPayloadAPIManager.reset();
		LevelsPlayerAPIManager.reset();
		LevelsAttributesAPIManager.reset();
		LevelsConfigAPIManager.reset();
		initialized = false;
	}

	public static boolean isInitialized() { return initialized; }
	public static boolean isEnabled() { return LevelsConfigAPIManager.isEnabled(); }

	public static void loadPersistedData(MinecraftServer server) { LevelsPlayerAPIManager.loadPersistedData(server); }
	public static void autosavePersistedData(MinecraftServer server) { LevelsPlayerAPIManager.autosavePersistedData(server); }
	public static void savePersistedData(MinecraftServer server) { LevelsPlayerAPIManager.savePersistedData(server); }
	public static void flushDirtySyncs(MinecraftServer server) { LevelsPlayerAPIManager.flushDirtySyncs(server); }
	public static void addXp(ServerPlayer player, int xpAmount) { LevelsPlayerAPIManager.addXp(player, xpAmount); }
	public static int getPlayerHungerBonusPoints(ServerPlayer player) { return LevelsPlayerAPIManager.getPlayerHungerBonusPoints(player); }

	static void handleLevelUpRequest(ServerPlayer player, String statId) {
		LevelsPlayerAPIManager.upgradeStat(player, statId);
	}

	public static boolean useAttributesContainer() {
		return madoku.craft.attributes.MadokuAttributesManager.isEnabled()
			&& (madoku.craft.attributes.HungerAPIManager.isEnabled()
				|| madoku.craft.attributes.LuckAPIManager.isEnabled());
	}
}
