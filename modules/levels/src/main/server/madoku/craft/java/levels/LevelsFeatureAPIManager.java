package madoku.craft.java.levels;

import net.minecraft.server.level.ServerPlayer;

/** Registration point for optional feature integrations used by Levels. */
public final class LevelsFeatureAPIManager {
	private static final LevelsFeatureAdapter NO_ADAPTER = new LevelsFeatureAdapter() { };
	private static volatile LevelsFeatureAdapter adapter = NO_ADAPTER;

	private LevelsFeatureAPIManager() { }

	public static void registerAdapter(LevelsFeatureAdapter candidate) {
		if (candidate == null) throw new IllegalArgumentException("Levels feature adapter must not be null.");
		adapter = candidate;
	}

	public static void unregisterAdapter() { adapter = NO_ADAPTER; }
	public static boolean useAttributesContainer() { return adapter.useAttributesContainer(); }
	public static boolean isHungerEnabled() { return adapter.isHungerEnabled(); }
	public static boolean isLuckEnabled() { return adapter.isLuckEnabled(); }
	public static void applyPlayerMaxHealthAbilityBonus(ServerPlayer player) { adapter.applyPlayerMaxHealthAbilityBonus(player); }
	public static void applyPlayerDamageAbilityBonus(ServerPlayer player) { adapter.applyPlayerDamageAbilityBonus(player); }
	public static void applyPlayerArmorAbilityBonus(ServerPlayer player) { adapter.applyPlayerArmorAbilityBonus(player); }
	public static void handleMaximumHungerChanged(ServerPlayer player) { adapter.handleMaximumHungerChanged(player); }
	public static void restoreJoinHealth(ServerPlayer player) { adapter.restoreJoinHealth(player); }
}
