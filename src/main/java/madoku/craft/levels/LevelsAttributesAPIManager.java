package madoku.craft.levels;

import net.minecraft.server.level.ServerPlayer;

/** Public contract for applying Madoku Levels attributes. */
public final class LevelsAttributesAPIManager {
	private static final LevelsAttributesProvider UNAVAILABLE_PROVIDER = new LevelsAttributesProvider() { };
	private static volatile LevelsAttributesProvider provider = UNAVAILABLE_PROVIDER;

	private LevelsAttributesAPIManager() {
	}

	public static void registerProvider(LevelsAttributesProvider candidate) { if (candidate == null) throw new IllegalArgumentException("Levels attributes provider must not be null."); provider = candidate; }
	public static void unregisterProvider() { provider = UNAVAILABLE_PROVIDER; }
	public static void initialize() { provider.initialize(); }
	public static void reset() { provider.reset(); }
	public static void applyPlayerAttributes(ServerPlayer player) { provider.applyPlayerAttributes(player); }
	public static int hungerBonusPoints(ServerPlayer player, int level) { return provider.hungerBonusPoints(player, level); }
	public static double valueAtLevel(ServerPlayer player, LevelStat stat, int level) {
		return provider.valueAtLevel(player, stat, level);
	}
}
