package madoku.craft.java.compat;

import madoku.craft.java.compat.attributes.MadokuAttributeFeatureAdapters;
import madoku.craft.java.compat.farming.MadokuFarmingFeatureAdapters;
import madoku.craft.java.compat.levels.MadokuLevelsFeatureAdapters;
import madoku.craft.java.compat.loot.MadokuLootFeatureAdapters;
import madoku.craft.java.compat.mobs.MadokuMobFeatureAdapters;
import madoku.craft.java.compat.utility.MadokuUtilityFeatureAdapters;
import madoku.craft.java.core.runtime.AdaptiveIntervalAPIManager;
import madoku.craft.java.core.time.TimeAPIManager;
import madoku.craft.java.mob.MadokuMobManager;
import net.minecraft.server.MinecraftServer;

/** Registers adapters owned by Compat. */
public final class MadokuCompatManager {
	private static final String DIFFICULTY_SYNC_ADAPTIVE_ID = "madoku-compat-difficulty-sync";
	private static final long DIFFICULTY_SYNC_MIN_INTERVAL_TICKS = 5L;
	private static final long DIFFICULTY_SYNC_MAX_INTERVAL_TICKS = 20L;
	private static long nextDifficultySyncTick = Long.MIN_VALUE;

	private MadokuCompatManager() { }

	/** Installs feature-to-feature bridges before gameplay begins. */
	public static void initialize() {
		if (MadokuCompatModuleState.hasAll(
			MadokuCompatModuleState.CORE_ID,
			MadokuCompatModuleState.ATTRIBUTES_ID
		)) {
			MadokuAttributeFeatureAdapters.initialize();
		}
		if (MadokuCompatModuleState.hasAll(
			MadokuCompatModuleState.CORE_ID,
			MadokuCompatModuleState.FARMING_ID
		)) {
			MadokuFarmingFeatureAdapters.initialize();
		}
		if (MadokuCompatModuleState.hasAll(
			MadokuCompatModuleState.CORE_ID,
			MadokuCompatModuleState.LEVELS_ID
			)
			&& MadokuCompatModuleState.hasAny(
				MadokuCompatModuleState.ATTRIBUTES_ID,
				MadokuCompatModuleState.PETS_ID
			)) {
			MadokuLevelsFeatureAdapters.initialize();
		}
		if (MadokuCompatModuleState.hasAll(MadokuCompatModuleState.CORE_ID)
			&& MadokuCompatModuleState.hasAny(
				MadokuCompatModuleState.ATTRIBUTES_ID,
				MadokuCompatModuleState.FARMING_ID,
				MadokuCompatModuleState.MOBS_ID,
				MadokuCompatModuleState.ITEMS_ID,
				MadokuCompatModuleState.PETS_ID
			)) {
			MadokuLootFeatureAdapters.initialize();
		}
		if (MadokuCompatModuleState.hasAll(
			MadokuCompatModuleState.CORE_ID,
			MadokuCompatModuleState.MOBS_ID
		)) {
			MadokuMobFeatureAdapters.initialize();
		}
		if (MadokuCompatModuleState.hasAll(
			MadokuCompatModuleState.CORE_ID,
			MadokuCompatModuleState.UTILITY_ID,
			MadokuCompatModuleState.ITEMS_ID
		)) {
			MadokuUtilityFeatureAdapters.initialize();
		}
	}

	public static void onServerStarted(MinecraftServer server) {
		AdaptiveIntervalAPIManager.clearSystem(DIFFICULTY_SYNC_ADAPTIVE_ID);
		nextDifficultySyncTick = Long.MIN_VALUE;
	}

	public static void onServerTick(MinecraftServer server) {
		if (server == null || !MadokuCompatModuleState.hasAll(
			MadokuCompatModuleState.MOBS_ID,
			MadokuCompatModuleState.HUD_ID
		)) {
			return;
		}
		long now = Math.max(0L, TimeAPIManager.getGameplayTicks());
		if (nextDifficultySyncTick != Long.MIN_VALUE && now < nextDifficultySyncTick) {
			return;
		}
		nextDifficultySyncTick = now + Math.max(1L, AdaptiveIntervalAPIManager.resolve(
			DIFFICULTY_SYNC_ADAPTIVE_ID,
			server,
			DIFFICULTY_SYNC_MIN_INTERVAL_TICKS,
			DIFFICULTY_SYNC_MAX_INTERVAL_TICKS
		));
		MadokuMobManager.broadcastDifficultyIfChanged(server);
	}

	public static void onServerStopped() {
		AdaptiveIntervalAPIManager.clearSystem(DIFFICULTY_SYNC_ADAPTIVE_ID);
		nextDifficultySyncTick = Long.MIN_VALUE;
	}
}
